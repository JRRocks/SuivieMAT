package com.scanlist.app.ui.folders

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.R
import com.scanlist.app.data.csv.ScanCsvBuilder
import com.scanlist.app.data.prefs.EmailPrefs
import com.scanlist.app.data.prefs.SettingsDataStore
import com.scanlist.app.data.repository.FolderRepository
import com.scanlist.app.data.repository.ScanRepository
import com.scanlist.app.export.ExportFileName
import com.scanlist.app.export.ExportManager
import com.scanlist.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val scanRepository: ScanRepository,
    private val settings: SettingsDataStore,
    private val exportManager: ExportManager,
) : ViewModel() {

    private val folderId: Long = savedStateHandle.get<Long>(Routes.ARG_FOLDER_ID) ?: Routes.NO_ID

    private val fileNameState = MutableStateFlow("")
    val fileName: StateFlow<String> = fileNameState.asStateFlow()

    val itemCount: StateFlow<Int> = scanRepository.observeCount(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val errorState = MutableStateFlow<Int?>(null)

    /** Message d'erreur en attente d'affichage, sous forme de ressource. */
    val error: StateFlow<Int?> = errorState.asStateFlow()

    val emailPrefs: StateFlow<EmailPrefs> = settings.emailPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmailPrefs())

    /**
     * Nom reellement ecrit sur le disque, apres assainissement.
     * Affiche sous le champ : l'utilisateur voit tout de suite ce que deviennent les
     * caracteres interdits, plutot que de le decouvrir en piece jointe.
     */
    val resolvedFileName: StateFlow<String> = fileNameState
        .map(ExportFileName::sanitize)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ExportFileName.FALLBACK,
        )

    init {
        viewModelScope.launch {
            val folder = folderRepository.getById(folderId) ?: return@launch
            fileNameState.value = exportManager.suggestFileName(folder.name)
                .removeSuffix(ExportFileName.EXTENSION)
        }
    }

    fun setFileName(value: String) {
        fileNameState.value = value
    }

    fun consumeError() {
        errorState.value = null
    }

    /**
     * Génère le fichier puis remonte l'intention d'envoi.
     *
     * L'écriture part sur [Dispatchers.IO] : un inventaire de plusieurs milliers de
     * lignes ne doit pas figer l'interface au moment d'appuyer sur « Envoyer ».
     * L'appelant se charge du `startActivity`, seul l'écran connaît son contexte.
     */
    fun buildShareIntent(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val items = scanRepository.getItems(folderId)
        if (items.isEmpty()) {
            errorState.value = R.string.export_empty
            return@launch
        }

        val result = withContext(Dispatchers.IO) {
            exportManager.write(
                csv = ScanCsvBuilder.build(items),
                rawFileName = fileNameState.value,
                lineCount = items.size,
            )
        }
        result.fold(
            onSuccess = { exported -> onReady(exportManager.shareIntent(exported, emailPrefs.value)) },
            onFailure = { errorState.value = R.string.export_failed },
        )
    }

    fun reportNoApp(@StringRes message: Int = R.string.export_no_app) {
        errorState.value = message
    }
}
