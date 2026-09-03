package com.scanlist.app.ui.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.data.db.entity.FolderEntity
import com.scanlist.app.data.db.entity.FolderSummary
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.data.repository.FolderRepository
import com.scanlist.app.data.repository.ScanRepository
import com.scanlist.app.domain.search.FolderSearch
import com.scanlist.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Liste des dossiers du menu "Dossiers", filtrable par nom. */
@HiltViewModel
class FolderListViewModel @Inject constructor(
    folderRepository: FolderRepository,
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()

    private val allFolders = folderRepository.observeAll()

    /**
     * Le filtre est applique en memoire plutot qu'en SQL : le nombre de dossiers reste
     * de l'ordre de la dizaine, et une requete par frappe serait du gaspillage.
     * La comparaison elle-meme est deleguee a [FolderSearch], testable sans Android.
     */
    val folders: StateFlow<List<FolderSummary>> =
        combine(allFolders, queryState) { list, query ->
            FolderSearch.filter(list, query) { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Distingue "aucun dossier" de "aucun resultat pour cette recherche". */
    val hasAnyFolder: StateFlow<Boolean> = allFolders
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuery(value: String) {
        queryState.value = value
    }
}

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val scanRepository: ScanRepository,
) : ViewModel() {

    val folderId: Long = savedStateHandle.get<Long>(Routes.ARG_FOLDER_ID) ?: Routes.NO_ID

    val folder: StateFlow<FolderEntity?> = folderRepository.observeById(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<ScanItemEntity>> = scanRepository.observeItems(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val itemCount: StateFlow<Int> = scanRepository.observeCount(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Derniere ligne supprimee, conservee le temps de proposer l'annulation.
     *
     * Une suppression est definitive en base ; garder l'entite en memoire evite
     * d'ajouter une colonne "supprime" a la table pour un besoin passager.
     */
    private val lastDeletedState = MutableStateFlow<ScanItemEntity?>(null)
    val lastDeleted: StateFlow<ScanItemEntity?> = lastDeletedState.asStateFlow()

    fun deleteItem(item: ScanItemEntity) = viewModelScope.launch {
        scanRepository.delete(item)
        lastDeletedState.value = item
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeletedState.value?.let { scanRepository.restore(it) }
        lastDeletedState.value = null
    }

    fun consumeDeletion() {
        lastDeletedState.value = null
    }

    fun rename(name: String) = viewModelScope.launch {
        folderRepository.rename(folderId, name)
    }

    fun deleteFolder(onDone: () -> Unit) = viewModelScope.launch {
        folderRepository.deleteById(folderId)
        onDone()
    }
}
