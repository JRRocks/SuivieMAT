package com.scanlist.app.ocrbench

import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.scanner.ConsensusCandidate
import com.scanlist.app.scanner.ScanConsensus
import com.scanlist.app.scanner.ScanDebouncer
import java.io.File
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Banc de mesure du moteur de lecture OCR.
 *
 * Question posee par l'utilisateur : la lecture serait-elle plus performante en Rust, ou
 * dans un autre langage ? Le banc y repond par deux mesures, sur les memes images :
 *
 * 1. le moteur de reconnaissance : ML Kit (C++, celui de l'application) contre ocrs
 *    (entierement en Rust), alternes image par image ;
 * 2. le traitement que l'application applique ensuite a chaque texte reconnu (formule,
 *    consensus, anti-rebond) : le code Kotlin de l'application contre son portage en
 *    Rust, appele par JNI.
 *
 * Il s'execute dans le processus de l'application : ML Kit y est celui de l'APK, et les
 * classes mesurees sont celles de l'application, pas des copies. Mode d'emploi dans
 * ocrbench/README.md.
 */
@RunWith(AndroidJUnit4::class)
class OcrBenchmark {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val testAssets = instrumentation.context.assets
    private val appContext = instrumentation.targetContext

    private class Label(
        val id: Int,
        val tier: String,
        val pattern: String,
        val expected: String,
        val files: Map<String, String>,
    )

    /** Image prete pour les moteurs : NV21 comme une image de camera, luminance en tete. */
    private class Frame(val nv21: ByteArray, val width: Int, val height: Int) {
        fun luma(): ByteArray = nv21.copyOf(width * height)
    }

    private enum class Verdict { JUSTE, FAUX, RIEN }

    private class EngineResult(
        val name: String,
        val resolution: String,
        /** Mediane des passes chronometrees, une par etiquette, en millisecondes. */
        val millis: List<Double>,
        val verdicts: List<Verdict>,
        val texts: List<String>,
    )

    @Test
    fun compare_les_moteurs_de_lecture() {
        say(MARKER_START)
        describeDevice()
        val labels = loadLabels()
        say("Etiquettes : ${labels.size} (${labels.groupBy { it.tier }.map { "${it.value.size} ${it.key}" }.joinToString(", ")})")

        val (mlKit, rust) = measureEngines(labels)
        report(mlKit, labels)
        if (rust != null) {
            report(rust, labels)
            faceToFace(mlKit, rust)
        }

        measurePostProcessing(labels, mlKit)

        measureResolutions(labels).forEach { report(it, labels) }

        say(MARKER_END)
        assertTrue("ML Kit n'a rien lu : le banc ne mesure rien", mlKit.verdicts.any { it == Verdict.JUSTE })
    }

    // ------------------------------------------------------------------ Moteurs

    /**
     * Les deux moteurs sur les memes images en 720p, la resolution d'analyse de
     * l'application, en les alternant passe apres passe : un emulateur n'est jamais
     * tout a fait au repos, et mesurer l'un puis l'autre attribuerait a un moteur la
     * charge de fond du moment. Alternes, ils la subissent a parts egales.
     */
    private fun measureEngines(labels: List<Label>): Pair<EngineResult, EngineResult?> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val engine = openRustEngine()
        try {
            // Les premiers appels chargent les modeles : ils ne disent rien du regime etabli.
            val first = loadFrame(labels.first(), "720")
            repeat(3) { Tasks.await(recognizer.process(first.toInputImage())) }
            if (engine != 0L) repeat(2) { RustBridge.engineRead(engine, first.luma(), first.width, first.height) }

            val mlKit = Samples()
            val rust = Samples()
            for (label in labels) {
                val frame = loadFrame(label, "720")
                // Extraite hors chronometre : sur un vrai flux, le plan Y existe deja.
                val luma = frame.luma()
                val mlKitMillis = DoubleArray(ENGINE_ROUNDS)
                val rustMillis = DoubleArray(ENGINE_ROUNDS)
                var mlKitText = ""
                var rustText: String? = null
                for (round in 0 until ENGINE_ROUNDS) {
                    // L'InputImage est reconstruite a chaque passe, comme pour chaque
                    // image de la camera.
                    mlKitMillis[round] = millis {
                        mlKitText = Tasks.await(recognizer.process(frame.toInputImage())).text
                    }
                    if (engine != 0L) {
                        rustMillis[round] = millis {
                            rustText = RustBridge.engineRead(engine, luma, frame.width, frame.height)
                        }
                    }
                }
                mlKit.add(median(mlKitMillis), mlKitText, label)
                rust.add(median(rustMillis), rustText.orEmpty(), label)
            }
            return mlKit.result("ML Kit (C++)", "720") to
                if (engine != 0L) rust.result("ocrs (Rust)", "720") else null
        } finally {
            recognizer.close()
            if (engine != 0L) RustBridge.engineDestroy(engine)
        }
    }

    /** Renvoie 0 si le moteur Rust n'est pas disponible, apres avoir dit pourquoi. */
    private fun openRustEngine(): Long {
        if (!RustBridge.available) {
            say("Moteur Rust : bibliotheque absente de cet APK, mesure sautee")
            return 0L
        }
        val detection = copyAsset("ocrbench/models/text-detection.onnx")
        val recognition = copyAsset("ocrbench/models/text-recognition.onnx")
        if (detection == null || recognition == null) {
            say("Moteur Rust : modeles absents de cet APK, mesure sautee")
            return 0L
        }
        var engine = 0L
        val loadMillis = millis { engine = RustBridge.engineCreate(detection.path, recognition.path) }
        if (engine == 0L) {
            say("Moteur Rust : chargement impossible (${RustBridge.lastError()})")
        } else {
            say("Moteur Rust : modeles charges en ${fmt(loadMillis)} ms")
        }
        return engine
    }

    /**
     * ML Kit aux trois resolutions, alternees elles aussi : ce que couterait ou
     * rapporterait un changement de la resolution d'analyse.
     */
    private fun measureResolutions(labels: List<Label>): List<EngineResult> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val samples = RESOLUTIONS.associateWith { Samples() }
            for (label in labels) {
                val frames = RESOLUTIONS.associateWith { loadFrame(label, it) }
                val timings = RESOLUTIONS.associateWith { DoubleArray(RESOLUTION_ROUNDS) }
                val texts = HashMap<String, String>()
                for (round in 0 until RESOLUTION_ROUNDS) {
                    for (resolution in RESOLUTIONS) {
                        val frame = frames.getValue(resolution)
                        timings.getValue(resolution)[round] = millis {
                            texts[resolution] = Tasks.await(recognizer.process(frame.toInputImage())).text
                        }
                    }
                }
                for (resolution in RESOLUTIONS) {
                    samples.getValue(resolution)
                        .add(median(timings.getValue(resolution)), texts[resolution].orEmpty(), label)
                }
            }
            return RESOLUTIONS.map { samples.getValue(it).result("ML Kit (C++), resolutions alternees", it) }
        } finally {
            recognizer.close()
        }
    }

    /** Accumule, etiquette par etiquette, le temps median et la lecture d'un moteur. */
    private inner class Samples {
        private val millis = ArrayList<Double>()
        private val verdicts = ArrayList<Verdict>()
        private val texts = ArrayList<String>()

        fun add(medianMillis: Double, text: String, label: Label) {
            millis += medianMillis
            verdicts += verdictOf(text, label)
            texts += text
        }

        fun result(name: String, resolution: String) = EngineResult(name, resolution, millis, verdicts, texts)
    }

    /** Les deux moteurs ont lu les memes images dans les memes conditions : image par image. */
    private fun faceToFace(mlKit: EngineResult, rust: EngineResult) {
        val ratios = mlKit.millis.indices.map { rust.millis[it] / mlKit.millis[it] }.sorted()
        val rustFaster = ratios.count { it < 1.0 }
        say("")
        say("## Face a face, image par image")
        say("Temps d'ocrs rapporte a celui de ML Kit : mediane x${fmt(percentile(ratios, 0.5))}, " +
            "de x${fmt(ratios.first())} a x${fmt(ratios.last())} ; ocrs plus rapide sur $rustFaster images sur ${ratios.size}")
        say("Lectures justes : ML Kit ${mlKit.verdicts.count { it == Verdict.JUSTE }}, ocrs ${rust.verdicts.count { it == Verdict.JUSTE }} " +
            "sur ${ratios.size} ; fausses : ML Kit ${mlKit.verdicts.count { it == Verdict.FAUX }}, " +
            "ocrs ${rust.verdicts.count { it == Verdict.FAUX }}")
    }

    // ---------------------------------------------------- Traitement par image

    /** Une formule et son etat, tels que `ScanViewModel` les tient en scan OCR de dossier. */
    private class KotlinPipeline(pattern: String) {
        private val regex = Regex(pattern)
        private val consensus = ScanConsensus(threshold = OCR_CONSENSUS_THRESHOLD, ambiguityGuard = true)
        private val debouncer = ScanDebouncer()
        var progress = 0f

        /** Le corps de `ScanViewModel.onTextRecognized`, hors ecriture en base. */
        fun process(text: String, now: Long): List<String> {
            val matches = RegexRuleEngine.extract(text, regex, 0)
            if (matches.isEmpty()) return emptyList()
            val confirmed = consensus.submit(matches.map { ConsensusCandidate(it) }, now)
            progress = consensus.peakProgress()
            return confirmed.filter { debouncer.accept(it.value, now) }.map { it.value }
        }
    }

    private fun measurePostProcessing(labels: List<Label>, mlKit: EngineResult) {
        say("")
        say("## Traitement de chaque texte reconnu (formule, consensus, anti-rebond)")
        val patterns = labels.map { it.pattern }.distinct()

        // Chaque etiquette reste $FRAMES_PER_LABEL images devant l'objectif, puis on passe
        // a la suivante : assez pour franchir le seuil du consensus, comme en inventaire.
        val frameTexts = ArrayList<String>()
        val framePipeline = ArrayList<Int>()
        val frameTimes = ArrayList<Long>()
        var clock = 0L
        labels.forEachIndexed { index, label ->
            repeat(FRAMES_PER_LABEL) {
                frameTexts += mlKit.texts[index]
                framePipeline += patterns.indexOf(label.pattern)
                frameTimes += clock
                clock += FRAME_INTERVAL_MS
            }
            clock += BETWEEN_LABELS_MS
        }
        val period = clock
        val frames = frameTexts.size
        val texts = frameTexts.toTypedArray()
        val pipelineOf = framePipeline.toIntArray()
        val times = frameTimes.toLongArray()
        say("Suite rejouee : $frames images (${labels.size} etiquettes x $FRAMES_PER_LABEL), textes rendus par ML Kit, " +
            "${fmt(texts.sumOf { it.length }.toDouble() / frames)} caracteres en moyenne")

        val kotlin = patterns.map { KotlinPipeline(it) }
        fun kotlinRun(repetitions: Int, base: Int): Long {
            var validated = 0L
            for (repetition in 0 until repetitions) {
                val offset = (base + repetition) * period
                for (frame in 0 until frames) {
                    validated += kotlin[pipelineOf[frame]].process(texts[frame], offset + times[frame]).size
                }
            }
            return validated
        }

        if (!RustBridge.available) {
            say("Portage Rust : bibliotheque absente de cet APK, seul le Kotlin est mesure")
            kotlinRun(WARMUP_REPETITIONS, 0)
            val nanos = bestOf { round -> timed { kotlinRun(TIMED_REPETITIONS, WARMUP_REPETITIONS + round * TIMED_REPETITIONS) } }
            say("Kotlin : ${fmt(nanos / 1e3 / (TIMED_REPETITIONS * frames))} microsecondes par image")
            return
        }

        val rust = LongArray(patterns.size) { RustBridge.pipelineCreate(patterns[it], 0, OCR_CONSENSUS_THRESHOLD, true) }
        if (rust.any { it == 0L }) {
            say("Portage Rust : formule refusee (${RustBridge.lastError()})")
            return
        }
        try {
            // Les deux versions doivent rendre les memes valeurs, image par image : sans
            // cela, comparer leurs temps n'aurait aucun sens.
            val progressOut = FloatArray(1)
            var mismatches = 0
            var validated = 0
            for (frame in 0 until frames) {
                val expected = kotlin[pipelineOf[frame]].process(texts[frame], times[frame])
                val actual = RustBridge.pipelineProcess(rust[pipelineOf[frame]], texts[frame], times[frame], progressOut)
                    ?.toList().orEmpty()
                if (expected != actual) mismatches++
                validated += expected.size
            }
            say("Equivalence Kotlin / Rust : ${if (mismatches == 0) "identiques" else "$mismatches DIFFERENCES"} " +
                "sur $frames images, $validated valeurs validees")
            assertTrue("Le portage Rust ne rend pas les memes valeurs que le Kotlin", mismatches == 0)

            fun rustRun(repetitions: Int, base: Int): Long {
                var count = 0L
                for (repetition in 0 until repetitions) {
                    val offset = (base + repetition) * period
                    for (frame in 0 until frames) {
                        count += RustBridge.pipelineProcess(
                            rust[pipelineOf[frame]], texts[frame], offset + times[frame], progressOut,
                        )?.size ?: 0
                    }
                }
                return count
            }

            // Le premier tour (base 1) repart apres la passe d'equivalence (base 0).
            kotlinRun(WARMUP_REPETITIONS, 1)
            rustRun(WARMUP_REPETITIONS, 1)
            val firstTimed = 1 + WARMUP_REPETITIONS
            val kotlinNanos = bestOf { round -> timed { kotlinRun(TIMED_REPETITIONS, firstTimed + round * TIMED_REPETITIONS) } }
            val rustNanos = bestOf { round -> timed { rustRun(TIMED_REPETITIONS, firstTimed + round * TIMED_REPETITIONS) } }
            val loopNanos = bestOf {
                RustBridge.pipelineLoop(rust, pipelineOf, texts, times, TIMED_REPETITIONS, period)?.get(0) ?: Long.MAX_VALUE
            }

            val perFrame = (TIMED_REPETITIONS * frames).toDouble()
            val kotlinMicros = kotlinNanos / 1e3 / perFrame
            val rustMicros = rustNanos / 1e3 / perFrame
            val loopMicros = loopNanos / 1e3 / perFrame
            val recognitionMillis = mlKit.millis.average()
            say("| Variante | Temps par image | Part d'une lecture ML Kit (${fmt(recognitionMillis)} ms) |")
            say("|---|---|---|")
            say("| Kotlin (application) | ${fmt(kotlinMicros)} µs | ${share(kotlinMicros, recognitionMillis)} |")
            say("| Rust, un appel JNI par image | ${fmt(rustMicros)} µs | ${share(rustMicros, recognitionMillis)} |")
            say("| Rust, calcul seul (sans passage JNI) | ${fmt(loopMicros)} µs | ${share(loopMicros, recognitionMillis)} |")
            say("Gain possible en passant ce traitement en Rust : " +
                "${fmt(kotlinMicros - rustMicros)} µs par image avec JNI, ${fmt(kotlinMicros - loopMicros)} µs au mieux.")
        } finally {
            rust.forEach(RustBridge::pipelineDestroy)
        }
    }

    // ----------------------------------------------------------------- Rapport

    private fun describeDevice() {
        val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        say("# Banc OCR SuivieMAT")
        say("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            "${Build.SUPPORTED_ABIS.joinToString("/")}, ${Runtime.getRuntime().availableProcessors()} coeurs")
        say("Application : ${appContext.packageName}, ${if (debuggable) "DEBUGGABLE (temps Kotlin pessimistes)" else "non debuggable"}")
    }

    private fun report(result: EngineResult, labels: List<Label>) {
        val sorted = result.millis.sorted()
        say("")
        say("## ${result.name}, images ${result.resolution}p")
        say("Temps par image : moyenne ${fmt(result.millis.average())} ms, mediane ${fmt(percentile(sorted, 0.5))} ms, " +
            "p90 ${fmt(percentile(sorted, 0.9))} ms, min ${fmt(sorted.first())} ms, max ${fmt(sorted.last())} ms " +
            "(${fmt(1000 / result.millis.average())} images/s)")
        say("| Niveau | Justes | Fausses | Sans lecture |")
        say("|---|---|---|---|")
        val tiers = labels.map { it.tier }.distinct()
        for (tier in tiers + "TOTAL") {
            val verdicts = labels.indices.filter { tier == "TOTAL" || labels[it].tier == tier }.map { result.verdicts[it] }
            say("| $tier | ${verdicts.count { it == Verdict.JUSTE }}/${verdicts.size} | " +
                "${verdicts.count { it == Verdict.FAUX }} | ${verdicts.count { it == Verdict.RIEN }} |")
        }
        labels.forEachIndexed { index, label ->
            if (result.verdicts[index] == Verdict.FAUX) {
                val read = RegexRuleEngine.extract(result.texts[index], Regex(label.pattern), 0)
                say("Lecture fausse, etiquette ${label.id} (${label.tier}) : attendu ${label.expected}, lu $read")
            }
        }
    }

    /**
     * Une lecture n'est juste que si la formule isole la valeur attendue et elle seule :
     * toute autre valeur serait enregistree par l'application comme une ligne de trop.
     */
    private fun verdictOf(text: String, label: Label): Verdict {
        val values = RegexRuleEngine.extract(text, Regex(label.pattern), 0)
        return when {
            values.isEmpty() -> Verdict.RIEN
            values.size == 1 && values.first() == label.expected -> Verdict.JUSTE
            else -> Verdict.FAUX
        }
    }

    // ------------------------------------------------------------------ Outils

    private fun loadLabels(): List<Label> {
        val manifest = JSONObject(testAssets.open("ocrbench/manifest.json").bufferedReader().use { it.readText() })
        val images = manifest.getJSONArray("images")
        return List(images.length()) { index ->
            val entry = images.getJSONObject(index)
            val files = entry.getJSONObject("files")
            Label(
                id = entry.getInt("id"),
                tier = entry.getString("tier"),
                pattern = entry.getString("pattern"),
                expected = entry.getString("expected"),
                files = files.keys().asSequence().associateWith { files.getString(it) },
            )
        }
    }

    /**
     * Decode l'image et la convertit en NV21, le format que la camera livre a ML Kit.
     * Chrominance neutre : les trois moteurs recoivent exactement la meme luminance.
     */
    private fun loadFrame(label: Label, resolution: String): Frame {
        val name = label.files.getValue(resolution)
        val bitmap = testAssets.open("ocrbench/$name").use { BitmapFactory.decodeStream(it) }
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val nv21 = ByteArray(width * height * 3 / 2)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = pixel shr 16 and 0xFF
            val green = pixel shr 8 and 0xFF
            val blue = pixel and 0xFF
            nv21[index] = ((red * 299 + green * 587 + blue * 114 + 500) / 1000).toByte()
        }
        nv21.fill(128.toByte(), fromIndex = width * height)
        return Frame(nv21, width, height)
    }

    private fun Frame.toInputImage(): InputImage =
        InputImage.fromByteArray(nv21, width, height, 0, InputImage.IMAGE_FORMAT_NV21)

    /** Copie un fichier de l'APK de test vers le cache : le moteur Rust lit des chemins. */
    private fun copyAsset(path: String): File? = runCatching {
        val target = File(appContext.cacheDir, path.substringAfterLast('/'))
        testAssets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
        target
    }.getOrNull()

    private inline fun millis(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1e6
    }

    private inline fun timed(block: () -> Long): Long {
        val start = System.nanoTime()
        sink += block()
        return System.nanoTime() - start
    }

    /** Le plus rapide de plusieurs tours : le bruit d'un emulateur ne fait que ralentir. */
    private inline fun bestOf(measure: (round: Int) -> Long): Long =
        (0 until TIMED_ROUNDS).minOf { measure(it) }

    private fun median(samples: DoubleArray): Double = percentile(samples.sorted(), 0.5)

    private fun percentile(sorted: List<Double>, fraction: Double): Double =
        sorted[((sorted.size - 1) * fraction).toInt()]

    private fun fmt(value: Double): String = String.format(Locale.FRANCE, if (value < 10) "%.2f" else "%.1f", value)

    private fun share(micros: Double, recognitionMillis: Double): String =
        String.format(Locale.FRANCE, "%.3f %%", micros / (recognitionMillis * 1000) * 100)

    /** Sur le flux d'instrumentation (lu par `am instrument`) et dans logcat. */
    private fun say(line: String) {
        Log.i(TAG, line)
        instrumentation.sendStatus(0, Bundle().apply {
            putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$line\n")
        })
    }

    private companion object {
        const val TAG = "OCRBENCH"
        const val MARKER_START = "=== BANC OCR : DEBUT ==="
        const val MARKER_END = "=== BANC OCR : FIN ==="

        /** `ScanViewModel.CONSENSUS_THRESHOLD_OCR`, prive dans l'application. */
        const val OCR_CONSENSUS_THRESHOLD = 5

        /** Passes chronometrees par image et par moteur ; la mediane est retenue. */
        const val ENGINE_ROUNDS = 5
        const val RESOLUTION_ROUNDS = 3
        val RESOLUTIONS = listOf("540", "720", "1080")

        const val FRAMES_PER_LABEL = 6
        const val FRAME_INTERVAL_MS = 100L

        /** Au-dela de la fenetre de l'anti-rebond : chaque etiquette repart d'un etat neuf. */
        const val BETWEEN_LABELS_MS = 2_000L

        const val WARMUP_REPETITIONS = 300
        const val TIMED_REPETITIONS = 500
        const val TIMED_ROUNDS = 5

        /** Empeche l'optimiseur d'eliminer un calcul dont on ne lirait pas le resultat. */
        @Volatile
        var sink = 0L
    }
}
