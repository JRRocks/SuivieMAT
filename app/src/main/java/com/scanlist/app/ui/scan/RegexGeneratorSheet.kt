package com.scanlist.app.ui.scan

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.domain.regex.RegexSuggester
import com.scanlist.app.domain.regex.RegexSuggester.BlockType
import com.scanlist.app.domain.regex.RegexSuggester.Kind
import com.scanlist.app.ui.common.PrimaryButton
import com.scanlist.app.ui.theme.PatternTextStyle
import com.scanlist.app.ui.theme.ValueFontFamily
import com.scanlist.app.ui.theme.compareColors

/**
 * Generateur de formules a partir d'exemples ([RegexSuggester], sans IA).
 *
 * Le calcul est instantane : il est refait a chaque frappe, sans ViewModel ni
 * coroutine. On coche une proposition, son detail se deplie, puis « Utiliser cette
 * formule » l'applique (etape 7 de la refonte, variante C). La formule choisie suit le
 * meme chemin qu'une formule tapee a la main ([onApply] = `setPattern`) : en OCR, elle
 * desarme donc la capture comme toute modification (decision 12).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexGeneratorSheet(onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val close = onDismiss
    var input by remember { mutableStateOf("") }
    val result = remember(input) { RegexSuggester.suggest(input) }
    // Une nouvelle saisie change les propositions : la plus stricte redevient le choix.
    var selected by remember(result) { mutableIntStateOf(0) }

    InstantModalSheet(onDismiss) {
        SheetHeader(title = stringResource(R.string.regex_generator_title), onClose = close)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Le mode d'emploi s'efface des la premiere frappe : les propositions ont
            // besoin de la place.
            if (result is RegexSuggester.Result.Empty) {
                Text(
                    stringResource(R.string.regex_generator_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.regex_generator_input_label)) },
                placeholder = { Text(stringResource(R.string.regex_generator_input_hint)) },
                supportingText = { Text(stringResource(R.string.regex_generator_input_support)) },
                singleLine = true,
                // Ni majuscule automatique ni correction : la casse de l'exemple fait
                // partie du format detecte.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                textStyle = PatternTextStyle,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            when (result) {
                RegexSuggester.Result.Empty -> Unit

                RegexSuggester.Result.Incompatible -> Text(
                    stringResource(R.string.regex_generator_incompatible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is RegexSuggester.Result.Found -> {
                    Text(
                        stringResource(
                            R.string.regex_generator_detected,
                            formatDescription(result.blocks),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceList {
                        result.suggestions.forEachIndexed { index, suggestion ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            SuggestionRow(
                                suggestion = suggestion,
                                selected = index == selected,
                                onSelect = { selected = index },
                            )
                        }
                    }
                }
            }
        }

        if (result is RegexSuggester.Result.Found) {
            SheetActionBar {
                PrimaryButton(
                    text = stringResource(R.string.regex_generator_use),
                    icon = Icons.Default.Check,
                    onClick = {
                        result.suggestions.getOrNull(selected)?.let { onApply(it.pattern) }
                        close()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Une proposition : son nom et la formule ; une fois cochee, ce qu'elle fait et une
 * valeur concrete qu'elle accepte ou refuse en plus de la formule stricte. Meme vert et
 * meme rouge que l'aide-memoire, eux-memes repris de la fonction Comparer.
 */
@Composable
private fun SuggestionRow(
    suggestion: RegexSuggester.Suggestion,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ChoiceRow(selected = selected, onSelect = onSelect) {
        Text(
            stringResource(titleOf(suggestion.kind)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            suggestion.pattern,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ValueFontFamily,
            color = MaterialTheme.colorScheme.primary,
        )
        if (selected) {
            Text(
                commentOf(suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            suggestion.alsoAccepts?.let {
                IllustrationLine(
                    stringResource(R.string.regex_generator_also_accepts, it),
                    accepted = true,
                )
            }
            suggestion.rejects?.let {
                IllustrationLine(stringResource(R.string.regex_generator_rejects, it), accepted = false)
            }
        }
    }
}

@Composable
private fun IllustrationLine(text: String, accepted: Boolean) {
    val colors = compareColors
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = ValueFontFamily,
        color = if (accepted) colors.onFound else colors.onMissing,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (accepted) colors.found else colors.missing,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@StringRes
private fun titleOf(kind: Kind): Int = when (kind) {
    Kind.STRICT -> R.string.regex_generator_strict_title
    Kind.ANY_CASE -> R.string.regex_generator_any_case_title
    Kind.SEPARATORS -> R.string.regex_generator_separators_title
    Kind.FIXED_PREFIX -> R.string.regex_generator_fixed_prefix_title
    Kind.FLEXIBLE -> R.string.regex_generator_flexible_title
}

@Composable
private fun commentOf(suggestion: RegexSuggester.Suggestion): String = when (suggestion.kind) {
    Kind.STRICT -> stringResource(R.string.regex_generator_strict_comment)
    Kind.ANY_CASE -> stringResource(R.string.regex_generator_any_case_comment)
    Kind.SEPARATORS -> stringResource(R.string.regex_generator_separators_comment)
    Kind.FIXED_PREFIX -> stringResource(
        R.string.regex_generator_fixed_prefix_comment,
        suggestion.fixedPrefix.orEmpty(),
    )
    Kind.FLEXIBLE -> stringResource(R.string.regex_generator_flexible_comment)
}

@Composable
private fun formatDescription(blocks: List<RegexSuggester.Block>): String =
    blocks.map { blockLabel(it) }
        .joinToString(stringResource(R.string.regex_generator_block_joiner))

@Composable
private fun blockLabel(block: RegexSuggester.Block): String = when {
    block.type == BlockType.LITERAL && block.text == " " ->
        stringResource(R.string.regex_generator_block_space)

    block.type == BlockType.LITERAL ->
        stringResource(R.string.regex_generator_block_literal, block.text)

    block.minLength == block.maxLength ->
        pluralStringResource(pluralOf(block.type), block.minLength, block.minLength)

    else -> stringResource(rangeOf(block.type), block.minLength, block.maxLength)
}

@PluralsRes
private fun pluralOf(type: BlockType): Int = when (type) {
    BlockType.DIGIT -> R.plurals.regex_generator_block_digit
    BlockType.UPPER -> R.plurals.regex_generator_block_upper
    BlockType.LOWER -> R.plurals.regex_generator_block_lower
    BlockType.LETTER, BlockType.LITERAL -> R.plurals.regex_generator_block_letter
}

@StringRes
private fun rangeOf(type: BlockType): Int = when (type) {
    BlockType.DIGIT -> R.string.regex_generator_block_digit_range
    BlockType.UPPER -> R.string.regex_generator_block_upper_range
    BlockType.LOWER -> R.string.regex_generator_block_lower_range
    BlockType.LETTER, BlockType.LITERAL -> R.string.regex_generator_block_letter_range
}
