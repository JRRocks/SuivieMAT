package com.scanlist.app.ui.cardtest

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanlist.app.R
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.common.ScanListScaffold
import kotlinx.coroutines.launch

/**
 * Ecran de diagnostic du badge sans contact : niveaux 2 (certificat) et 3 (defi-reponse).
 *
 * Present dans les seuls APK de debug (decision 22 de contexte.md) : c'est un outil de
 * test physique, pas une fonction de l'application.
 */
@Composable
fun CardTestScreen(
    onBack: () -> Unit,
    viewModel: CardTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val availability = rememberCardReader(onTag = viewModel::onTag)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.card_test_copied)

    ScanListScaffold(title = stringResource(R.string.card_test_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state = state, availability = availability)

            val done = state as? CardTestState.Done
            if (done != null) {
                val sections = remember(done) { CardReportFormatter.sections(context.resources, done.outcome) }
                sections.forEach { ReportSectionCard(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(CardReportFormatter.toText(context.resources, sections)))
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        },
                    ) {
                        Text(stringResource(R.string.card_test_copy))
                    }
                    OutlinedButton(onClick = viewModel::reset) {
                        Text(stringResource(R.string.card_test_clear))
                    }
                }
            }
        }
    }
}

private enum class NfcAvailability { MISSING, DISABLED, READY }

/**
 * Mode lecteur reserve aux cartes a microprocesseur, arme sur ON_RESUME et desarme sur
 * ON_PAUSE comme dans NfcScanSurface : un retour des reglages systeme doit retrouver un
 * lecteur arme.
 *
 * Volontairement distinct de NfcScanSurface plutot que partage : cet ecran de test est
 * provisoire, et le chemin de scan NFC ne doit pas changer pour lui.
 */
@Composable
private fun rememberCardReader(onTag: (Tag) -> Unit): NfcAvailability {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val currentOnTag by rememberUpdatedState(onTag)
    var enabled by remember { mutableStateOf(nfcAdapter?.isEnabled == true) }

    DisposableEffect(lifecycleOwner, activity, nfcAdapter) {
        if (activity == null || nfcAdapter == null) {
            return@DisposableEffect onDispose {}
        }

        val callback = NfcAdapter.ReaderCallback { tag -> currentOnTag(tag) }
        // Types A et B seulement : ce sont les deux porteurs d'ISO 14443-4, le protocole des
        // cartes a microprocesseur. SKIP_NDEF_CHECK : un badge ne porte pas de contenu NDEF,
        // la sonde du systeme ne ferait que retarder le premier echange.
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    nfcAdapter.enableReaderMode(activity, callback, flags, null)
                    enabled = nfcAdapter.isEnabled
                }
                Lifecycle.Event.ON_PAUSE -> runCatching { nfcAdapter.disableReaderMode(activity) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { nfcAdapter.disableReaderMode(activity) }
        }
    }

    return when {
        nfcAdapter == null -> NfcAvailability.MISSING
        !enabled -> NfcAvailability.DISABLED
        else -> NfcAvailability.READY
    }
}

@Composable
private fun StatusCard(state: CardTestState, availability: NfcAvailability) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                availability == NfcAvailability.MISSING ->
                    Text(stringResource(R.string.nfc_unavailable), textAlign = TextAlign.Center)
                availability == NfcAvailability.DISABLED -> {
                    Text(stringResource(R.string.nfc_disabled), textAlign = TextAlign.Center)
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                        Text(stringResource(R.string.nfc_enable_settings))
                    }
                }
                state == CardTestState.Running -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.card_test_running), textAlign = TextAlign.Center)
                }
                else -> {
                    Icon(
                        Icons.Default.Nfc,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    val message = if (state is CardTestState.Done) R.string.card_test_done else R.string.card_test_instruction
                    Text(stringResource(message), textAlign = TextAlign.Center)
                }
            }
            Text(
                stringResource(R.string.card_test_pin_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReportSectionCard(section: ReportSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleSmall)
            section.lines.forEach { ReportLineRow(it) }
        }
    }
}

@Composable
private fun ReportLineRow(line: ReportLine) {
    Row(verticalAlignment = Alignment.Top) {
        when (line.status) {
            LineStatus.OK -> StatusIcon(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
            LineStatus.FAILED -> StatusIcon(Icons.Default.Cancel, MaterialTheme.colorScheme.error)
            LineStatus.INFO -> Spacer(Modifier.width(STATUS_ICON_SIZE))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                line.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Selectionnable : un sujet de certificat ou un AID se recopie plus surement
            // qu'il ne se retape.
            SelectionContainer {
                Text(line.value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(STATUS_ICON_SIZE))
}

private val STATUS_ICON_SIZE = 20.dp
