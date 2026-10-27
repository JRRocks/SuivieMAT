package com.scanlist.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.ui.theme.shellColors

/**
 * Etat des snackbars, partage par tout le graphe de navigation.
 *
 * Fourni une seule fois a la racine : chaque ecran l'affiche via [ScanListScaffold],
 * ce qui permet a un message emis pendant une navigation d'atteindre l'ecran d'arrivee.
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Cadre commun a tous les ecrans : l'en-tete bleu, puis le contenu.
 *
 * [headerTrailing] place un compteur a droite du titre, [headerBottom] une bande sous
 * le titre (les compteurs de comparaison) : c'est la signature du style, le chiffre
 * qui compte vit dans l'en-tete et reste visible quoi qu'il arrive en dessous.
 *
 * [bottomBar] fixe une action sous le pouce ; passee au Scaffold plutot que posee dans
 * le contenu, pour que les messages s'affichent au-dessus d'elle et non par-dessus.
 */
@Composable
fun ScanListScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    headerBottom: (@Composable ColumnScope.() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        bottomBar = bottomBar,
        topBar = {
            ShellHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                actions = actions,
                trailing = headerTrailing,
                bottom = headerBottom,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
private fun ShellHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    bottom: (@Composable ColumnScope.() -> Unit)?,
) {
    val shell = shellColors
    val hasIconRow = onBack != null || actions != null

    Surface(color = shell.container, contentColor = shell.content) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Le bleu passe sous la barre d'etat (edge-to-edge) ; seul le contenu
                // s'en ecarte.
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            if (hasIconRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    actions?.invoke(this)
                }
            } else {
                Spacer(Modifier.height(28.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        // Ecran racine sans fleche de retour : le titre est le nom de
                        // l'application, il prend la taille du dessus.
                        style = if (hasIconRow) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = shell.contentVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
            }

            if (bottom != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = bottom,
                )
            }
        }
    }
}

/**
 * Compteur pose a droite du titre de l'en-tete : un grand chiffre, son unite dessous.
 */
@Composable
fun HeaderCounter(value: String, label: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = shellColors.contentVariant,
        )
    }
}

/**
 * Entree de menu : icone, titre et sous-titre sur une carte blanche.
 * Brique de base de toute la hierarchie de navigation.
 *
 * [emphasized] teint la carte du bleu de l'en-tete : reserve a l'entree que
 * l'utilisateur prend le plus souvent (Nouveau scan, dernier mode utilise).
 */
@Composable
fun MenuCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val shell = shellColors
    val container = if (emphasized) shell.container else MaterialTheme.colorScheme.surface
    val content = if (emphasized) shell.content else MaterialTheme.colorScheme.onSurface
    val secondary = if (emphasized) {
        shell.contentVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent = if (emphasized) shell.content else MaterialTheme.colorScheme.primary

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emphasized) 3.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondary,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    trailing,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
            }
        }
    }
}

/**
 * Bouton d'action principal : pleine largeur, 56 dp, coins de 16 dp. Un seul par ecran.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

/**
 * Bouton secondaire, teinte claire du bleu : pour les actions qui accompagnent
 * l'action principale sans lui disputer l'attention.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = contentPadding,
        modifier = modifier.heightIn(min = 50.dp),
    ) {
        ButtonContent(text = text, icon = icon)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Intitule de section, au-dessus d'une liste ou d'un groupe de cartes. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Etat vide d'une liste. */
@Composable
fun EmptyState(title: String, hint: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
