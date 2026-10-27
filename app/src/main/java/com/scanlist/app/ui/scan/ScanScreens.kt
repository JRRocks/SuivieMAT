package com.scanlist.app.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.pluralStringResource
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
import com.scanlist.app.ui.common.HeaderCounter
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

/**
 * Duree d'affichage du bandeau de derniere capture.
 *
 * A 0 sur decision explicite de l'utilisateur : outil pro, la rapidite prime sur le
 * confort visuel. Le retour reste assure par la vibration et le bip (ScanFeedback),
 * independants de ce delai.
 */
private const val BANNER_DURATION_MS = 0L

/**
 * Onglet Nouveau scan : choix OCR / Code 2D / Code-barres / NFC.
 *
 * C'est l'ecran d'ouverture de l'application (decision 28) : le geste de tous les jours
 * est a un appui du lancement.
 */
@Composable
fun NewScanModeScreen(
    onModeSelected: (ScanMode) -> Unit,
) {
    ScanListScaffold(
        title = stringResource(R.string.menu_new_scan),
        subtitle = stringResource(R.string.choose_scan_mode),
    ) { padding ->
        ScanModePicker(
            onModeSelected = onModeSelected,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
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

    // Le compteur vit dans l'en-tete bleu : le nombre de captures a cote du nom du
    // dossier, l'avancement sous le nom de la session. Il reste visible quelle que soit
    // la longueur de la liste en dessous.
    val isNewFolder = !isCompare && folderName == null
    ScanListScaffold(
        title = when {
            isCompare -> sessionName ?: stringResource(R.string.compare_title)
            else -> folderName ?: stringResource(R.string.scan_new_folder_title)
        },
        subtitle = if (isNewFolder) {
            stringResource(R.string.scan_subtitle_new_folder, mode.label)
        } else {
            mode.label
        },
        onBack = onBack,
        headerTrailing = if (isCompare) {
            null
        } else {
            {
                HeaderCounter(
                    value = items.size.toString(),
                    label = pluralStringResource(R.plurals.scan_capture_label, items.size),
                )
            }
        },
        headerBottom = if (isCompare) {
            { CompareCounterBar(found = found.size, missing = missing.size) }
        } else {
            null
        },
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
            // Les panneaux de formule (OCR, filtre de conformite) n'ont pas de poids :
            // ils prennent la hauteur de leur contenu, la camera et la liste se
            // partagent le reste. Depuis le retrait de l'apercu OCR (decision 26), un
            // poids fixe ne laisserait qu'un vide sous le bouton de capture.
            Box(
                modifier = Modifier
                    .weight(
                        when {
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
                    capturing = capturing,
                    groupCount = groupCount,
                    groupIndex = groupIndex,
                    onPatternChange = viewModel::setPattern,
                    onGroupIndexChange = viewModel::setGroupIndex,
                    onToggleCapture = viewModel::toggleCapture,
                    onOpenLibrary = { libraryOpen = true },
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

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .weight(
                        when {
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
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(background, MaterialTheme.shapes.medium)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CapturedRow(item: ScanItemEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
