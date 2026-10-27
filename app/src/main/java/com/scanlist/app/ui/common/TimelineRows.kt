package com.scanlist.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.domain.folders.ActivityMoment
import com.scanlist.app.domain.folders.FolderPeriod
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Briques des listes rangees par date : l'onglet Dossiers (etape 5 de la refonte) et
 * l'onglet Comparer (etape 6), qui doivent se lire de la meme facon.
 */

private val DAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)
private val FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

@get:StringRes
val FolderPeriod.label: Int
    get() = when (this) {
        FolderPeriod.TODAY -> R.string.folders_period_today
        FolderPeriod.THIS_WEEK -> R.string.folders_period_week
        FolderPeriod.OLDER -> R.string.folders_period_older
    }

@Composable
fun momentLabel(moment: ActivityMoment): String = when (moment) {
    is ActivityMoment.Time -> stringResource(R.string.folder_when_time, moment.hour, moment.minute)
    ActivityMoment.Yesterday -> stringResource(R.string.folder_when_yesterday)
    is ActivityMoment.Weekday -> moment.day.getDisplayName(TextStyle.FULL, Locale.FRANCE)
    is ActivityMoment.Day -> DAY_MONTH_FORMAT.format(moment.date)
    is ActivityMoment.DayOfOtherYear -> FULL_DATE_FORMAT.format(moment.date)
}

/**
 * Une ligne de liste par date : un pictogramme, le nom, une ligne de details, le nombre
 * d'elements a droite. Les lignes d'une meme periode forment une seule carte, arrondie
 * a ses deux bouts.
 *
 * [below] ajoute un bloc sous la ligne, dans la meme carte et hors de sa zone
 * d'appui : l'avancement d'une comparaison dans l'onglet Comparer.
 */
@Composable
fun TimelineRow(
    icon: ImageVector,
    title: String,
    meta: String,
    count: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    below: @Composable () -> Unit = {},
) {
    val top = if (isFirst) 20.dp else 0.dp
    val bottom = if (isLast) 20.dp else 0.dp
    val shape = RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
    val countDescription = pluralStringResource(R.plurals.folder_item_label, count)
        .let { "$count $it" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = countDescription },
            )
        }
        below()
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
