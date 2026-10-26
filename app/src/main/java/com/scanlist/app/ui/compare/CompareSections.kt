package com.scanlist.app.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.ui.common.SectionTitle
import com.scanlist.app.ui.theme.ScanValueTextStyle
import com.scanlist.app.ui.theme.compareColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Briques d'affichage du resultat d'une comparaison.
 *
 * Partagees par l'ecran de scan et par l'ecran de session : pendant le controle et
 * apres, l'utilisateur doit voir exactement les memes couleurs sur les memes valeurs.
 *
 * Regle posee par l'utilisateur : seule la liste de reference change de couleur. Elle
 * nait en rouge — presence a confirmer — et chaque entree passe au vert quand elle est
 * lue. Une valeur etrangere a la liste n'apparait nulle part.
 */

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

/**
 * Avancement du controle.
 *
 * Deux compteurs colores et une barre : d'un coup d'oeil, combien il en reste. Les
 * couleurs portent la meme information que les chiffres, l'utilisateur n'a pas a lire
 * le libelle pour savoir ou il en est. Pose dans l'en-tete bleu, sans fond propre.
 */
@Composable
fun CompareCounterBar(
    found: Int,
    missing: Int,
    modifier: Modifier = Modifier,
) {
    val colors = compareColors
    val total = found + missing
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountChip(
                text = stringResource(R.string.compare_count_missing, missing),
                background = colors.missing,
                foreground = colors.onMissing,
                modifier = Modifier.weight(1f),
            )
            CountChip(
                text = stringResource(R.string.compare_count_found, found, total),
                background = colors.found,
                foreground = colors.onFound,
                modifier = Modifier.weight(1f),
            )
        }
        if (total > 0) {
            LinearProgressIndicator(
                progress = { found.toFloat() / total },
                color = colors.progress,
                trackColor = colors.missing,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
    }
}

@Composable
private fun CountChip(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = foreground,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(background, MaterialTheme.shapes.small)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    )
}

/**
 * La liste de reference, rouge puis verte.
 *
 * Les entrees a confirmer en tete : ce sont les seules sur lesquelles il reste a
 * agir, elles doivent rester visibles sans defiler. La section verte ferme la liste,
 * elle n'appelle plus aucune action mais confirme le travail fait.
 */
fun LazyListScope.compareSections(
    missing: List<ReferenceEntryEntity>,
    found: List<ReferenceEntryEntity>,
) {
    if (missing.isNotEmpty()) {
        item(key = "head-missing") {
            SectionHeader(stringResource(R.string.compare_section_missing, missing.size))
        }
        items(missing, key = { "missing-" + it.id }) { entry ->
            CompareRow(
                value = entry.value,
                label = entry.label,
                background = compareColors.missing,
                foreground = compareColors.onMissing,
            )
        }
    }

    if (found.isNotEmpty()) {
        item(key = "head-found") {
            SectionHeader(stringResource(R.string.compare_section_found, found.size))
        }
        items(found, key = { "found-" + it.id }) { entry ->
            CompareRow(
                value = entry.value,
                label = entry.label,
                background = compareColors.found,
                foreground = compareColors.onFound,
                trailing = entry.matchedAt?.let { TIME_FORMAT.format(Date(it)) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    SectionTitle(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * Une valeur de reference, sur son fond de couleur.
 *
 * Le fond porte toute la largeur de la ligne : sur un ecran tenu a bout de bras, une
 * pastille de couleur ne se voit pas, un aplat si.
 */
@Composable
private fun CompareRow(
    value: String,
    label: String?,
    background: Color,
    foreground: Color,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(background, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(value, style = ScanValueTextStyle, color = foreground)
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground,
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = foreground,
            )
        }
    }
}
