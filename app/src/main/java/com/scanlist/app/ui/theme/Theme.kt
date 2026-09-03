package com.scanlist.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Teal = Color(0xFF00897B)
private val TealDark = Color(0xFF4DB6AC)
private val Ink = Color(0xFF0F1A24)
private val Slate = Color(0xFF1B2733)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Color(0xFF00695C),
    background = Color(0xFFF6F8FA),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE3E8ED),
    onSurfaceVariant = Color(0xFF44525F),
    // Surfaces inverses : ce sont elles qui habillent les snackbars. Sans ces
    // valeurs, Material retombe sur son violet par defaut, hors charte.
    inverseSurface = Ink,
    inverseOnSurface = Color(0xFFE6EDF3),
    inversePrimary = Color(0xFF80CBC4),
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Ink,
    secondary = Color(0xFF80CBC4),
    background = Ink,
    onBackground = Color(0xFFE6EDF3),
    surface = Slate,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF27333F),
    onSurfaceVariant = Color(0xFFA9B7C3),
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Ink,
    inversePrimary = Color(0xFF00695C),
)

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
 */
data class CompareColors(
    val found: Color,
    val onFound: Color,
    val missing: Color,
    val onMissing: Color,
    val neutral: Color,
    val onNeutral: Color,
)

private val LightCompareColors = CompareColors(
    found = Color(0xFFC8E6C9),
    onFound = Color(0xFF1B5E20),
    missing = Color(0xFFFFCDD2),
    onMissing = Color(0xFFB71C1C),
    neutral = Color(0xFFDDE3E8),
    onNeutral = Color(0xFF4B5A66),
)

private val DarkCompareColors = CompareColors(
    found = Color(0xFF1E4620),
    onFound = Color(0xFF9BE29E),
    missing = Color(0xFF4E1417),
    onMissing = Color(0xFFFF9A9A),
    neutral = Color(0xFF2C3742),
    onNeutral = Color(0xFFA9B7C3),
)

val LocalCompareColors = staticCompositionLocalOf { LightCompareColors }

private val ScanListTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
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
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
)

@Composable
fun ScanListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val compareColors = if (darkTheme) DarkCompareColors else LightCompareColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalCompareColors provides compareColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScanListTypography,
            content = content,
        )
    }
}

/** Raccourci de lecture des couleurs de comparaison depuis un composable. */
val compareColors: CompareColors
    @Composable get() = LocalCompareColors.current
