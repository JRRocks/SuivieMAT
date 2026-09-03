package com.scanlist.app.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Reconnaissance de texte sur le flux camera.
 *
 * L'analyseur ne fait que lire : il remonte le texte brut et laisse l'appelant lui
 * appliquer la formule regex active. Deux raisons a ce decoupage :
 *
 * 1. La formule change pendant que la camera tourne — l'utilisateur la tape en direct.
 *    La garder hors de l'analyseur evite de le reconstruire a chaque frappe.
 * 2. Le texte brut est necessaire a l'ecran de reglage : sans lui, impossible de savoir
 *    quoi ecrire dans la formule.
 *
 * Modele latin embarque dans l'APK : aucun telechargement, fonctionne hors-ligne.
 */
class OcrAnalyzer(
    private val onTextRecognized: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val text = result.text
                if (text.isNotBlank()) onTextRecognized(text)
            }
            // Regle transverse : sur erreur, ne rien faire.
            .addOnFailureListener { }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** A appeler quand le scan se termine : libere le modele ML Kit. */
    fun close() = recognizer.close()
}
