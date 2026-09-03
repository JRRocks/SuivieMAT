package com.scanlist.app.ui.compare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.R
import com.scanlist.app.data.repository.CompareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Creation d'une session de comparaison, depuis un dossier local ou un import .csv.
 *
 * Les deux entrees du menu Comparer convergent ici : une seule session, un seul
 * moteur, un seul ecran de resultats, quelle que soit l'origine de la reference.
 */
@HiltViewModel
class CompareStartViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compareRepository: CompareRepository,
) : ViewModel() {

    private val _error = MutableStateFlow<Int?>(null)
    /** Message d'erreur en attente, sous forme de ressource. */
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun createFromFolder(folderId: Long, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val sessionId = compareRepository.createFromFolder(folderId)
        if (sessionId == null) {
            _error.value = R.string.compare_folder_missing
        } else {
            onCreated(sessionId)
        }
    }

    /**
     * Lit le .csv choisi via le Storage Access Framework.
     *
     * L'URI est ouvert immediatement : la permission de lecture accordee par le
     * selecteur n'est valable que pour cet aller-retour.
     */
    fun createFromCsv(uri: Uri, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val content = withContext(Dispatchers.IO) { readText(uri) }
        if (content.isNullOrBlank()) {
            _error.value = R.string.compare_csv_unreadable
            return@launch
        }
        val sessionId = compareRepository.createFromCsv(displayName(uri), content)
        if (sessionId == null) {
            _error.value = R.string.compare_csv_no_reference
        } else {
            onCreated(sessionId)
        }
    }

    private fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "reference.csv"
}
