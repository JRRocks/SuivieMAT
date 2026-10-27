package com.scanlist.app.ui.scan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.common.PrimaryButton
import com.scanlist.app.ui.theme.PatternTextStyle
import com.scanlist.app.ui.theme.ValueFontFamily
import com.scanlist.app.ui.theme.compareColors
import kotlinx.coroutines.launch

/*
 * Etape 4 de la refonte (variante B, choix de l'utilisateur) : la camera occupe toute la
 * hauteur, la formule se replie en une pastille posee sur l'image et s'edite dans une
 * feuille. La feuille garde le contenu des anciens panneaux : le champ, les outils sous
 * le champ (decision 26) et, en OCR, le choix du groupe capture.
 */

// Commandes posees sur l'image de la camera : fond sombre translucide et texte clair
// quel que soit le theme, l'image derriere est toujours celle du capteur.
private val OverlayScrim = Color(0xC70D1120)
private val OverlayContent = Color.White
private val OverlayContentVariant = Color(0xFFC9D3EE)
private val OverlayError = Color(0xFFFFB4AB)

/**
 * Pastille de la formule, en haut de l'image : la formule active reste visible sans
 * prendre de place a la camera, un appui ouvre son edition.
 */
@Composable
fun PatternPill(
    isOcr: Boolean,
    pattern: String,
    validation: RegexRuleEngine.Validation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val invalid = pattern.isNotEmpty() && validation is RegexRuleEngine.Validation.Invalid
    val editLabel = stringResource(R.string.scan_pattern_edit)

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(OverlayScrim)
            .clickable(onClickLabel = editLabel, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isOcr) Icons.Default.Edit else Icons.Default.FilterAlt,
            contentDescription = null,
            tint = OverlayContent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(
                if (isOcr) R.string.scan_pattern_pill_ocr else R.string.scan_pattern_pill_filter,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = OverlayContent,
        )
        if (pattern.isBlank()) {
            Text(
                stringResource(
                    if (isOcr) {
                        R.string.scan_pattern_none_ocr
                    } else {
                        R.string.scan_pattern_none_filter
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = OverlayContentVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                pattern,
                style = PatternTextStyle,
                color = if (invalid) OverlayError else OverlayContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (invalid) {
                Text(
                    stringResource(R.string.scan_pattern_invalid),
                    style = MaterialTheme.typography.labelLarge,
                    color = OverlayError,
                )
            }
        }
        Text(editLabel, style = MaterialTheme.typography.labelLarge, color = OverlayContentVariant)
    }
}

/**
 * Armement de la capture OCR (decision 12), en bouton flottant au bas de l'image : il
 * reste sous le pouce, la ou l'ancien panneau le placait au milieu de l'ecran.
 *
 * Sans formule valide, il ne reste pas grise (illisible sur l'image sombre) : il ouvre
 * l'edition de la formule, seul geste utile a ce moment-la.
 */
@Composable
fun CaptureToggleButton(
    capturing: Boolean,
    canCapture: Boolean,
    onToggleCapture: () -> Unit,
    onEditPattern: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, icon) = when {
        capturing -> R.string.ocr_stop to Icons.Default.Stop
        canCapture -> R.string.ocr_start to Icons.Default.PlayArrow
        else -> R.string.ocr_enter_pattern to Icons.Default.Edit
    }
    Button(
        onClick = if (capturing || canCapture) onToggleCapture else onEditPattern,
        shape = CircleShape,
        colors = when {
            capturing -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
            canCapture -> ButtonDefaults.buttonColors()
            else -> ButtonDefaults.buttonColors(
                containerColor = OverlayScrim,
                contentColor = OverlayContent,
            )
        },
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
        contentPadding = PaddingValues(horizontal = 28.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(label), modifier = Modifier.padding(start = 10.dp))
    }
}

/**
 * Edition de la formule, ouverte depuis la pastille.
 *
 * En OCR, la formule isole un fragment dans du texte libre, avec le choix du groupe
 * capture. En QR Code et code-barres, c'est un filtre de conformite : le code est deja
 * une valeur delimitee, la formule le valide ou le rejette en totalite, sans groupe ni
 * armement. Chaque frappe s'applique tout de suite, comme avant ; en OCR elle desarme
 * la capture (decision 12).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternEditorSheet(
    isOcr: Boolean,
    pattern: String,
    validation: RegexRuleEngine.Validation,
    groupCount: Int,
    groupIndex: Int,
    onPatternChange: (String) -> Unit,
    onGroupIndexChange: (Int) -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val invalid = validation as? RegexRuleEngine.Validation.Invalid

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = pattern,
                onValueChange = onPatternChange,
                label = {
                    Text(
                        stringResource(
                            if (isOcr) {
                                R.string.ocr_pattern_label
                            } else {
                                R.string.barcode_filter_label
                            },
                        ),
                    )
                },
                placeholder = {
                    Text(
                        stringResource(
                            if (isOcr) R.string.ocr_pattern_hint else R.string.barcode_filter_hint,
                        ),
                    )
                },
                singleLine = true,
                isError = pattern.isNotEmpty() && invalid != null,
                supportingText = {
                    if (pattern.isNotEmpty() && invalid != null && invalid.message.isNotBlank()) {
                        Text(invalid.message)
                    } else if (!isOcr && pattern.isBlank()) {
                        Text(stringResource(R.string.barcode_filter_empty))
                    }
                },
                // Ni majuscule automatique ni correction : une regex n'est pas du texte.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                textStyle = PatternTextStyle,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            // Sans formule, la feuille s'ouvre pour en ecrire une : le clavier vient avec
            // elle. Avec une formule, on vient plutot chercher un outil, le clavier
            // masquerait la moitie de la feuille.
            LaunchedEffect(Unit) {
                if (pattern.isBlank()) focusRequester.requestFocus()
            }

            RegexToolsRow(onPatternChange = onPatternChange, onOpenLibrary = onOpenLibrary)

            // Le choix du groupe n'a de sens que si la formule en contient.
            if (isOcr && groupCount > 0) {
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
                                    if (index == 0) {
                                        stringResource(R.string.ocr_group_full)
                                    } else {
                                        "$index"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            PrimaryButton(
                text = stringResource(R.string.action_confirm),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Les trois outils de formule, sous le champ et non plus a cote : a cote, chaque
 * icone retrecissait le champ, et une icone sans libelle ne dit pas ce qu'elle fait.
 * Partages par l'OCR et le filtre de conformite, qui offrent donc les memes outils.
 */
@Composable
private fun RegexToolsRow(onPatternChange: (String) -> Unit, onOpenLibrary: () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RegexToolButton(
            icon = Icons.Default.Info,
            label = R.string.regex_tool_info,
            onClick = { showHelp = true },
            modifier = Modifier.weight(1f),
        )
        RegexToolButton(
            icon = Icons.Default.AutoFixHigh,
            label = R.string.regex_tool_generator,
            onClick = { showGenerator = true },
            modifier = Modifier.weight(1f),
        )
        RegexToolButton(
            icon = Icons.Default.Bookmarks,
            label = R.string.regex_tool_library,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
    }

    if (showHelp) {
        RegexHelpSheet(onDismiss = { showHelp = false })
    }

    if (showGenerator) {
        RegexGeneratorSheet(
            onApply = onPatternChange,
            onDismiss = { showGenerator = false },
        )
    }
}

@Composable
private fun RegexToolButton(
    icon: ImageVector,
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        // Marges reduites au minimum : sur le S25 (environ 384 dp de large), chaque
        // bouton dispose d'environ 112 dp, et "Regex Auto" en occupe environ 74 a lui
        // seul. Avec les 24 dp de marge par defaut, le libelle serait tronque.
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        // labelMedium et non labelLarge (16 sp) : a 16 sp, "Regex Auto" et son icone
        // rempliraient tout juste les 112 dp du bouton, au risque d'etre tronques.
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
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
                            Text(rule.pattern, fontFamily = ValueFontFamily)
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
                    fontFamily = ValueFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ocr_save_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.ocr_save),
                    onClick = {
                        onSave(name)
                        name = ""
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                title = stringResource(
                    R.string.regex_help_example_title,
                    stringResource(R.string.regex_help_example_desc),
                ),
                pattern = stringResource(
                    R.string.regex_help_example_pattern_label,
                    stringResource(R.string.regex_help_example_pattern),
                ),
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
private fun RegexHelpExample(title: String, pattern: String) {
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
                    pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ValueFontFamily,
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
                        fontFamily = ValueFontFamily,
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
