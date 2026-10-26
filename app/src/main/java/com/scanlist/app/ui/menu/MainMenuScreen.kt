package com.scanlist.app.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.ui.common.MenuCard
import com.scanlist.app.ui.common.ScanListScaffold

/**
 * Menu principal : les trois branches de la specification, dans l'ordre impose.
 *
 * "Nouveau scan" est teint du bleu de l'en-tete : c'est le geste de tous les jours, il
 * doit se trouver sans lire.
 */
@Composable
fun MainMenuScreen(
    onFolders: () -> Unit,
    onCompare: () -> Unit,
    onNewScan: () -> Unit,
) {
    ScanListScaffold(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.menu_title),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuCard(
                title = stringResource(R.string.menu_folders),
                subtitle = stringResource(R.string.menu_folders_subtitle),
                icon = Icons.Default.Folder,
                onClick = onFolders,
            )
            MenuCard(
                title = stringResource(R.string.menu_compare),
                subtitle = stringResource(R.string.menu_compare_subtitle),
                icon = Icons.AutoMirrored.Filled.FactCheck,
                onClick = onCompare,
            )
            MenuCard(
                title = stringResource(R.string.menu_new_scan),
                subtitle = stringResource(R.string.menu_new_scan_subtitle),
                icon = Icons.Default.QrCodeScanner,
                emphasized = true,
                onClick = onNewScan,
            )
        }
    }
}
