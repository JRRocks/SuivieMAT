package com.scanlist.app.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.scanlist.app.ui.common.SectionTitle
import com.scanlist.app.ui.compare.CompareCounterBar
import com.scanlist.app.ui.compare.compareSections
import com.scanlist.app.ui.theme.ScanValueTextStyle
import com.scanlist.app.ui.theme.compareColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Etape 4 de la refonte (variante B, choix de l'utilisateur) : la source de capture
 * (camera pour OCR/QR/code-barres, surface d'attente pour NFC) occupe toute la hauteur
 * sous l'en-tete ; les resultats vivent dans un tiroir qui montre les dernieres lignes et
 * se deplie d'un appui. La formule se replie en pastille sur l'image. Aucune validation,
 * aucune boite de dialogue pendant le scan — le retour se fait par vibration et par bip.
 *
 * En scan de dossier, le tiroir porte les captures, la plus recente en tete. En
 * comparaison, c'est la liste de reference qui se vide de son rouge.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var editorOpen by remember { mutableStateOf(false) }
    // Filtre de conformite optionnel en QR Code et code-barres, formule obligatoire en
    // OCR ; sans objet en NFC et en comparaison, ou la reference fait deja le tri.
    val hasPattern = isOcr || (!isNfc && !isCompare)

    val drawerState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = true),
    )
    val drawerExpanded = drawerState.bottomSheetState.targetValue == SheetValue.Expanded
    val scope = rememberCoroutineScope()
    // Le tiroir passe sous la barre de navigation systeme : sa hauteur repliee la compte.
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val drawerPeek = DRAWER_PEEK + navigationInset

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
        BottomSheetScaffold(
            scaffoldState = drawerState,
            sheetPeekHeight = drawerPeek,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShadowElevation = 8.dp,
            sheetDragHandle = { DrawerHandle() },
            sheetContent = {
                ScanDrawer(
                    title = stringResource(
                        if (isCompare) {
                            R.string.scan_drawer_reference
                        } else {
                            R.string.scan_drawer_captures
                        },
                    ),
                    expanded = drawerExpanded,
                    onToggle = {
                        scope.launch {
                            if (drawerExpanded) {
                                drawerState.bottomSheetState.partialExpand()
                            } else {
                                drawerState.bottomSheetState.expand()
                            }
                        }
                    },
                    bottomInset = navigationInset,
                    minHeight = drawerPeek,
                ) {
                    if (isCompare) {
                        // Les entrees encore rouges en tete : c'est ce qu'il reste a
                        // trouver, et donc ce que l'utilisateur doit voir sans deplier.
                        compareSections(missing = missing, found = found)
                    } else if (items.isEmpty()) {
                        item(key = "empty") { DrawerEmptyHint() }
                    } else {
                        items(items, key = { it.id }) { item ->
                            CapturedRow(item = item, onDelete = { viewModel.deleteItem(item) })
                            HorizontalDivider()
                        }
                    }
                }
            },
            containerColor = if (isNfc) MaterialTheme.colorScheme.background else Color.Black,
            // Seul le haut est pris a l'en-tete : le tiroir descend jusqu'au bord de
            // l'ecran, sous la barre de navigation systeme.
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        ) {
            // clipToBounds obligatoire : PreviewView met son rendu a l'echelle par une
            // transformation de matrice, qui deborde de ses limites sans decoupe et
            // viendrait s'afficher sous le tiroir. La zone s'arrete au bord du tiroir
            // replie, pour que le reticule reste centre dans ce qui est visible.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = drawerPeek)
                    .clipToBounds(),
            ) {
                if (isNfc) {
                    NfcScanSurface(
                        onTagRead = viewModel::onNfcTagRead,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (analyzer != null) {
                    // Fond noir de la camera : le message d'autorisation, s'il apparait,
                    // doit s'y lire en clair.
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
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
                }

                if (hasPattern) {
                    PatternPill(
                        isOcr = isOcr,
                        pattern = pattern,
                        validation = validation,
                        onClick = { editorOpen = true },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }

                LastEventBanner(
                    event = event,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // En OCR, le bouton de capture occupe deja le bas de l'image.
                        .padding(bottom = if (isOcr) 88.dp else 12.dp),
                )

                if (isOcr) {
                    CaptureToggleButton(
                        capturing = capturing,
                        canCapture = validation is RegexRuleEngine.Validation.Valid,
                        onToggleCapture = viewModel::toggleCapture,
                        onEditPattern = { editorOpen = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }
    }

    if (editorOpen) {
        PatternEditorSheet(
            isOcr = isOcr,
            pattern = pattern,
            validation = validation,
            groupCount = groupCount,
            groupIndex = groupIndex,
            onPatternChange = viewModel::setPattern,
            onGroupIndexChange = viewModel::setGroupIndex,
            onOpenLibrary = { libraryOpen = true },
            onDismiss = { editorOpen = false },
        )
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

/**
 * Hauteur du tiroir replie, hors barre de navigation systeme : sa poignee, son en-tete
 * et les deux dernieres lignes. Assez pour voir la lecture qu'on vient de faire, sans
 * prendre a la camera plus que necessaire.
 */
private val DRAWER_PEEK = 208.dp

/** Poignee du tiroir, plus fine que celle de Material pour laisser la place aux lignes. */
@Composable
private fun DrawerHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/**
 * Contenu du tiroir : un en-tete qui le deplie ou le replie d'un appui — le glisser
 * n'est pas le seul geste, on scanne parfois avec des gants — puis la liste.
 */
@Composable
private fun ScanDrawer(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    bottomInset: Dp,
    minHeight: Dp,
    content: LazyListScope.() -> Unit,
) {
    // Jamais plus court que sa hauteur repliee : un tiroir presque vide laisserait voir
    // le fond noir de la camera sous lui.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(text = title, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    if (expanded) R.string.scan_drawer_collapse else R.string.scan_drawer_expand,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = bottomInset),
            content = content,
        )
    }
}

@Composable
private fun DrawerEmptyHint() {
    Text(
        stringResource(R.string.scan_drawer_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
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
