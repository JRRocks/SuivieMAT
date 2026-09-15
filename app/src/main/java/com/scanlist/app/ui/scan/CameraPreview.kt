package com.scanlist.app.ui.scan

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.common.util.concurrent.ListenableFuture
import com.scanlist.app.domain.model.ScanMode
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Resolution d'analyse courante : assez fine pour un code-barres imprime petit, assez
 * legere pour que ML Kit tienne la cadence sans faire chauffer l'appareil.
 */
val STANDARD_ANALYSIS_RESOLUTION: Size = Size(1280, 720)

/**
 * Resolution d'analyse des codes 2D.
 *
 * Un PDF417 ou un Data Matrix concentre ses donnees dans des modules bien plus fins
 * qu'un QR Code de meme encombrement : sous environ deux pixels par module, le
 * decodeur ne voit plus la difference entre deux barres voisines et ne rend rien. En
 * 720p, un PDF417 d'etiquette industrielle tombe sous ce seuil des qu'on ne colle pas
 * l'objectif dessus. Le surcout de calcul est reserve a ce mode : le scan 1D, qui est
 * le cas courant, garde sa cadence.
 */
val DENSE_ANALYSIS_RESOLUTION: Size = Size(1920, 1080)

/** Resolution a demander pour [mode]. */
fun analysisResolutionFor(mode: ScanMode): Size =
    if (mode == ScanMode.QR_CODE) DENSE_ANALYSIS_RESOLUTION else STANDARD_ANALYSIS_RESOLUTION

/**
 * Apercu camera avec analyse d'images en continu.
 *
 * [analyzer] tourne sur un thread dedie ; la strategie KEEP_ONLY_LATEST fait que les
 * images arrivees pendant une analyse sont abandonnees plutot que mises en file. Le
 * pipeline ne prend donc jamais de retard, meme si une frame demande plus de temps —
 * c'est ce qui permet de monter la resolution en [DENSE_ANALYSIS_RESOLUTION] sans
 * risquer d'accumuler du retard sur le chemin critique.
 */
@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    torchEnabled: Boolean,
    modifier: Modifier = Modifier,
    analysisResolution: Size = STANDARD_ANALYSIS_RESOLUTION,
    consensusProgress: Float = 0f,
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Conserve pour la liberation : appeler getInstance().get() depuis onDispose
    // bloquerait le thread principal si l'ecran est quitte avant l'ouverture.
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(analyzer, analysisResolution) {
        val provider = try {
            ProcessCameraProvider.getInstance(context).await()
        } catch (e: Exception) {
            onError(e.message ?: "Camera indisponible")
            return@LaunchedEffect
        }
        cameraProvider = provider

        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    analysisResolution,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolution)
            .build()
        imageAnalysis.setAnalyzer(executor, analyzer)

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            onError(e.message ?: "Impossible d'ouvrir la camera")
        }
    }

    LaunchedEffect(camera, torchEnabled) {
        val current = camera ?: return@LaunchedEffect
        if (current.cameraInfo.hasFlashUnit()) current.cameraControl.enableTorch(torchEnabled)
    }

    // Clef Unit : l'effet ne doit se declencher qu'a la sortie de l'ecran. La valeur
    // de cameraProvider est lue au moment du dispose, pas a la mise en place.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            executor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Viewfinder(progress = consensusProgress, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Reticule d'aide au cadrage : quatre coins, sans masque qui assombrirait la scene.
 *
 * [progress] fait grandir les coins jusqu'a former un cadre complet a mesure que le
 * consensus avance — retour silencieux, aucun bip avant que le seuil ne soit atteint.
 * A 0 (mode NFC, comparaison, ou simplement rien en cours de lecture), l'apparence est
 * identique a celle d'avant l'ajout du consensus.
 */
@Composable
private fun Viewfinder(progress: Float = 0f, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.78f
        val frameHeight = frameWidth * 0.62f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight
        val baseCorner = frameWidth * 0.10f
        val stroke = 4.dp.toPx()
        val color = Color(0xFF4ADE80)
        val advance = progress.coerceIn(0f, 1f)
        val horizontalArm = baseCorner + (frameWidth / 2f - baseCorner) * advance
        val verticalArm = baseCorner + (frameHeight / 2f - baseCorner) * advance

        fun line(start: Offset, end: Offset) = drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        line(Offset(left, top), Offset(left + horizontalArm, top))
        line(Offset(left, top), Offset(left, top + verticalArm))
        line(Offset(right, top), Offset(right - horizontalArm, top))
        line(Offset(right, top), Offset(right, top + verticalArm))
        line(Offset(left, bottom), Offset(left + horizontalArm, bottom))
        line(Offset(left, bottom), Offset(left, bottom - verticalArm))
        line(Offset(right, bottom), Offset(right - horizontalArm, bottom))
        line(Offset(right, bottom), Offset(right, bottom - verticalArm))
    }
}

/** Attente d'un ListenableFuture sans dependance supplementaire. */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        Executor { it.run() },
    )
}
