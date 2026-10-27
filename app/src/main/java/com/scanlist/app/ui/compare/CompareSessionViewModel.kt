package com.scanlist.app.ui.compare

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.R
import com.scanlist.app.data.csv.CompareCsvBuilder
import com.scanlist.app.data.db.entity.ComparisonSessionEntity
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.data.prefs.EmailPrefs
import com.scanlist.app.data.prefs.SettingsDataStore
import com.scanlist.app.data.repository.CompareRepository
import com.scanlist.app.data.repository.FolderRepository
import com.scanlist.app.export.ExportManager
import com.scanlist.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Ecran de resultats d'une session de comparaison.
 *
 * Tout est lu depuis la base : la session survit a la fermeture de l'application, on
 * peut la reprendre le lendemain et continuer a valider la ou on s'etait arrete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompareSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val compareRepository: CompareRepository,
    folderRepository: FolderRepository,
    settings: SettingsDataStore,
    private val exportManager: ExportManager,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle[Routes.ARG_SESSION_ID] ?: Routes.NO_ID

    val session: StateFlow<ComparisonSessionEntity?> = compareRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val references: StateFlow<List<ReferenceEntryEntity>> =
        compareRepository.observeReferences(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Nom du dossier ou la session a ete enregistree, null tant qu'elle ne l'est pas.
     * Observe et non lu une fois : l'ecran doit basculer des l'enregistrement fait.
     */
    val resultFolderName: StateFlow<String?> = session
        .flatMapLatest { current ->
            val folderId = current?.resultFolderId
            if (folderId == null) flowOf(null) else folderRepository.observeById(folderId)
        }
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messageState = MutableStateFlow<Int?>(null)

    /** Message en attente d'affichage, sous forme de ressource. */
    val message: StateFlow<Int?> = messageState.asStateFlow()

    private val emailPrefs: StateFlow<EmailPrefs> = settings.emailPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmailPrefs())

    fun consumeMessage() {
        messageState.value = null
    }

    /**
     * Enregistre la session dans un dossier unique.
     *
     * L'appel est idempotent cote depot : appuyer deux fois ne cree pas deux dossiers.
     */
    fun saveToFolder() = viewModelScope.launch {
        val folderId = compareRepository.saveSessionToFolder(sessionId)
        messageState.value = if (folderId == null) {
            R.string.compare_save_failed
        } else {
            R.string.compare_saved
        }
    }

    /**
     * Genere le .csv de la session et remonte l'intention d'envoi.
     *
     * Ce fichier est le seul a porter les manquants : le dossier de resultat, lui, ne
     * contient que les elements dont la presence a ete confirmee.
     */
    fun buildShareIntent(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val result = compareRepository.buildResult(sessionId)
        if (result == null || result.referenceCount == 0) {
            messageState.value = R.string.compare_export_empty
            return@launch
        }

        val written = withContext(Dispatchers.IO) {
            exportManager.write(
                csv = CompareCsvBuilder.build(
                    missing = result.missing,
                    found = result.found,
                ),
                rawFileName = exportManager.suggestFileName(result.session.name),
                lineCount = result.referenceCount,
            )
        }
        written.fold(
            onSuccess = { exported -> onReady(exportManager.shareIntent(exported, emailPrefs.value)) },
            onFailure = { messageState.value = R.string.export_failed },
        )
    }

    /** Supprime la session ; [onDone] quitte l'ecran qui la decrivait. */
    fun deleteSession(onDone: () -> Unit) = viewModelScope.launch {
        compareRepository.deleteSession(sessionId)
        onDone()
    }

    fun reportNoApp(@StringRes messageRes: Int = R.string.export_no_app) {
        messageState.value = messageRes
    }
}
