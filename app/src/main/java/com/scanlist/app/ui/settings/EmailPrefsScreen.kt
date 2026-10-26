package com.scanlist.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scanlist.app.R
import com.scanlist.app.data.prefs.EmailPrefs
import com.scanlist.app.data.prefs.SettingsDataStore
import com.scanlist.app.ui.common.PrimaryButton
import com.scanlist.app.ui.common.ScanListScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmailPrefsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {

    val prefs: StateFlow<EmailPrefs> = settings.emailPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmailPrefs())

    fun save(prefs: EmailPrefs) = viewModelScope.launch {
        settings.setEmailPrefs(prefs)
    }
}

/**
 * Preferences d'envoi de l'export .csv.
 *
 * Destinataire, objet et message pre-remplis dans l'Intent vers le client mail.
 * Aucun identifiant de messagerie n'est demande ni stocke.
 */
@Composable
fun EmailPrefsScreen(
    onBack: () -> Unit,
    viewModel: EmailPrefsViewModel = hiltViewModel(),
) {
    val stored by viewModel.prefs.collectAsStateWithLifecycle()

    var recipient by remember(stored) { mutableStateOf(stored.recipient) }
    var subject by remember(stored) { mutableStateOf(stored.subject) }
    var body by remember(stored) { mutableStateOf(stored.body) }

    ScanListScaffold(
        title = stringResource(R.string.settings_email_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text(stringResource(R.string.settings_email_recipient)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.settings_email_subject)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.settings_email_body)) },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.settings_save),
                onClick = {
                    viewModel.save(EmailPrefs(recipient.trim(), subject, body))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
