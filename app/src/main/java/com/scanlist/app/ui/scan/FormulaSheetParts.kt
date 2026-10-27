package com.scanlist.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import kotlinx.coroutines.launch

/*
 * Pieces communes des fenetres de formules (etape 7 de la refonte, variante C) : un
 * titre avec sa croix de fermeture, une liste ou l'on coche, puis un seul bouton fixe
 * pour valider le choix. Cocher ne fait rien d'autre que cocher : rien ne s'applique
 * sur un appui de travers.
 */

/** Ferme la feuille en l'animant, puis previent l'appelant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun sheetCloser(sheetState: SheetState, onDismiss: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
}

/** Titre d'une feuille, ses actions eventuelles, puis la croix : fermer est en haut. */
@Composable
internal fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        actions()
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
        }
    }
}

/**
 * Le bouton qui valide le choix, fixe au bas de la feuille : il reste sous le pouce
 * quelle que soit la longueur de la liste au-dessus.
 */
@Composable
internal fun SheetActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * Carte blanche qui regroupe les lignes a cocher : un seul groupe, que le lecteur
 * d'ecran annonce comme un choix parmi plusieurs.
 */
@Composable
internal fun ChoiceList(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.selectableGroup(), content = content)
    }
}

/**
 * Une ligne a cocher. La ligne cochee prend le fond teinte : on voit d'un coup d'oeil
 * ce que le bouton du bas va appliquer.
 */
@Composable
internal fun ChoiceRow(
    selected: Boolean,
    onSelect: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 56.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // onClick nul : c'est la ligne entiere qui coche, pas le seul rond.
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            RadioButton(selected = selected, onClick = null)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        trailing()
    }
}
