package com.scanlist.app.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.data.db.entity.FolderSummary
import com.scanlist.app.data.repository.CompareRepository
import com.scanlist.app.data.repository.FolderRepository
import com.scanlist.app.domain.compare.CompareEntry
import com.scanlist.app.domain.compare.CompareOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Onglet Comparer : les dossiers et les imports .csv, chacun avec l'avancement de sa
 * derniere comparaison. La fusion elle-meme est dans [CompareOverview], testable sans
 * Android.
 */
@HiltViewModel
class CompareHomeViewModel @Inject constructor(
    folderRepository: FolderRepository,
    compareRepository: CompareRepository,
) : ViewModel() {

    /** null tant que la base n'a pas repondu : l'ecran n'affiche pas « vide » a tort. */
    val entries: StateFlow<List<CompareEntry<FolderSummary>>?> =
        combine(
            folderRepository.observeAll(),
            compareRepository.observeSessionProgress(),
        ) { folders, sessions ->
            CompareOverview.build(folders, { it.id }, { it.updatedAt }, sessions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
