package com.scanlist.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanlist.app.R
import com.scanlist.app.domain.model.ScanMode

/**
 * Etat des snackbars, partage par tout le graphe de navigation.
 *
 * Fourni une seule fois a la racine : chaque ecran l'affiche via [ScanListScaffold],
 * ce qui permet a un message emis pendant une navigation d'atteindre l'ecran d'arrivee.
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/** En-tete commun a tous les ecrans, avec fleche de retour optionnelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanListScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}

/**
 * Entree de menu : titre, sous-titre et chevron.
 * Brique de base de toute la hierarchie de navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Selecteur de mode de scan.
 *
 * Reutilise par "Nouveau scan", "Reprendre le scan" et "Lancer le scan" d'une
 * comparaison : les trois sous-menus de la specification pointent sur ce composant,
 * ce qui garantit qu'ils proposent exactement les memes options.
 */
@Composable
fun ScanModePicker(
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: ScanMode? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MenuCard(
            title = stringResource(R.string.mode_ocr),
            subtitle = stringResource(R.string.mode_ocr_desc),
            icon = Icons.Default.TextFields,
            trailing = if (highlighted == ScanMode.OCR) "•" else null,
            onClick = { onModeSelected(ScanMode.OCR) },
        )
        MenuCard(
            title = stringResource(R.string.mode_qr),
            subtitle = stringResource(R.string.mode_qr_desc),
            icon = Icons.Default.QrCode,
            trailing = if (highlighted == ScanMode.QR_CODE) "•" else null,
            onClick = { onModeSelected(ScanMode.QR_CODE) },
        )
        MenuCard(
            title = stringResource(R.string.mode_barcode),
            subtitle = stringResource(R.string.mode_barcode_desc),
            icon = Icons.Default.QrCodeScanner,
            trailing = if (highlighted == ScanMode.BARCODE) "•" else null,
            onClick = { onModeSelected(ScanMode.BARCODE) },
        )
        MenuCard(
            title = stringResource(R.string.mode_nfc),
            subtitle = stringResource(R.string.mode_nfc_desc),
            icon = Icons.Default.Nfc,
            trailing = if (highlighted == ScanMode.NFC) "•" else null,
            onClick = { onModeSelected(ScanMode.NFC) },
        )
    }
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Bandeau des ecrans dont la logique arrive dans une phase ulterieure. */
@Composable
fun PhaseNotice(text: String = stringResource(R.string.wip_notice)) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
