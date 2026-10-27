package com.scanlist.app.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.domain.compare.SessionProgress
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.ui.common.SectionTitle
import com.scanlist.app.ui.theme.ScanValueTextStyle
import com.scanlist.app.ui.theme.compareColors
import com.scanlist.app.ui.theme.shellColors
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
            ProgressBar(found = found, total = total, height = 8.dp)
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
 * Avancement d'une session, dans l'en-tete de son ecran : le nombre restant a valider
 * en grand, puis la barre. C'est le chiffre sur lequel l'operateur agit ; le total
 * vient en second (etape 6 de la refonte).
 */
@Composable
fun CompareProgressHeader(
    found: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val missing = total - found
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                missing.toString(),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                pluralStringResource(R.plurals.compare_remaining_of, missing, total),
                style = MaterialTheme.typography.bodyLarge,
                color = shellColors.contentVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        ProgressBar(found = found, total = total, height = 8.dp)
    }
}

/**
 * Avancement d'une comparaison sous la ligne de son dossier, dans l'onglet Comparer :
 * ce qui reste a valider, le compte, et de quoi la rouvrir. Un bloc a part, pose sous
 * la ligne : un appui sur la ligne elle-meme lance une nouvelle comparaison.
 */
@Composable
fun SessionProgressStrip(
    progress: SessionProgress,
    onOpen: () -> Unit,
) {
    val colors = compareColors
    Column(
        modifier = Modifier
            .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (progress.isComplete) {
                Text(
                    stringResource(R.string.compare_progress_complete),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onFound,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    stringResource(R.string.compare_count_missing, progress.missing),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onMissing,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.compare_progress_ratio, progress.found, progress.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(
                    if (progress.isComplete) R.string.compare_open else R.string.compare_resume,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        ProgressBar(found = progress.found, total = progress.total, height = 6.dp)
    }
}

/** Barre verte sur fond rouge : la meme lecture que la liste qu'elle resume. */
@Composable
private fun ProgressBar(found: Int, total: Int, height: Dp) {
    val colors = compareColors
    LinearProgressIndicator(
        progress = { if (total > 0) found.toFloat() / total else 0f },
        color = colors.progress,
        trackColor = colors.missing,
        strokeCap = StrokeCap.Round,
        // Sans quoi Material 3 dessine un point et un espace en bout de barre, qui se
        // liraient comme un element a part.
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
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
        compareRows(missing)
    }

    if (found.isNotEmpty()) {
        item(key = "head-found") {
            SectionHeader(stringResource(R.string.compare_section_found, found.size))
        }
        compareRows(found)
    }
}

/**
 * Les entrees d'un seul etat, sans intitule : l'ecran de session les filtre par
 * « A valider / Valides », le selecteur tient lieu de titre.
 */
fun LazyListScope.compareRows(entries: List<ReferenceEntryEntity>) {
    items(entries, key = { it.state.name + "-" + it.id }) { entry ->
        val isFound = entry.state == ReferenceState.FOUND
        CompareRow(
            value = entry.value,
            label = entry.label,
            background = if (isFound) compareColors.found else compareColors.missing,
            foreground = if (isFound) compareColors.onFound else compareColors.onMissing,
            trailing = entry.matchedAt?.takeIf { isFound }?.let { TIME_FORMAT.format(Date(it)) },
        )
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
