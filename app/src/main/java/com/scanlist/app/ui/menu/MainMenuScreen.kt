package com.scanlist.app.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Nfc
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
 * [onCardTest] n'est fourni que dans les APK de debug : l'ecran de test du badge est un
 * outil de diagnostic hors de la hierarchie imposee (decision 22), il ne doit pas
 * apparaitre dans la version distribuee.
 */
@Composable
fun MainMenuScreen(
    onFolders: () -> Unit,
    onCompare: () -> Unit,
    onNewScan: () -> Unit,
    onCardTest: (() -> Unit)? = null,
) {
    ScanListScaffold(title = stringResource(R.string.menu_title)) { padding ->
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
                icon = Icons.Default.Compare,
                onClick = onCompare,
            )
            MenuCard(
                title = stringResource(R.string.menu_new_scan),
                subtitle = stringResource(R.string.menu_new_scan_subtitle),
                icon = Icons.Default.QrCodeScanner,
                onClick = onNewScan,
            )
            if (onCardTest != null) {
                MenuCard(
                    title = stringResource(R.string.menu_card_test),
                    subtitle = stringResource(R.string.menu_card_test_subtitle),
                    icon = Icons.Default.Nfc,
                    onClick = onCardTest,
                )
            }
        }
    }
}
