package com.scanlist.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.theme.compareColors
import kotlinx.coroutines.launch

/**
 * Panneau de reglage de la formule OCR.
 *
 * Il tient sous la camera pendant le reglage : l'utilisateur vise l'etiquette reelle,
 * voit le texte que la camera en tire, ajuste sa formule, et ne demarre la capture
 * qu'une fois l'apercu satisfaisant.
 */
@Composable
fun OcrControlPanel(
    pattern: String,
    validation: RegexRuleEngine.Validation,
    preview: OcrPreview,
    capturing: Boolean,
    groupCount: Int,
    groupIndex: Int,
    onPatternChange: (String) -> Unit,
    onGroupIndexChange: (Int) -> Unit,
    onToggleCapture: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRawText by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val colors = compareColors
    val invalid = validation as? RegexRuleEngine.Validation.Invalid

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pattern,
                onValueChange = onPatternChange,
                label = { Text(stringResource(R.string.ocr_pattern_label)) },
                placeholder = { Text(stringResource(R.string.ocr_pattern_hint)) },
                singleLine = true,
                isError = pattern.isNotEmpty() && invalid != null,
                supportingText = {
                    if (pattern.isNotEmpty() && invalid != null && invalid.message.isNotBlank()) {
                        Text(invalid.message)
                    }
                },
                // Ni majuscule automatique ni correction : une regex n'est pas du texte.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.weight(1f),
            )
            RegexHelpButton(
                onClick = { showHelp = true },
                modifier = Modifier.padding(start = 4.dp),
            )
            IconButton(onClick = onOpenLibrary) {
                Icon(
                    Icons.Default.Bookmarks,
                    contentDescription = stringResource(R.string.ocr_library),
                )
            }
        }

        if (showHelp) {
            RegexHelpSheet(onDismiss = { showHelp = false })
        }

        // Le choix du groupe n'a de sens que si la formule en contient.
        if (groupCount > 0) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ocr_group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (0..groupCount).forEach { index ->
                    FilterChip(
                        selected = groupIndex == index,
                        onClick = { onGroupIndexChange(index) },
                        label = {
                            Text(
                                if (index == 0) stringResource(R.string.ocr_group_full) else "$index",
                            )
                        },
                    )
                }
            }
        }

        Text(
            stringResource(R.string.ocr_preview),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            pattern.isBlank() -> PreviewHint(stringResource(R.string.ocr_no_pattern))
            preview.matches.isEmpty() -> PreviewHint(stringResource(R.string.ocr_no_match))
            else -> Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Meme vert que la fonction Comparer : un seul vocabulaire visuel.
                preview.matches.forEach { match ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(match, fontFamily = FontFamily.Monospace) },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            disabledContainerColor = colors.found,
                            disabledLabelColor = colors.onFound,
                        ),
                    )
                }
            }
        }

        TextButton(
            onClick = { showRawText = !showRawText },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                stringResource(
                    if (showRawText) R.string.ocr_raw_text_hide else R.string.ocr_raw_text_show,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (showRawText) {
            Text(
                preview.rawText.ifBlank { stringResource(R.string.ocr_raw_text_empty) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
            )
        }

        Button(
            onClick = onToggleCapture,
            enabled = validation is RegexRuleEngine.Validation.Valid,
            colors = if (capturing) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (capturing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
            )
            Text(
                stringResource(if (capturing) R.string.ocr_stop else R.string.ocr_start),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Filtre de conformite pour QR Code et code-barres.
 *
 * A la difference de l'OCR, il ne s'agit pas d'isoler un fragment dans du texte
 * libre : le code est deja une valeur delimitee, la formule le valide ou le rejette
 * en totalite. Ni choix de groupe capturant ni armement de la capture : ces deux
 * notions n'ont pas de sens ici, un code decode est deja une valeur complete.
 */
@Composable
fun BarcodeFilterPanel(
    pattern: String,
    validation: RegexRuleEngine.Validation,
    onPatternChange: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val invalid = validation as? RegexRuleEngine.Validation.Invalid

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = pattern,
            onValueChange = onPatternChange,
            label = { Text(stringResource(R.string.barcode_filter_label)) },
            placeholder = { Text(stringResource(R.string.barcode_filter_hint)) },
            singleLine = true,
            isError = pattern.isNotEmpty() && invalid != null,
            supportingText = {
                if (pattern.isNotEmpty() && invalid != null && invalid.message.isNotBlank()) {
                    Text(invalid.message)
                } else if (pattern.isBlank()) {
                    Text(stringResource(R.string.barcode_filter_empty))
                }
            },
            // Ni majuscule automatique ni correction : une regex n'est pas du texte.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenLibrary) {
            Icon(
                Icons.Default.Bookmarks,
                contentDescription = stringResource(R.string.ocr_library),
            )
        }
    }
}

@Composable
private fun PreviewHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Bibliotheque des formules enregistrees.
 *
 * Triee par usage recent : la formule du dernier inventaire est en tete, ce qui est
 * presque toujours celle qu'on veut reprendre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexLibrarySheet(
    rules: List<RegexRuleEntity>,
    currentPattern: String,
    activeRuleId: Long?,
    canSave: Boolean,
    onApply: (RegexRuleEntity) -> Unit,
    onDelete: (RegexRuleEntity) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ocr_library),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                RegexHelpButton(onClick = { showHelp = true })
            }

            if (showHelp) {
                RegexHelpSheet(onDismiss = { showHelp = false })
            }

            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.ocr_library_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.ocr_library_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rules.forEach { rule ->
                    ListItem(
                        headlineContent = { Text(rule.name) },
                        supportingContent = {
                            Text(rule.pattern, fontFamily = FontFamily.Monospace)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (rule.id == activeRuleId) {
                                    Text(
                                        stringResource(R.string.ocr_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { onDelete(rule) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(
                                            R.string.ocr_delete_rule,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onApply(rule)
                                onDismiss()
                            },
                    )
                    HorizontalDivider()
                }
            }

            if (canSave) {
                Text(
                    stringResource(R.string.ocr_save),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    currentPattern,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ocr_save_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onSave(name)
                        name = ""
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ocr_save))
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

/**
 * Petit bouton rond "?" ouvrant l'aide-memoire regex (RegexHelpSheet).
 *
 * Partage entre le champ "Formule regex" et la bibliotheque "Formules enregistrees" :
 * un seul contenu d'aide, deux points d'entree la ou l'utilisateur ecrit ou relit
 * une formule.
 */
@Composable
private fun RegexHelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.size(30.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(
            Icons.Default.QuestionMark,
            contentDescription = stringResource(R.string.regex_help_button),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Aide-memoire regex : la vraie recommandation (faire generer la formule par un LLM,
 * avec un prompt pret a copier) suivie d'un exemple qui montre le resultat reel du
 * moteur regex sur quelques valeurs, plutot qu'une reference syntaxique statique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegexHelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.regex_help_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                stringResource(R.string.regex_help_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            RegexPromptCopyBox()

            RegexHelpExample(
                title = stringResource(R.string.regex_help_example_title),
                description = stringResource(R.string.regex_help_example_desc),
                pattern = stringResource(R.string.regex_help_example_pattern),
            )

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

/**
 * Prompt pret a copier en un clic.
 *
 * Le texte affiche porte des points de suspension visuels ("...") pour montrer que la
 * phrase reste a completer, mais ce ne sont que des points de rendu : le texte copie
 * dans le presse-papiers s'arrete juste apres "pour ", pret a recevoir la liste de
 * valeurs de l'utilisateur sans qu'il ait a les effacer d'abord.
 */
@Composable
private fun RegexPromptCopyBox(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val template = stringResource(R.string.regex_help_prompt_template)
    val copiedMessage = stringResource(R.string.regex_help_prompt_copied)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.regex_help_prompt_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    clipboard.setText(AnnotatedString(template))
                    scope.launch { snackbarHost.showSnackbar(copiedMessage) }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$template...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.regex_help_prompt_label),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Un cas concret : la formule, puis son verdict reel (via [RegexRuleEngine.conforms],
 * le meme moteur que le filtre de conformite) sur une poignee de valeurs proches —
 * bonnes et mauvaises — pour montrer ce qu'une regex rejette et pourquoi.
 */
@Composable
private fun RegexHelpExample(title: String, description: String, pattern: String) {
    val colors = compareColors
    val regex = remember(pattern) { RegexRuleEngine.compileOrNull(pattern) }
    val values = stringArrayResource(R.array.regex_help_result_values)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.regex_help_result_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                values.forEach { value ->
                    val ok = RegexRuleEngine.conforms(value, regex)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (ok) colors.onFound else colors.onMissing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (ok) colors.found else colors.missing,
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
