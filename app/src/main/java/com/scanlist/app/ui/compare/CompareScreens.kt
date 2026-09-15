package com.scanlist.app.ui.compare

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanlist.app.R
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.ui.common.EmptyState
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.common.MenuCard
import com.scanlist.app.ui.common.ScanListScaffold
import com.scanlist.app.ui.common.ScanModePicker

/** Comparer : choix de la source de la liste de reference. */
@Composable
fun CompareSourceScreen(
    onBack: () -> Unit,
    onFromFolder: () -> Unit,
    onFromCsv: () -> Unit,
) {
    ScanListScaffold(title = stringResource(R.string.compare_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.compare_source),
                style = MaterialTheme.typography.titleMedium,
            )
            MenuCard(
                title = stringResource(R.string.compare_source_folder),
                subtitle = stringResource(R.string.compare_source_folder_desc),
                icon = Icons.Default.Folder,
                onClick = onFromFolder,
            )
            MenuCard(
                title = stringResource(R.string.compare_source_csv),
                subtitle = stringResource(R.string.compare_source_csv_desc),
                icon = Icons.Default.UploadFile,
                onClick = onFromCsv,
            )
        }
    }
}

/** Lancer le scan d'une comparaison : OCR / Code 2D / Code-barres / NFC. */
@Composable
fun CompareScanModeScreen(
    onBack: () -> Unit,
    onModeSelected: (ScanMode) -> Unit,
    viewModel: CompareSessionViewModel = hiltViewModel(),
) {
    val references by viewModel.references.collectAsStateWithLifecycle()
    val missing = references.count { it.state == ReferenceState.MISSING }

    ScanListScaffold(title = stringResource(R.string.compare_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.choose_scan_mode),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.compare_remaining, missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScanModePicker(onModeSelected = onModeSelected)
        }
    }
}

/**
 * Resultats d'une session.
 *
 * La liste de reference et son etat : rouge tant que la presence physique n'est pas
 * confirmee, vert des qu'elle l'est. C'est le seul contenu de l'ecran — une valeur
 * lue hors de cette liste n'est pas prise en compte par la comparaison.
 */
@Composable
fun CompareSessionScreen(
    onBack: () -> Unit,
    onLaunchScan: () -> Unit,
    viewModel: CompareSessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val references by viewModel.references.collectAsStateWithLifecycle()
    val resultFolderName by viewModel.resultFolderName.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val found = references.filter { it.state == ReferenceState.FOUND }
    val missing = references.filter { it.state == ReferenceState.MISSING }

    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHostState.current
    val messageText = message?.let { stringResource(it) }

    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ScanListScaffold(
        title = session?.name ?: stringResource(R.string.compare_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CompareCounterBar(found = found.size, missing = missing.size)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onLaunchScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text(
                        stringResource(R.string.compare_launch_scan),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = viewModel::saveToFolder,
                        // Deja enregistree : le depot renverrait le meme dossier, mais
                        // proposer le bouton laisserait croire a une seconde sauvegarde.
                        enabled = resultFolderName == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.compare_save_session))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.buildShareIntent { intent ->
                                // Aucune application de messagerie n'est garantie
                                // presente : l'echec reste un message, pas un plantage.
                                try {
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    viewModel.reportNoApp()
                                }
                            }
                        },
                        enabled = references.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Text(
                            stringResource(R.string.compare_export),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                resultFolderName?.let { name ->
                    Text(
                        stringResource(R.string.compare_saved_in, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            if (references.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.compare_reference_empty),
                    hint = stringResource(R.string.compare_reference_empty_hint),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    compareSections(missing = missing, found = found)
                }
            }
        }
    }
}
