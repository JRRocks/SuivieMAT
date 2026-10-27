package com.scanlist.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.scanlist.app.domain.model.ScanMode

/** Pictogramme d'un mode de scan, pour les lignes compactes (liste des dossiers). */
val ScanMode.icon: ImageVector
    get() = when (this) {
        ScanMode.OCR -> Icons.Default.TextFields
        ScanMode.QR_CODE -> Icons.Default.QrCode2
        ScanMode.BARCODE -> BarcodeIcon
        ScanMode.NFC -> Icons.Default.Nfc
    }

/**
 * Une rangee de barres d'epaisseurs variees. Material Icons n'a pas de code-barres
 * lineaire, et les icones voisines (colonnes, scanner) se confondent avec le Code 2D.
 */
private val BarcodeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Barcode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // La couleur est remplacee par la teinte de l'Icon : seule la forme compte.
        path(fill = SolidColor(Color.Black)) {
            listOf(3f to 2f, 6f to 1f, 8.5f to 2f, 11.5f to 1f, 13.5f to 2.5f, 17f to 1f, 19f to 2f)
                .forEach { (x, width) ->
                    moveTo(x, 5f)
                    lineTo(x + width, 5f)
                    lineTo(x + width, 19f)
                    lineTo(x, 19f)
                    close()
                }
        }
    }.build()
}
