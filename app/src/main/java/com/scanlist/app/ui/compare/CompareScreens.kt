package com.scanlist.app.ui.compare

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.FolderSummary
import com.scanlist.app.domain.compare.CompareEntry
import com.scanlist.app.domain.compare.SessionProgress
import com.scanlist.app.domain.folders.FolderTimeline
import com.scanlist.app.domain.model.CompareSourceKind
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.ui.common.EmptyState
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.common.PrimaryButton
import com.scanlist.app.ui.common.ScanListScaffold
import com.scanlist.app.ui.common.ScanModePicker
import com.scanlist.app.ui.common.SecondaryButton
import com.scanlist.app.ui.common.SectionTitle
import com.scanlist.app.ui.common.TimelineRow
import com.scanlist.app.ui.common.icon
import com.scanlist.app.ui.common.label
import com.scanlist.app.ui.common.momentLabel
import com.scanlist.app.ui.theme.compareColors
import java.time.ZoneId

/**
 * Onglet Comparer (etape 6 de la refonte, variante G, decision 30).
 *
 * Une seule liste par date, comme l'onglet Dossiers : chaque dossier peut servir de
 * liste de reference, et celui qu'une comparaison a deja utilise porte son avancement
 * et de quoi la reprendre. Un import .csv prend place a sa date, puisque son fichier
 * ne peut pas etre relu : seule sa session le represente.
 */
@Composable
fun CompareHomeScreen(
    onImportCsv: () -> Unit,
    onNewFromFolder: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: CompareHomeViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<CompareEntry.FromFolder<FolderSummary>?>(null) }

    ScanListScaffold(
        title = stringResource(R.string.compare_title),
        subtitle = stringResource(R.string.compare_subtitle),
    ) { padding ->
        val list = entries
        // Fige a chaque changement de la liste, comme l'onglet Dossiers.
        val now = remember(list) { System.currentTimeMillis() }
        val zone = remember { ZoneId.systemDefault() }
        val groups = remember(list, now) {
            list?.let { FolderTimeline.group(it, { entry -> entry.timestamp }, now, zone) }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            item(key = "import") {
                SecondaryButton(
                    text = stringResource(R.string.compare_import_csv),
                    icon = Icons.Default.UploadFile,
                    onClick = onImportCsv,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (list != null && list.isEmpty()) {
                item(key = "vide") {
                    EmptyState(
                        title = stringResource(R.string.compare_empty),
                        hint = stringResource(R.string.compare_empty_hint),
                    )
                }
            }
            groups?.forEach { (period, inPeriod) ->
                item(key = "periode-" + period.name) {
                    SectionTitle(
                        text = stringResource(period.label),
                        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(inPeriod, key = { _, entry -> entry.key }) { index, entry ->
                    CompareEntryRow(
                        entry = entry,
                        moment = momentLabel(FolderTimeline.momentOf(entry.timestamp, now, zone)),
                        isFirst = index == 0,
                        isLast = index == inPeriod.lastIndex,
                        onFolder = { folderEntry ->
                            // Une comparaison existe deja : la remplacer sans prevenir
                            // cacherait son avancement derriere une liste toute rouge.
                            if (folderEntry.session != null) {
                                pending = folderEntry
                            } else {
                                onNewFromFolder(folderEntry.folder.id)
                            }
                        },
                        onOpenSession = onOpenSession,
                    )
                }
            }
        }
    }

    pending?.let { entry ->
        val session = entry.session ?: return@let
        ExistingComparisonDialog(
            folderName = entry.folder.name,
            session = session,
            onResume = {
                pending = null
                onOpenSession(session.sessionId)
            },
            onNew = {
                pending = null
                onNewFromFolder(entry.folder.id)
            },
            onDismiss = { pending = null },
        )
    }
}

private val CompareEntry<FolderSummary>.key: String
    get() = when (this) {
        is CompareEntry.FromFolder -> "dossier-" + folder.id
        is CompareEntry.Standalone -> "session-" + session.sessionId
    }

@Composable
private fun CompareEntryRow(
    entry: CompareEntry<FolderSummary>,
    moment: String,
    isFirst: Boolean,
    isLast: Boolean,
    onFolder: (CompareEntry.FromFolder<FolderSummary>) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    when (entry) {
        is CompareEntry.FromFolder -> {
            val folder = entry.folder
            val isComparison = folder.kind == FolderKind.COMPARISON
            TimelineRow(
                icon = if (isComparison) {
                    Icons.AutoMirrored.Filled.FactCheck
                } else {
                    folder.defaultScanMode.icon
                },
                title = folder.name,
                meta = stringResource(
                    R.string.folder_row_meta,
                    if (isComparison) {
                        stringResource(R.string.folder_kind_comparison_title)
                    } else {
                        folder.defaultScanMode.label
                    },
                    moment,
                ),
                count = folder.itemCount,
                isFirst = isFirst,
                isLast = isLast,
                onClick = { onFolder(entry) },
                below = {
                    entry.session?.let { session ->
                        SessionProgressStrip(session, onOpen = { onOpenSession(session.sessionId) })
                    }
                },
            )
        }

        is CompareEntry.Standalone -> {
            val session = entry.session
            val isCsv = session.sourceKind == CompareSourceKind.CSV_IMPORT
            TimelineRow(
                icon = if (isCsv) {
                    Icons.Default.Description
                } else {
                    Icons.AutoMirrored.Filled.FactCheck
                },
                // Le nom du fichier est ce que l'operateur a choisi ; celui de la session
                // n'en est qu'un derive (« Comparaison armurerie.csv »).
                title = if (isCsv) session.sourceRef else session.name,
                meta = stringResource(
                    R.string.folder_row_meta,
                    stringResource(
                        if (isCsv) {
                            R.string.compare_kind_csv
                        } else {
                            R.string.folder_kind_comparison_title
                        },
                    ),
                    moment,
                ),
                count = session.total,
                isFirst = isFirst,
                isLast = isLast,
                onClick = { onOpenSession(session.sessionId) },
                below = {
                    SessionProgressStrip(session, onOpen = { onOpenSession(session.sessionId) })
                },
            )
        }
    }
}

@Composable
private fun ExistingComparisonDialog(
    folderName: String,
    session: SessionProgress,
    onResume: () -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_existing_title)) },
        text = {
            Text(
                stringResource(
                    R.string.compare_existing_text,
                    folderName,
                    session.found,
                    session.total,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) { Text(stringResource(R.string.compare_resume)) }
        },
        dismissButton = {
            TextButton(onClick = onNew) { Text(stringResource(R.string.compare_existing_new)) }
        },
    )
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

    ScanListScaffold(
        title = stringResource(R.string.compare_launch_scan),
        subtitle = stringResource(R.string.compare_remaining, missing),
        onBack = onBack,
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
 * Une session de comparaison (etape 6 de la refonte).
 *
 * L'en-tete dit combien il reste a valider ; dessous, un selecteur montre soit ce qui
 * reste (rouge), soit ce qui est fait (vert). La liste de reference est le seul
 * contenu : une valeur lue hors de cette liste n'est pas prise en compte. « Lancer le
 * scan » est sous le pouce ; enregistrer et exporter, gestes de fin de controle,
 * passent en icones dans l'en-tete.
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
    var showFound by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

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
        subtitle = resultFolderName?.let { stringResource(R.string.compare_saved_in, it) },
        onBack = onBack,
        actions = {
            IconButton(
                onClick = viewModel::saveToFolder,
                // Deja enregistree : le depot renverrait le meme dossier, mais proposer
                // l'action laisserait croire a une seconde sauvegarde.
                enabled = resultFolderName == null,
            ) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.compare_save_session),
                )
            }
            IconButton(
                onClick = {
                    viewModel.buildShareIntent { intent ->
                        // Aucune application de messagerie n'est garantie presente :
                        // l'echec reste un message, pas un plantage.
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.reportNoApp()
                        }
                    }
                },
                enabled = references.isNotEmpty(),
            ) {
                Icon(
                    Icons.Default.IosShare,
                    contentDescription = stringResource(R.string.compare_export),
                )
            }
            SessionOverflowMenu(onDelete = { confirmingDelete = true })
        },
        // Masque tant que la liste n'est pas chargee : « 0 reste a valider sur 0 » le
        // temps d'une lecture en base serait faux.
        headerBottom = if (references.isEmpty()) {
            null
        } else {
            { CompareProgressHeader(found = found.size, total = references.size) }
        },
        bottomBar = { LaunchScanBar(onLaunchScan) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (references.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.compare_reference_empty),
                    hint = stringResource(R.string.compare_reference_empty_hint),
                )
            } else {
                StateFilter(
                    missingCount = missing.size,
                    foundCount = found.size,
                    showFound = showFound,
                    onSelect = { showFound = it },
                )
                val shown = if (showFound) found else missing
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        shown.isNotEmpty() -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            compareRows(shown)
                        }

                        showFound -> EmptyState(
                            title = stringResource(R.string.compare_none_found),
                            hint = stringResource(R.string.compare_none_found_hint),
                        )

                        else -> EmptyState(
                            title = stringResource(R.string.compare_all_found),
                            hint = stringResource(R.string.compare_all_found_hint),
                        )
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.compare_delete)) },
            text = {
                Text(stringResource(R.string.compare_delete_confirm, session?.name.orEmpty()))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.deleteSession(onBack)
                    },
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Selecteur « A valider / Valides ». Chaque segment prend, une fois choisi, la
 * couleur des lignes qu'il montre : le rouge et le vert restent les seuls codes.
 */
@Composable
private fun StateFilter(
    missingCount: Int,
    foundCount: Int,
    showFound: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    val colors = compareColors
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(4.dp),
        ) {
            FilterSegment(
                text = stringResource(R.string.compare_filter_missing, missingCount),
                selected = !showFound,
                selectedColor = colors.missing,
                selectedContent = colors.onMissing,
                onClick = { onSelect(false) },
                modifier = Modifier.weight(1f),
            )
            FilterSegment(
                text = stringResource(R.string.compare_filter_found, foundCount),
                selected = showFound,
                selectedColor = colors.found,
                selectedContent = colors.onFound,
                onClick = { onSelect(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilterSegment(
    text: String,
    selected: Boolean,
    selectedColor: Color,
    selectedContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) selectedColor else Color.Transparent,
        contentColor = if (selected) {
            selectedContent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = if (selected) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
            )
        }
    }
}

/** « Lancer le scan », fixe sous le pouce quelle que soit la longueur de la liste. */
@Composable
private fun LaunchScanBar(onLaunchScan: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PrimaryButton(
                text = stringResource(R.string.compare_launch_scan),
                icon = Icons.Default.QrCodeScanner,
                onClick = onLaunchScan,
                modifier = Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SessionOverflowMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.compare_delete)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}
