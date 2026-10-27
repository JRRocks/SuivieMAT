package com.scanlist.app.ui.folders

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.FolderSummary
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.domain.folders.ActivityMoment
import com.scanlist.app.domain.folders.FolderPeriod
import com.scanlist.app.domain.folders.FolderTimeline
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.ui.common.EmptyState
import com.scanlist.app.ui.common.HeaderCounter
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.common.MenuCard
import com.scanlist.app.ui.common.PrimaryButton
import com.scanlist.app.ui.common.ScanListScaffold
import com.scanlist.app.ui.common.ScanModePicker
import com.scanlist.app.ui.common.SecondaryButton
import com.scanlist.app.ui.common.SectionTitle
import com.scanlist.app.ui.common.icon
import com.scanlist.app.ui.theme.ScanValueTextStyle
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val ITEM_TIME_FORMAT = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)
private val DAY_FORMAT = SimpleDateFormat("dd/MM/yyyy 'à' HH'h'mm", Locale.FRANCE)
private val DAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)
private val FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

/**
 * Liste des dossiers existants, filtrable.
 *
 * Deux usages : l'onglet Dossiers de la barre de navigation, sans fleche de retour, et
 * le choix de la liste de reference d'une comparaison, qui en a une.
 */
@Composable
fun FolderListScreen(
    subtitle: String,
    onFolderSelected: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: FolderListViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hasAnyFolder by viewModel.hasAnyFolder.collectAsStateWithLifecycle()

    ScanListScaffold(
        title = stringResource(R.string.folders_title),
        subtitle = subtitle,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Le champ n'apparait qu'a partir du moment ou la recherche a un sens.
            if (hasAnyFolder) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.folders_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }

            when {
                !hasAnyFolder -> EmptyState(
                    title = stringResource(R.string.folders_empty),
                    hint = stringResource(R.string.folders_empty_hint),
                )

                folders.isEmpty() -> EmptyState(
                    title = stringResource(R.string.folders_no_match),
                )

                else -> {
                    // Fige a chaque changement de la liste : un ecran reste ouvert sur
                    // quelques minutes, pas sur un changement de jour.
                    val now = remember(folders) { System.currentTimeMillis() }
                    val zone = remember { ZoneId.systemDefault() }
                    val groups = remember(folders, now) {
                        FolderTimeline.group(folders, { it.updatedAt }, now, zone)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        groups.forEach { (period, inPeriod) ->
                            item(key = "periode-" + period.name) {
                                SectionTitle(
                                    text = stringResource(period.label),
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 20.dp,
                                        bottom = 8.dp,
                                    ),
                                )
                            }
                            itemsIndexed(
                                inPeriod,
                                key = { _, folder -> folder.id },
                            ) { index, folder ->
                                FolderRow(
                                    folder = folder,
                                    moment = momentLabel(
                                        FolderTimeline.momentOf(folder.updatedAt, now, zone),
                                    ),
                                    isFirst = index == 0,
                                    isLast = index == inPeriod.lastIndex,
                                    onClick = { onFolderSelected(folder.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Une ligne de la liste des dossiers : le mode en pictogramme, le nom, le mode et le
 * moment de la derniere activite, le nombre d'elements a droite. Les lignes d'une meme
 * periode forment une seule carte, arrondie a ses deux bouts.
 */
@Composable
private fun FolderRow(
    folder: FolderSummary,
    moment: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val top = if (isFirst) 20.dp else 0.dp
    val bottom = if (isLast) 20.dp else 0.dp
    val shape = RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
    val isComparison = folder.kind == FolderKind.COMPARISON
    val countDescription = pluralStringResource(
        R.plurals.folder_item_label,
        folder.itemCount,
    ).let { "${folder.itemCount} $it" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isComparison) {
                    Icons.AutoMirrored.Filled.FactCheck
                } else {
                    folder.defaultScanMode.icon
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.folder_row_meta,
                        if (isComparison) {
                            stringResource(R.string.folder_kind_comparison_title)
                        } else {
                            folder.defaultScanMode.label
                        },
                        moment,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                folder.itemCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = countDescription },
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private val FolderPeriod.label: Int
    get() = when (this) {
        FolderPeriod.TODAY -> R.string.folders_period_today
        FolderPeriod.THIS_WEEK -> R.string.folders_period_week
        FolderPeriod.OLDER -> R.string.folders_period_older
    }

@Composable
private fun momentLabel(moment: ActivityMoment): String = when (moment) {
    is ActivityMoment.Time -> stringResource(R.string.folder_when_time, moment.hour, moment.minute)
    ActivityMoment.Yesterday -> stringResource(R.string.folder_when_yesterday)
    is ActivityMoment.Weekday -> moment.day.getDisplayName(TextStyle.FULL, Locale.FRANCE)
    is ActivityMoment.Day -> DAY_MONTH_FORMAT.format(moment.date)
    is ActivityMoment.DayOfOtherYear -> FULL_DATE_FORMAT.format(moment.date)
}

/**
 * Fiche d'un dossier : les trois entrees de la specification, Consulter / Exporter en
 * .csv / Reprendre le scan, puis un apercu des dernieres valeurs.
 *
 * Reprendre le scan est le bouton principal (etape 5 de la refonte, choix de
 * l'utilisateur) : c'est le geste le plus frequent sur un dossier existant. Renommer et
 * supprimer sont relegues au menu de la barre : ce sont des operations de maintenance,
 * pas des etapes du flux de travail decrit dans la specification.
 */
@Composable
fun FolderActionsScreen(
    onBack: () -> Unit,
    onConsult: () -> Unit,
    onExport: () -> Unit,
    onResume: () -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel(),
) {
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val count by viewModel.itemCount.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    ScanListScaffold(
        title = folder?.name ?: stringResource(R.string.folders_title),
        subtitle = folder?.let { current ->
            stringResource(
                R.string.folder_header_details,
                DAY_FORMAT.format(Date(current.createdAt)),
                current.defaultScanMode.label,
            )
        },
        onBack = onBack,
        actions = {
            FolderOverflowMenu(
                onRename = { renaming = true },
                onDelete = { confirmingDelete = true },
            )
        },
        headerTrailing = { ItemCounter(count) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.folder_action_resume),
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Marges reduites : "Exporter en .csv" et son icone doivent tenir dans
                // une demi-largeur de S25 sans etre tronques.
                SecondaryButton(
                    text = stringResource(R.string.folder_action_consult),
                    icon = Icons.Default.Visibility,
                    onClick = onConsult,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.folder_action_export),
                    icon = Icons.Default.IosShare,
                    onClick = onExport,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            if (items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.folder_detail_empty),
                    hint = stringResource(R.string.folder_detail_empty_hint),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(
                        text = stringResource(R.string.folder_recent_values),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    )
                    TextButton(onClick = onConsult) {
                        Text(stringResource(R.string.folder_see_all))
                    }
                }
                RecentValues(items.take(RECENT_VALUES))
            }
        }
    }

    if (renaming) {
        RenameFolderDialog(
            currentName = folder?.name.orEmpty(),
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmingDelete) {
        DeleteFolderDialog(
            folderName = folder?.name.orEmpty(),
            itemCount = count,
            onConfirm = {
                confirmingDelete = false
                // Le dossier disparait : on quitte l'ecran qui le decrivait.
                viewModel.deleteFolder(onBack)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** Nombre de valeurs montrees sur la fiche ; le reste est dans Consulter. */
private const val RECENT_VALUES = 5

/** Apercu des dernieres valeurs lues, la plus recente en tete, en lecture seule. */
@Composable
private fun RecentValues(items: List<ScanItemEntity>) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.value, style = ScanValueTextStyle, modifier = Modifier.weight(1f))
                    Text(
                        ITEM_TIME_FORMAT.format(Date(item.scannedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun FolderOverflowMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.folder_rename)) },
            leadingIcon = {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
            },
            onClick = {
                expanded = false
                onRename()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.folder_delete)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onDelete()
            },
        )
    }
}

@Composable
private fun RenameFolderDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_new_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** La suppression d'un dossier emporte toutes ses lignes : elle est confirmee. */
@Composable
private fun DeleteFolderDialog(
    folderName: String,
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_delete)) },
        text = { Text(stringResource(R.string.folder_delete_confirm, folderName, itemCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Consulter : detail de la liste, avec l'entree "Lancer la fonction Comparer". */
@Composable
fun FolderDetailScreen(
    onBack: () -> Unit,
    onLaunchCompare: (Long) -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel(),
) {
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val lastDeleted by viewModel.lastDeleted.collectAsStateWithLifecycle()

    val clipboard = LocalClipboardManager.current
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.item_copied)
    val deletedMessage = stringResource(R.string.item_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    // Une suppression est definitive en base : l'annulation doit rester a portee de main
    // le temps du message, pas plus.
    LaunchedEffect(lastDeleted) {
        if (lastDeleted != null) {
            val result = snackbarHost.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.consumeDeletion()
            }
        }
    }

    ScanListScaffold(
        title = folder?.name ?: stringResource(R.string.folder_action_consult),
        subtitle = stringResource(R.string.folder_action_consult),
        onBack = onBack,
        headerTrailing = { ItemCounter(items.size) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MenuCard(
                    title = stringResource(R.string.folder_launch_compare),
                    icon = Icons.Default.Compare,
                    onClick = { onLaunchCompare(viewModel.folderId) },
                )
            }

            if (items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.folder_detail_empty),
                    hint = stringResource(R.string.folder_detail_empty_hint),
                )
            } else {
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        ScanItemRow(
                            item = item,
                            onCopy = {
                                clipboard.setText(AnnotatedString(item.value))
                                scope.launch { snackbarHost.showSnackbar(copiedMessage) }
                            },
                            onDelete = { viewModel.deleteItem(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanItemRow(
    item: ScanItemEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Chasse fixe : evite toute confusion O/0 et I/l/1 a la relecture.
            Text(item.value, style = ScanValueTextStyle)
            Text(
                buildString {
                    append(item.mode.label)
                    item.symbology?.let { append(" · ").append(it) }
                    append(" · ").append(ITEM_TIME_FORMAT.format(Date(item.scannedAt)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.item_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Reprendre le scan : sous-options OCR / Code 2D / Code-barres / NFC. */
@Composable
fun FolderResumeModeScreen(
    onBack: () -> Unit,
    onModeSelected: (ScanMode) -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel(),
) {
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val count by viewModel.itemCount.collectAsStateWithLifecycle()

    ScanListScaffold(
        title = stringResource(R.string.folder_action_resume),
        subtitle = folder?.name,
        onBack = onBack,
        headerTrailing = { ItemCounter(count) },
    ) { padding ->
        // Le mode du dernier scan est mis en avant : c'est presque toujours celui
        // qu'on veut reprendre pour completer la meme liste.
        ScanModePicker(
            onModeSelected = onModeSelected,
            highlighted = folder?.defaultScanMode,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

/**
 * Exporter en .csv : les deux sous-entrees de la specification, choix du nom de
 * fichier et preferences d'envoi, puis l'envoi lui-meme.
 */
@Composable
fun FolderExportScreen(
    onBack: () -> Unit,
    onEmailPrefs: () -> Unit,
    detailViewModel: FolderDetailViewModel = hiltViewModel(),
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val folder by detailViewModel.folder.collectAsStateWithLifecycle()
    val count by viewModel.itemCount.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()
    val resolvedName by viewModel.resolvedFileName.collectAsStateWithLifecycle()
    val prefs by viewModel.emailPrefs.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHostState.current
    val errorText = error?.let { stringResource(it) }

    LaunchedEffect(errorText) {
        errorText?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    ScanListScaffold(
        title = stringResource(R.string.export_title),
        subtitle = folder?.name,
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (count == 0) {
                    stringResource(R.string.export_empty)
                } else {
                    stringResource(R.string.export_ready, count)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = fileName,
                onValueChange = viewModel::setFileName,
                label = { Text(stringResource(R.string.export_file_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                supportingText = {
                    // L'assainissement est montre en direct : les caracteres interdits
                    // ne doivent pas se decouvrir une fois le mail parti.
                    Text(stringResource(R.string.export_final_name, resolvedName))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.export_columns),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MenuCard(
                title = stringResource(R.string.export_prefs),
                subtitle = if (prefs.recipient.isBlank()) {
                    stringResource(R.string.export_recipient_none)
                } else {
                    stringResource(R.string.export_recipient, prefs.recipient)
                },
                icon = Icons.Default.IosShare,
                onClick = onEmailPrefs,
            )

            PrimaryButton(
                text = stringResource(R.string.export_send),
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = {
                    viewModel.buildShareIntent { intent ->
                        // Aucune application de messagerie n'est garantie presente :
                        // l'echec doit rester un message, jamais un plantage.
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.reportNoApp()
                        }
                    }
                },
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Nombre d'elements du dossier, dans l'en-tete bleu. */
@Composable
private fun ItemCounter(count: Int) {
    HeaderCounter(
        value = count.toString(),
        label = pluralStringResource(R.plurals.folder_item_label, count),
    )
}
