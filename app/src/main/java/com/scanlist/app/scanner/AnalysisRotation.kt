package com.scanlist.app.scanner

import android.view.Surface

/**
 * Rotation a donner a l'analyse d'images selon la position physique du telephone.
 *
 * L'ecran est verrouille en portrait ; l'operateur tourne pourtant le telephone pour
 * cadrer une etiquette en long. ML Kit doit recevoir l'image dans le sens de lecture,
 * sinon l'OCR voit un texte couche et ne rend rien. Les seuils a 45 degres sont ceux
 * de la documentation CameraX.
 */
object AnalysisRotation {

    /** [degrees] : valeur d'`OrientationEventListener`, 0 a 359. */
    fun fromOrientation(degrees: Int): Int = when (degrees) {
        in 45 until 135 -> Surface.ROTATION_270
        in 135 until 225 -> Surface.ROTATION_180
        in 225 until 315 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }
}
