package com.scanlist.app.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.ui.common.ScanListScaffold
import com.scanlist.app.ui.common.ScanModePicker
import com.scanlist.app.ui.compare.CompareCounterBar
import com.scanlist.app.ui.compare.compareSections
import com.scanlist.app.ui.theme.ScanValueTextStyle
import com.scanlist.app.ui.theme.compareColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

/** Duree d'affichage du bandeau de derniere capture. */
private const val BANNER_DURATION_MS = 1_400L

/** Nouveau scan : choix OCR / Code 2D / Code-barres / NFC. */
@Composable
fun NewScanModeScreen(
    onBack: () -> Unit,
    onModeSelected: (ScanMode) -> Unit,
) {
    ScanListScaffold(title = stringResource(R.string.menu_new_scan), onBack = onBack) { padding ->
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
            ScanModePicker(onModeSelected = onModeSelected)
        }
    }
}

/**
 * Ecran de scan, pour les deux contextes.
 *
 * Source de capture en haut (camera pour OCR/QR/code-barres, surface d'attente pour
 * NFC), resultat en dessous : l'utilisateur voit sa lecture prise en compte sans
 * quitter des yeux la zone de detection. Aucune validation, aucune boite de dialogue —
 * le retour se fait par vibration et par bip.
 *
 * En scan de dossier, le bas de l'ecran est la liste des captures qui s'allonge. En
 * comparaison, c'est la liste de reference qui se vide de son rouge.
 */
@Composable
fun ScanScreen(
    mode: ScanMode,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val analyzer = viewModel.analyzer
    val isOcr = mode == ScanMode.OCR
    val isNfc = mode == ScanMode.NFC
    val isCompare = viewModel.isCompare

    val items by viewModel.items.collectAsStateWithLifecycle()
    val folderName by viewModel.folderName.collectAsStateWithLifecycle()
    val sessionName by viewModel.sessionName.collectAsStateWithLifecycle()
    val references by viewModel.references.collectAsStateWithLifecycle()
    val torch by viewModel.torch.collectAsStateWithLifecycle()
    val event by viewModel.lastEvent.collectAsStateWithLifecycle()
    val consensusProgress by viewModel.consensusProgress.collectAsStateWithLifecycle()

    val pattern by viewModel.pattern.collectAsStateWithLifecycle()
    val validation by viewModel.validation.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val capturing by viewModel.capturing.collectAsStateWithLifecycle()
    val groupCount by viewModel.groupCount.collectAsStateWithLifecycle()
    val groupIndex by viewModel.groupIndex.collectAsStateWithLifecycle()
    val savedRules by viewModel.savedRules.collectAsStateWithLifecycle()
    val activeRuleId by viewModel.activeRuleId.collectAsStateWithLifecycle()

    var libraryOpen by remember { mutableStateOf(false) }

    KeepScreenOn()

    // Le bandeau s'efface seul : il ne doit jamais masquer la capture suivante.
    LaunchedEffect(event) {
        if (event != null) {
            delay(BANNER_DURATION_MS)
            viewModel.consumeEvent()
        }
    }

    val found = references.filter { it.state == ReferenceState.FOUND }
    val missing = references.filter { it.state == ReferenceState.MISSING }

    ScanListScaffold(
        title = (if (isCompare) sessionName else folderName)
            ?: "${stringResource(R.string.scan_title)} — ${mode.label}",
        onBack = onBack,
        actions = {
            // Pas de camera en NFC : la torche n'a pas de sens dans ce mode.
            if (!isNfc) {
                IconButton(onClick = viewModel::toggleTorch) {
                    Icon(
                        if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = stringResource(
                            if (torch) R.string.scan_torch_off else R.string.scan_torch_on,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // clipToBounds obligatoire : PreviewView met son rendu a l'echelle par une
            // transformation de matrice, qui deborde de ses limites sans decoupe et
            // vient s'afficher par-dessus la liste des captures. Sans objet en NFC,
            // qui partage cette meme zone avec une surface statique.
            //
            // En OCR la camera cede de la place au panneau de reglage : l'utilisateur
            // a besoin de voir sa formule et son apercu autant que l'etiquette.
            Box(
                modifier = Modifier
                    .weight(
                        when {
                            isOcr -> 0.32f
                            isNfc || isCompare -> 0.55f
                            else -> 0.50f
                        },
                    )
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                if (isNfc) {
                    NfcScanSurface(
                        onTagRead = viewModel::onNfcTagRead,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (analyzer != null) {
                    CameraPermissionGate {
                        CameraPreview(
                            analyzer = analyzer,
                            torchEnabled = torch,
                            modifier = Modifier.fillMaxSize(),
                            // Les codes 2D (PDF417, Data Matrix) ont des modules bien
                            // plus fins : ils demandent plus de pixels que le 1D.
                            analysisResolution = analysisResolutionFor(mode),
                            // Comparaison : le consensus ne s'y applique pas (portee
                            // validee), le reticule reste dans son etat d'origine.
                            consensusProgress = if (isCompare) 0f else consensusProgress,
                            onError = viewModel::onCameraError,
                        )
                    }
                }
                LastEventBanner(
                    event = event,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                )
            }

            if (isOcr) {
                HorizontalDivider()
                OcrControlPanel(
                    pattern = pattern,
                    validation = validation,
                    preview = preview,
                    capturing = capturing,
                    groupCount = groupCount,
                    groupIndex = groupIndex,
                    onPatternChange = viewModel::setPattern,
                    onGroupIndexChange = viewModel::setGroupIndex,
                    onToggleCapture = viewModel::toggleCapture,
                    onOpenLibrary = { libraryOpen = true },
                    modifier = Modifier.weight(0.40f),
                )
            } else if (!isNfc && !isCompare) {
                // Filtre de conformite optionnel, meme bibliotheque de formules que
                // l'OCR. Sans objet en comparaison : la reference fait deja le tri.
                HorizontalDivider()
                BarcodeFilterPanel(
                    pattern = pattern,
                    validation = validation,
                    onPatternChange = viewModel::setPattern,
                    onOpenLibrary = { libraryOpen = true },
                )
            }

            if (isCompare) {
                CompareCounterBar(found = found.size, missing = missing.size)
            } else {
                CaptureHeader(
                    count = items.size,
                    folderName = folderName,
                    mode = mode,
                )
            }
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .weight(
                        when {
                            isOcr -> 0.28f
                            isNfc || isCompare -> 0.45f
                            else -> 0.38f
                        },
                    )
                    .fillMaxWidth(),
            ) {
                if (isCompare) {
                    // Les entrees encore rouges en tete : c'est ce qu'il reste a
                    // trouver, et donc ce que l'utilisateur doit voir sans defiler.
                    compareSections(missing = missing, found = found)
                } else {
                    items(items, key = { it.id }) { item ->
                        CapturedRow(item = item, onDelete = { viewModel.deleteItem(item) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (libraryOpen) {
        RegexLibrarySheet(
            rules = savedRules,
            currentPattern = pattern,
            activeRuleId = activeRuleId,
            canSave = validation is RegexRuleEngine.Validation.Valid && activeRuleId == null,
            onApply = viewModel::applyRule,
            onDelete = viewModel::deleteRule,
            onSave = viewModel::saveCurrentRule,
            onDismiss = { libraryOpen = false },
        )
    }
}

@Composable
private fun CaptureHeader(count: Int, folderName: String?, mode: ScanMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.scan_captured, count),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                folderName ?: stringResource(R.string.scan_new_folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            mode.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Bandeau de la derniere lecture.
 *
 * Vert pour une prise en compte, gris pour une lecture sans effet : les memes
 * couleurs que la fonction Comparer, pour ne pas avoir deux vocabulaires visuels
 * dans l'application.
 */
@Composable
private fun LastEventBanner(event: ScanEvent?, modifier: Modifier = Modifier) {
    val colors = compareColors

    AnimatedVisibility(visible = event != null, modifier = modifier) {
        val (background, foreground, label) = when (event) {
            is ScanEvent.Saved -> Triple(
                colors.found,
                colors.onFound,
                stringResource(R.string.scan_saved, event.value),
            )

            is ScanEvent.Matched -> Triple(
                colors.found,
                colors.onFound,
                stringResource(R.string.compare_matched, event.value),
            )

            is ScanEvent.Duplicate -> Triple(
                colors.neutral,
                colors.onNeutral,
                "${stringResource(R.string.scan_duplicate)} : ${event.value}",
            )

            is ScanEvent.AlreadyMatched -> Triple(
                colors.neutral,
                colors.onNeutral,
                stringResource(R.string.compare_already_matched, event.value),
            )

            // Gris, et non rouge : le rouge est reserve aux entrees de reference qui
            // restent a valider. Une lecture hors liste n'est pas une erreur, elle
            // n'est simplement pas prise en compte.
            is ScanEvent.OutOfList -> Triple(
                colors.neutral,
                colors.onNeutral,
                stringResource(R.string.compare_out_of_list, event.value),
            )

            is ScanEvent.Error -> Triple(colors.missing, colors.onMissing, event.message)
            is ScanEvent.Notice -> Triple(
                colors.neutral,
                colors.onNeutral,
                stringResource(event.messageRes),
            )

            null -> Triple(colors.neutral, colors.onNeutral, "")
        }

        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(background, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CapturedRow(item: ScanItemEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.value, style = ScanValueTextStyle)
            Text(
                buildString {
                    item.symbology?.let { append(it).append(" · ") }
                    append(TIME_FORMAT.format(Date(item.scannedAt)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.scan_delete_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Garde l'ecran allume : un scan a la chaine ne doit pas s'interrompre en veille. */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
