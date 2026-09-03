package com.scanlist.app.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
 * en mode QR Code, ML Kit ne cherche meme pas les codes-barres, et reciproquement.
 * C'est ce qui rend les modes reellement exclusifs, et non simplement filtres apres
 * coup — l'exigence anti-erreur de la specification.
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
                        ?.let { DetectedCode(it, symbologyName(barcode.format)) }
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

        /** Formats 1D reconnus en mode Code-barres. */
        const val ONE_D_FORMATS = Barcode.FORMAT_EAN_13 or
            Barcode.FORMAT_EAN_8 or
            Barcode.FORMAT_UPC_A or
            Barcode.FORMAT_UPC_E or
            Barcode.FORMAT_CODE_39 or
            Barcode.FORMAT_CODE_93 or
            Barcode.FORMAT_CODE_128 or
            Barcode.FORMAT_ITF or
            Barcode.FORMAT_CODABAR

        fun optionsFor(mode: ScanMode): BarcodeScannerOptions =
            BarcodeScannerOptions.Builder()
                .apply {
                    when (mode) {
                        ScanMode.QR_CODE -> setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        ScanMode.BARCODE -> setBarcodeFormats(ONE_D_FORMATS)
                        // L'OCR et le NFC passent par d'autres analyseurs, jamais par
                        // ce decodeur ; branches conservees pour l'exhaustivite du when.
                        ScanMode.OCR, ScanMode.NFC -> setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    }
                }
                .build()

        fun symbologyName(format: Int): String = when (format) {
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            else -> "AUTRE"
        }
    }
}
