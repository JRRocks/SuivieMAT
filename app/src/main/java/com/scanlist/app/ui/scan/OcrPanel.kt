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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.ui.theme.compareColors

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
            IconButton(onClick = onOpenLibrary) {
                Icon(
                    Icons.Default.Bookmarks,
                    contentDescription = stringResource(R.string.ocr_library),
                )
            }
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.ocr_library),
                style = MaterialTheme.typography.titleMedium,
            )

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
