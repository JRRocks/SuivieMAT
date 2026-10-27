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
 * appliquer la formule regex active. La formule change pendant que la camera tourne —
 * l'utilisateur la tape en direct ; la garder hors de l'analyseur evite de le
 * reconstruire a chaque frappe.
 *
 * Tant que [isActive] est faux (capture desarmee, decision 12), aucune image n'est
 * lue : le texte serait jete (plus d'apercu en direct depuis la decision 26), et la
 * reconnaissance sur chaque image chauffe le telephone et vide la batterie pendant
 * qu'on tape la formule ou qu'on se deplace entre deux etageres.
 *
 * Modele latin embarque dans l'APK : aucun telechargement, fonctionne hors-ligne.
 */
class OcrAnalyzer(
    private val isActive: () -> Boolean,
    private val onTextRecognized: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !isActive()) {
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
