package com.scanlist.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.scanlist.app.R

/**
 * Les trois branches de la specification, devenues onglets (decision 28).
 *
 * L'ordre est celui du menu principal qu'elles remplacent ; Nouveau scan, ecran
 * d'ouverture, tombe sous le pouce droit.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    FOLDERS(Routes.FOLDERS, R.string.menu_folders, Icons.Default.Folder),
    COMPARE(Routes.COMPARE_SOURCE, R.string.menu_compare, Icons.AutoMirrored.Filled.FactCheck),
    NEW_SCAN(Routes.NEW_SCAN_MODE, R.string.menu_new_scan, Icons.Default.QrCodeScanner),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * Barre de navigation du bas, affichee sur les trois ecrans racines seulement.
 *
 * Les ecrans plus profonds ont leur fleche de retour, et l'ecran de scan a besoin de
 * toute la hauteur pour la camera.
 */
@Composable
fun MainNavigationBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
