package com.scanlist.app.scanner

import com.scanlist.app.domain.model.ScanValue

/** Une valeur validee par le consensus, prete pour l'enregistrement. */
data class ConsensusCandidate(
    val value: String,
    val symbology: String? = null,
)

/**
 * Validation par consensus du scan a la chaine.
 *
 * L'optique se trompe : angle, reflet, etiquette abimee. Une seule lecture ne suffit
 * pas a distinguer une vraie valeur d'un artefact. Ce filtre compte les lectures
 * identiques d'une meme valeur et ne la valide qu'au bout de [threshold] occurrences.
 *
 * « Consecutives » ne veut pas dire « a chaque frame » : une frame sans decodage ne
 * casse pas un compteur en cours (une main qui tremble ne doit pas repousser
 * indefiniment la validation), mais une valeur differente qui s'intercale le fait.
 * Chaque compteur expire au bout de [ttlMs] sans nouvelle lecture, pour qu'un
 * consensus abandonne ne traine pas et ne se complete pas avec une lecture bien plus
 * tardive.
 *
 * Non thread-safe : appelee depuis l'unique thread d'analyse CameraX, au meme titre
 * que [ScanDebouncer]. Elle s'intercale AVANT lui dans le pipeline : une valeur
 * confirmee ici passe ensuite par le debouncer, qui devient un verrou anti-rebond
 * post-validation plutot qu'un pre-filtre — sinon il ecarterait les lectures 2 et 3
 * avant meme que le consensus ne les compte.
 */
class ScanConsensus(
    private val threshold: Int,
    private val ambiguityGuard: Boolean,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    private class Counter(
        var count: Int,
        var lastSeen: Long,
        val symbology: String?,
        val firstRawValue: String,
    ) {
        /** Cles avec lesquelles cette valeur a deja partage une image : objets distincts. */
        val coexistedWith = HashSet<String>()
    }

    private val counters = LinkedHashMap<String, Counter>()

    /**
     * Confronte une image de candidats (deja filtres par la formule de conformite)
     * au consensus en cours et renvoie les valeurs qui atteignent [threshold] sur cet
     * appel. Une valeur validee est aussitot purgee (verrouillage et purge, etape 4) :
     * elle redevient disponible pour un nouveau consensus si le meme objet repasse
     * plus tard devant la camera.
     */
    fun submit(candidates: List<ConsensusCandidate>, now: Long = System.currentTimeMillis()): List<ConsensusCandidate> {
        pruneExpired(now)

        val frameKeys = candidates
            .filter { ScanValue.isUsable(it.value) }
            .map { ScanValue.key(it.value) }
            .distinct()

        val newlyCreated = mutableListOf<String>()
        candidates.forEach { candidate ->
            if (!ScanValue.isUsable(candidate.value)) return@forEach
            val key = ScanValue.key(candidate.value)
            val existing = counters[key]
            if (existing == null) {
                counters[key] = Counter(1, now, candidate.symbology, candidate.value)
                newlyCreated += key
            } else {
                existing.count += 1
                existing.lastSeen = now
            }
        }

        if (ambiguityGuard) {
            markCoexistence(frameKeys)
            resolveAmbiguity(newlyCreated)
        }

        val validated = mutableListOf<ConsensusCandidate>()
        frameKeys.forEach { key ->
            val counter = counters[key] ?: return@forEach
            if (counter.count >= threshold) {
                validated += ConsensusCandidate(counter.firstRawValue, counter.symbology)
                counters.remove(key)
            }
        }
        return validated
    }

    /** Progression du compteur le plus avance, pour le retour visuel du reticule. */
    fun peakProgress(): Float {
        val best = counters.values.maxOfOrNull { it.count } ?: return 0f
        return (best.toFloat() / threshold).coerceIn(0f, 1f)
    }

    fun reset() {
        counters.clear()
    }

    /**
     * Deux valeurs vues ensemble dans la meme image sont deux objets physiquement
     * distincts : elles ne doivent jamais s'annuler l'une l'autre, meme proches
     * (numeros de serie sequentiels dans le meme rack).
     */
    private fun markCoexistence(frameKeys: List<String>) {
        if (frameKeys.size < 2) return
        for (i in frameKeys.indices) {
            for (j in i + 1 until frameKeys.size) {
                counters[frameKeys[i]]?.coexistedWith?.add(frameKeys[j])
                counters[frameKeys[j]]?.coexistedWith?.add(frameKeys[i])
            }
        }
    }

    /**
     * Une valeur qui vient d'apparaitre et qui est a une lettre pres d'une valeur deja
     * en cours de comptage, sans que les deux n'aient jamais partage une image, est
     * vraisemblablement la meme etiquette lue deux fois differemment. Les deux
     * compteurs repartent a zero plutot que de laisser le hasard trancher lequel des
     * deux atteint le seuil en premier.
     */
    private fun resolveAmbiguity(newlyCreated: List<String>) {
        newlyCreated.forEach { key ->
            val counter = counters[key] ?: return@forEach
            val conflict = counters.keys.firstOrNull { other ->
                other != key &&
                    withinEditDistanceOne(key, other) &&
                    other !in counter.coexistedWith &&
                    key !in (counters[other]?.coexistedWith ?: emptySet())
            }
            if (conflict != null) {
                counters.remove(key)
                counters.remove(conflict)
            }
        }
    }

    /** Purge les compteurs abandonnes, sans quoi une session de plusieurs milliers de codes ferait croitre la table indefiniment. */
    private fun pruneExpired(now: Long) {
        counters.entries.removeAll { now - it.value.lastSeen >= ttlMs }
    }

    companion object {
        const val DEFAULT_TTL_MS = 800L

        /** Vrai si [a] et [b] different d'au plus une substitution, insertion ou suppression. */
        internal fun withinEditDistanceOne(a: String, b: String): Boolean {
            if (a == b) return false
            val lengthDiff = a.length - b.length
            if (lengthDiff !in -1..1) return false

            if (a.length == b.length) {
                var diffs = 0
                for (i in a.indices) {
                    if (a[i] != b[i]) {
                        diffs++
                        if (diffs > 1) return false
                    }
                }
                return diffs == 1
            }

            val shorter = if (a.length < b.length) a else b
            val longer = if (a.length < b.length) b else a
            var i = 0
            var j = 0
            var skipped = false
            while (i < shorter.length && j < longer.length) {
                if (shorter[i] == longer[j]) {
                    i++
                    j++
                } else {
                    if (skipped) return false
                    skipped = true
                    j++
                }
            }
            return true
        }
    }
}
