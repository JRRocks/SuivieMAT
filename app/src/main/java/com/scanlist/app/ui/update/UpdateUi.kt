package com.scanlist.app.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanlist.app.R
import com.scanlist.app.ui.common.LocalSnackbarHostState

/**
 * Fenetre de mise a jour, posee une fois a la racine de la navigation.
 *
 * [promptAllowed] est faux hors des trois onglets : une mise a jour trouvee par la
 * recherche automatique pendant un scan attend le retour a un onglet. Rien ne doit
 * venir se poser sur la camera au milieu d'un inventaire.
 */
@Composable
fun UpdateHost(viewModel: UpdateViewModel, promptAllowed: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current

    // Le texte est resolu en composition : stringResource n'est pas appelable depuis
    // l'effet lui-meme.
    val noticeText = notice?.let { stringResource(it.message, it.version) }
    LaunchedEffect(noticeText) {
        noticeText?.let {
            // Consomme apres l'affichage, pas avant : vider le message relancerait cet
            // effet et couperait le bandeau a peine apparu.
            snackbarHostState.showSnackbar(it)
            viewModel.consumeNotice()
        }
    }

    // L'installateur d'Android s'ouvre des la fin du telechargement : un seul appui,
    // « Installer », mene jusqu'a sa demande de confirmation.
    val launchInstaller = (state as? UpdateUiState.Ready)?.launchInstaller == true
    LaunchedEffect(launchInstaller) {
        if (launchInstaller) openInstaller(context, viewModel)
    }

    when (val current = state) {
        UpdateUiState.Idle -> Unit

        is UpdateUiState.Available -> if (promptAllowed) {
            UpdateDialog(
                title = stringResource(R.string.update_available_title),
                text = if (current.megabytes != null) {
                    stringResource(
                        R.string.update_available_text,
                        current.version,
                        current.megabytes,
                        viewModel.installedVersion,
                    )
                } else {
                    stringResource(R.string.update_available_text_no_size, current.version, viewModel.installedVersion)
                },
                confirmLabel = stringResource(R.string.update_action_install),
                onConfirm = viewModel::download,
                dismissLabel = stringResource(R.string.update_action_later),
                onDismiss = viewModel::dismiss,
            )
        }

        is UpdateUiState.Downloading -> UpdateDialog(
            title = stringResource(R.string.update_downloading_title, current.version),
            // Un appui a cote ne doit pas interrompre un telechargement de 20 Mo.
            dismissOnOutsideTap = false,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::cancelDownload,
        ) {
            if (current.percent != null) {
                LinearProgressIndicator(
                    progress = { current.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.update_downloading_percent, current.percent),
                    modifier = Modifier.align(Alignment.End),
                )
            } else {
                Text(stringResource(R.string.update_downloading_unknown))
            }
        }

        is UpdateUiState.Ready -> UpdateDialog(
            title = stringResource(R.string.update_ready_title, current.version),
            text = stringResource(R.string.update_ready_text),
            confirmLabel = stringResource(R.string.update_action_install),
            onConfirm = { openInstaller(context, viewModel) },
            dismissLabel = stringResource(R.string.update_action_later),
            onDismiss = viewModel::dismiss,
        )

        is UpdateUiState.Failed -> UpdateDialog(
            title = stringResource(R.string.update_failed_title),
            text = stringResource(current.message),
            confirmLabel = stringResource(R.string.update_action_retry),
            onConfirm = viewModel::download,
            dismissLabel = stringResource(R.string.action_close),
            onDismiss = viewModel::dismiss,
        )
    }
}

private fun openInstaller(context: Context, viewModel: UpdateViewModel) {
    val intent = viewModel.installIntent()
    if (intent == null) {
        viewModel.onInstallerUnavailable()
        return
    }
    try {
        context.startActivity(intent)
        viewModel.onInstallerLaunched()
    } catch (e: ActivityNotFoundException) {
        viewModel.onInstallerUnavailable()
    }
}

/**
 * Boite de dialogue de la mise a jour : celle de Material, sans animation de fenetre
 * (decision 32, tout est instantane).
 */
@Composable
private fun UpdateDialog(
    title: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    text: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    dismissOnOutsideTap: Boolean = true,
    content: @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutsideTap),
        title = {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setWindowAnimations(0) }
            Text(title)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (text != null) Text(text)
                content?.invoke(this)
            }
        },
        confirmButton = {
            if (confirmLabel != null) {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/**
 * Pied de l'onglet Nouveau scan : la version installee, et la recherche de mise a jour
 * a la demande. Discret par construction : ce n'est pas un geste de tous les jours, il
 * ne doit pas disputer la place aux modes de scan.
 */
@Composable
fun AppVersionFooter(
    version: String,
    checking: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.update_installed_version, stringResource(R.string.app_name), version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCheck, enabled = !checking) {
            Text(
                stringResource(if (checking) R.string.update_checking else R.string.update_check_now),
            )
        }
    }
}
