package com.scanlist.app.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.scanlist.app.domain.model.ScanMode

/** Un code decode dans une image. */
data class DetectedCode(
    val value: String,
    val symbology: String,
)

/**
 * Decodage des codes 1D et 2D sur le flux camera.
 *
 * Le filtrage par mode est applique **aux options du decodeur**, pas au resultat :
 * en mode Code 2D, ML Kit ne cherche meme pas les codes-barres lineaires, et
 * reciproquement. C'est ce qui rend les modes reellement exclusifs, et non simplement
 * filtres apres coup — l'exigence anti-erreur de la specification. La table des
 * formats vit dans [BarcodeFormats], ou elle reste verifiable en test.
 *
 * Tous les codes trouves dans une meme image sont remontes ensemble : l'appelant en
 * fera une ligne chacun.
 */
class BarcodeAnalyzer(
    mode: ScanMode,
    private val onCodesDetected: (List<DetectedCode>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(optionsFor(mode))

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val codes = barcodes.mapNotNull { barcode ->
                    barcode.rawValue
                        ?.takeIf { it.isNotBlank() }
                        ?.let { DetectedCode(it, BarcodeFormats.symbologyName(barcode.format)) }
                }
                if (codes.isNotEmpty()) onCodesDetected(codes)
            }
            // Regle transverse : sur erreur, ne rien faire. Une frame floue ou mal
            // exposee est simplement ignoree, le scan continue.
            .addOnFailureListener { }
            // L'ImageProxy doit etre libere dans tous les cas, sinon le pipeline se
            // bloque des que le buffer est plein.
            .addOnCompleteListener { imageProxy.close() }
    }

    /** A appeler quand le scan se termine : libere le modele ML Kit. */
    fun close() = scanner.close()

    private companion object {

        fun optionsFor(mode: ScanMode): BarcodeScannerOptions =
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(BarcodeFormats.forMode(mode))
                .build()
    }
}
