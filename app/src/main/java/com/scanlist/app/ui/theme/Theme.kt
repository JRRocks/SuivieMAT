package com.scanlist.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.scanlist.app.R

/*
 * Style "Bleu de travail", choisi par l'utilisateur le 2026-10-26 (decision 27 de
 * consignes/contexte.md) : l'en-tete de chaque ecran est teint du bleu des tenues de
 * travail, le contenu reste clair, les caracteres sont grands pour une lecture a bout
 * de bras et une manipulation au pouce, meme gante.
 */

private val WorkBlue = Color(0xFF22386F)
private val WorkBlueNight = Color(0xFF1C2C5A)
private val Ink = Color(0xFF121A2E)
private val InkSoft = Color(0xFF4A5470)
private val Mist = Color(0xFFE6EAF4)

private val LightColors = lightColorScheme(
    primary = WorkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E7F5),
    onPrimaryContainer = Color(0xFF1A2B57),
    secondary = Color(0xFF3E5596),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E7F5),
    onSecondaryContainer = Color(0xFF1A2B57),
    background = Color(0xFFF2F4F9),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1F8),
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFF2F4F9),
    surfaceContainerHigh = Color(0xFFECEFF6),
    surfaceContainerHighest = Color(0xFFE6EAF3),
    outline = Color(0xFF737D97),
    outlineVariant = Color(0xFFE1E5EE),
    // Surfaces inverses : ce sont elles qui habillent les snackbars. Sans ces
    // valeurs, Material retombe sur son violet par defaut, hors charte.
    inverseSurface = Ink,
    inverseOnSurface = Mist,
    inversePrimary = Color(0xFFAFC2F5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC2F5),
    onPrimary = Color(0xFF10224D),
    primaryContainer = Color(0xFF2B3F75),
    onPrimaryContainer = Color(0xFFDCE3F8),
    secondary = Color(0xFFBCC8EC),
    onSecondary = Color(0xFF1A2B57),
    secondaryContainer = Color(0xFF2B3F75),
    onSecondaryContainer = Color(0xFFDCE3F8),
    background = Color(0xFF0F1422),
    onBackground = Mist,
    surface = Color(0xFF171D2E),
    onSurface = Mist,
    surfaceVariant = Color(0xFF232B40),
    onSurfaceVariant = Color(0xFFA9B3CC),
    surfaceContainerLowest = Color(0xFF0B1020),
    surfaceContainerLow = Color(0xFF141A2A),
    surfaceContainer = Color(0xFF171D2E),
    surfaceContainerHigh = Color(0xFF1E2537),
    surfaceContainerHighest = Color(0xFF252D42),
    outline = Color(0xFF8790A8),
    outlineVariant = Color(0xFF2C3550),
    inverseSurface = Mist,
    inverseOnSurface = Ink,
    inversePrimary = WorkBlue,
)

/**
 * Couleurs de l'en-tete bleu, commun a tous les ecrans.
 *
 * Hors du schema Material : le bleu de l'en-tete reste fonce en theme sombre, alors que
 * le `primary` sombre s'eclaircit pour rester lisible sur fond nuit. Les confondre
 * donnerait un en-tete bleu pale en theme sombre.
 */
data class ShellColors(
    val container: Color,
    val content: Color,
    val contentVariant: Color,
)

private val LightShellColors = ShellColors(
    container = WorkBlue,
    content = Color.White,
    contentVariant = Color(0xFFC9D3EE),
)

private val DarkShellColors = ShellColors(
    container = WorkBlueNight,
    content = Color.White,
    contentVariant = Color(0xFFB9C5E6),
)

val LocalShellColors = staticCompositionLocalOf { LightShellColors }

/**
 * Couleurs semantiques de la fonction Comparer.
 *
 * Code impose par l'utilisateur, a ne pas modifier sans validation : la liste de
 * reference nait en **rouge** (presence a valider) et passe au **vert** a la lecture.
 * Ce sont les deux seuls etats.
 *
 * [neutral] ne porte aucun etat : c'est le fond des bandeaux passagers — doublon,
 * message, valeur lue hors de la liste de reference. Il ne doit jamais servir a
 * qualifier une entree de la liste.
 *
 * [progress] est le vert franc de la barre d'avancement : pose sur l'en-tete bleu, le
 * vert fonce du texte ne s'en detacherait pas.
 */
data class CompareColors(
    val found: Color,
    val onFound: Color,
    val missing: Color,
    val onMissing: Color,
    val neutral: Color,
    val onNeutral: Color,
    val progress: Color,
)

private val LightCompareColors = CompareColors(
    found = Color(0xFFD9F0DF),
    onFound = Color(0xFF1A5A2E),
    missing = Color(0xFFFBDDDD),
    onMissing = Color(0xFF8A1C1C),
    neutral = Color(0xFFE3E7EF),
    onNeutral = Color(0xFF3F4A63),
    progress = Color(0xFF3FA35E),
)

private val DarkCompareColors = CompareColors(
    found = Color(0xFF1E4426),
    onFound = Color(0xFF9BE0AA),
    missing = Color(0xFF4E1A1E),
    onMissing = Color(0xFFFFA3A3),
    neutral = Color(0xFF2A3246),
    onNeutral = Color(0xFFB9C3DA),
    progress = Color(0xFF5CC27D),
)

val LocalCompareColors = staticCompositionLocalOf { LightCompareColors }

/*
 * Polices embarquees (res/font, licence OFL dans assets/licences) : l'application est
 * hors-ligne, une police telechargeable a la volee n'y aurait pas sa place. Fichiers
 * variables : la graisse declaree ne fait que choisir l'entree de la famille, c'est
 * variationSettings qui regle l'axe "wght" du fichier. Sans lui, Android dessine
 * l'instance par defaut du fichier, la plus fine (ExtraLight), quelle que soit la
 * graisse demandee : tous les titres sortaient en trait fin (corrige en 1.9).
 */
// FontVariation.weight est encore marque experimental en Compose 1.7, mais c'est le seul
// moyen de regler la graisse d'un fichier variable lu depuis les ressources.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val SourceSans3 = FontFamily(
    variable(R.font.source_sans_3, FontWeight.Normal),
    variable(R.font.source_sans_3, FontWeight.Medium),
    variable(R.font.source_sans_3, FontWeight.SemiBold),
    variable(R.font.source_sans_3, FontWeight.Bold),
)

/** Chasse fixe des valeurs et des formules, la meme dans tous les ecrans. */
val ValueFontFamily = FontFamily(
    variable(R.font.source_code_pro, FontWeight.Medium),
    variable(R.font.source_code_pro, FontWeight.SemiBold),
)

private fun sans(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = SourceSans3,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/*
 * Echelle plus grande que celle de Material : l'ecran se lit tenu a bout de bras, sous
 * les neons d'un hangar. bodySmall passe ainsi de 12 a 14 sp, ce qui suffit a rendre
 * lisibles toutes les lignes secondaires (horodatage, symbologie) sans retoucher les
 * ecrans un par un.
 */
private val ScanListTypography = Typography(
    displaySmall = sans(FontWeight.Bold, 36, 44),
    headlineLarge = sans(FontWeight.Bold, 34, 40),
    headlineMedium = sans(FontWeight.Bold, 30, 36),
    headlineSmall = sans(FontWeight.Bold, 25, 30),
    titleLarge = sans(FontWeight.Bold, 22, 28),
    titleMedium = sans(FontWeight.SemiBold, 18, 24),
    titleSmall = sans(FontWeight.Bold, 15, 20),
    bodyLarge = sans(FontWeight.Normal, 17, 23),
    bodyMedium = sans(FontWeight.Normal, 15, 21),
    bodySmall = sans(FontWeight.Normal, 14, 19),
    labelLarge = sans(FontWeight.SemiBold, 16, 20),
    labelMedium = sans(FontWeight.SemiBold, 14, 18),
    labelSmall = sans(FontWeight.SemiBold, 12, 16),
)

private val ScanListShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Style reserve aux valeurs scannees.
 *
 * Chasse fixe pour eviter toute confusion O/0 et I/l/1 a la relecture sur le terrain.
 * Volontairement **pas** applique au `bodyLarge` du theme : les champs de saisie de
 * Material 3 s'en servent par defaut, et une barre de recherche en chasse fixe fait
 * bricolage. Le monospace se demande explicitement, la ou il sert vraiment.
 */
val ScanValueTextStyle = TextStyle(
    fontFamily = ValueFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 26.sp,
)

/** Saisie d'une formule regex : meme chasse fixe que les valeurs, sans la graisse. */
val PatternTextStyle = TextStyle(
    fontFamily = ValueFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    lineHeight = 24.sp,
)

@Composable
fun ScanListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val compareColors = if (darkTheme) DarkCompareColors else LightCompareColors
    val shellColors = if (darkTheme) DarkShellColors else LightShellColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            // L'en-tete bleu passe sous la barre d'etat dans les deux themes : ses
            // icones restent toujours claires.
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalCompareColors provides compareColors,
        LocalShellColors provides shellColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScanListTypography,
            shapes = ScanListShapes,
            content = content,
        )
    }
}

/** Raccourci de lecture des couleurs de comparaison depuis un composable. */
val compareColors: CompareColors
    @Composable get() = LocalCompareColors.current

/** Raccourci de lecture des couleurs de l'en-tete depuis un composable. */
val shellColors: ShellColors
    @Composable get() = LocalShellColors.current
