package com.scanlist.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanlist.app.R
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.ui.theme.ScanValueTextStyle
import com.scanlist.app.ui.theme.shellColors

/**
 * Selecteur de mode de scan, par echantillon d'etiquette.
 *
 * Reutilise par "Nouveau scan", "Reprendre le scan" et "Lancer le scan" d'une
 * comparaison : les trois sous-menus de la specification pointent sur ce composant,
 * ce qui garantit qu'ils proposent exactement les memes options.
 *
 * Chaque mode montre un petit dessin de ce qu'il lit (etape 3 de la refonte, choix de
 * l'utilisateur) : l'operateur reconnait l'etiquette qu'il a sous les yeux sans lire.
 * Le choix explicite du mode reste l'exigence anti-erreur de la specification, le
 * dessin ne fait que le rendre plus sur.
 *
 * [highlighted] met en avant le mode du dernier scan, teint du bleu de l'en-tete.
 */
@Composable
fun ScanModePicker(
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: ScanMode? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeEntry.entries.forEach { entry ->
            ModeCard(
                entry = entry,
                emphasized = highlighted == entry.mode,
                onClick = { onModeSelected(entry.mode) },
            )
        }
    }
}

private enum class ModeEntry(
    val mode: ScanMode,
    @StringRes val title: Int,
    @StringRes val hint: Int,
) {
    OCR(ScanMode.OCR, R.string.mode_ocr, R.string.mode_ocr_desc),
    MATRIX(ScanMode.QR_CODE, R.string.mode_qr, R.string.mode_qr_desc),
    BARS(ScanMode.BARCODE, R.string.mode_barcode, R.string.mode_barcode_desc),
    CHIP(ScanMode.NFC, R.string.mode_nfc, R.string.mode_nfc_desc),
}

@Composable
private fun ModeCard(entry: ModeEntry, emphasized: Boolean, onClick: () -> Unit) {
    val shell = shellColors
    val container = if (emphasized) shell.container else MaterialTheme.colorScheme.surface
    val content = if (emphasized) shell.content else MaterialTheme.colorScheme.onSurface
    val secondary = if (emphasized) {
        shell.contentVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tile = if (emphasized) {
        shell.content.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emphasized) 3.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 72.dp)
                    .background(tile, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                when (entry) {
                    ModeEntry.OCR -> PrintedSample()
                    ModeEntry.MATRIX -> MatrixSample()
                    ModeEntry.BARS -> BarsSample()
                    ModeEntry.CHIP -> ChipSample()
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(entry.title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(entry.hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondary,
                )
            }
        }
    }
}

/*
 * Couleurs d'une etiquette physique, papier blanc imprime en noir : elles ne suivent pas
 * le theme. Une etiquette reste blanche quand le telephone passe en theme sombre, et
 * c'est ce contraste qu'on reconnait sur le materiel.
 */
private val Paper = Color.White
private val Ink = Color(0xFF121A2E)
private val Edge = Color(0xFFC3CAD9)
private val LabelShape = RoundedCornerShape(6.dp)

@Composable
private fun Label(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .background(Paper, LabelShape)
            .border(1.dp, Edge, LabelShape),
        content = content,
    )
}

/** OCR : un numero imprime en clair, et deux lignes de mentions autour. */
@Composable
private fun PrintedSample() {
    Label {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                stringResource(R.string.mode_sample_ocr),
                style = ScanValueTextStyle.copy(fontSize = 15.sp, lineHeight = 16.sp),
                color = Ink,
            )
            Box(Modifier.size(width = 44.dp, height = 3.dp).background(Edge, LabelShape))
            Box(Modifier.size(width = 30.dp, height = 3.dp).background(Edge, LabelShape))
        }
    }
}

/** Code 2D : un QR Code stylise, sur une grille de 25 modules. */
@Composable
private fun MatrixSample() {
    Label {
        Canvas(modifier = Modifier.padding(5.dp).size(46.dp)) {
            val cell = size.width / 25f
            listOf(0 to 0, 18 to 0, 0 to 18).forEach { (x, y) -> finder(x, y, cell) }
            MATRIX_MODULES.forEach { (x, y, w, h) -> module(x, y, w, h, cell, Ink) }
        }
    }
}

/** Code-barres : une seule rangee de barres d'epaisseurs variees. */
@Composable
private fun BarsSample() {
    Label {
        Canvas(modifier = Modifier.padding(6.dp).size(width = 62.dp, height = 30.dp)) {
            val unit = size.width / 62f
            BARS.forEach { (x, w) ->
                drawRect(Ink, Offset(x * unit, 0f), Size(w * unit, size.height))
            }
        }
    }
}

/** NFC/RFID : un tag sans contact, son antenne en spirale et sa puce. */
@Composable
private fun ChipSample() {
    Canvas(modifier = Modifier.size(54.dp)) {
        val u = size.width / 40f
        val stroke = Stroke(width = 1.5f * u)
        roundRect(2f, 36f, 5f, u, Paper)
        roundRect(2f, 36f, 5f, u, Edge, stroke)
        roundRect(7f, 26f, 4f, u, Ink, stroke)
        roundRect(11f, 18f, 3f, u, Ink, stroke)
        roundRect(16.5f, 7f, 1f, u, Ink)
    }
}

/** Motif de reperage d'un QR Code : carre plein, anneau blanc, coeur plein. */
private fun DrawScope.finder(x: Int, y: Int, cell: Float) {
    module(x, y, 7, 7, cell, Ink)
    module(x + 1, y + 1, 5, 5, cell, Paper)
    module(x + 2, y + 2, 3, 3, cell, Ink)
}

private fun DrawScope.module(x: Int, y: Int, w: Int, h: Int, cell: Float, color: Color) {
    drawRect(color, Offset(x * cell, y * cell), Size(w * cell, h * cell))
}

/** Carre arrondi centre sur la grille de 40 unites du dessin du tag. */
private fun DrawScope.roundRect(
    origin: Float,
    side: Float,
    radius: Float,
    unit: Float,
    color: Color,
    style: Stroke? = null,
) {
    val topLeft = Offset(origin * unit, origin * unit)
    val rectSize = Size(side * unit, side * unit)
    val corner = CornerRadius(radius * unit)
    if (style == null) {
        drawRoundRect(color, topLeft, rectSize, corner)
    } else {
        drawRoundRect(color, topLeft, rectSize, corner, style = style)
    }
}

/** Modules de donnees du QR stylise : x, y, largeur, hauteur, en modules. */
private val MATRIX_MODULES = listOf(
    listOf(9, 1, 2, 2), listOf(12, 3, 2, 2), listOf(9, 5, 1, 3), listOf(14, 7, 3, 1),
    listOf(8, 9, 3, 2), listOf(12, 10, 2, 3), listOf(16, 9, 2, 2), listOf(20, 9, 3, 1),
    listOf(1, 9, 2, 2), listOf(4, 11, 3, 1), listOf(9, 13, 1, 2), listOf(15, 13, 4, 2),
    listOf(20, 12, 2, 4), listOf(10, 16, 3, 2), listOf(14, 17, 2, 3), listOf(18, 18, 3, 2),
    listOf(9, 20, 2, 3), listOf(12, 21, 3, 2), listOf(17, 21, 2, 2), listOf(21, 22, 3, 2),
    listOf(23, 18, 2, 2),
)

/** Barres du code-barres stylise : position et epaisseur, sur 62 unites. */
private val BARS = listOf(
    0 to 2, 3 to 1, 6 to 3, 10 to 1, 13 to 2, 17 to 1, 19 to 3, 24 to 1, 26 to 2, 30 to 1,
    33 to 3, 37 to 1, 40 to 2, 44 to 1, 46 to 3, 51 to 1, 53 to 2, 57 to 1, 60 to 2,
)
