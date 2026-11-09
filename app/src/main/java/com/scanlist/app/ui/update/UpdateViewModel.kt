package com.scanlist.app.ui.update

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.R
import com.scanlist.app.data.prefs.SettingsDataStore
import com.scanlist.app.domain.update.UpdateOutcome
import com.scanlist.app.domain.update.UpdatePolicy
import com.scanlist.app.update.UpdateCheck
import com.scanlist.app.update.UpdateDownload
import com.scanlist.app.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que la fenetre de mise a jour montre. */
sealed interface UpdateUiState {

    /** Rien a montrer. */
    data object Idle : UpdateUiState

    /** [megabytes] vaut `null` quand GitHub n'annonce pas la taille. */
    data class Available(val version: String, val megabytes: Int?) : UpdateUiState

    /** [percent] vaut `null` quand la taille du fichier est inconnue. */
    data class Downloading(val version: String, val percent: Int?) : UpdateUiState

    /**
     * L'APK est telecharge et controle. [launchInstaller] demande a l'ecran d'ouvrir
     * l'installateur d'Android, une seule fois ; ensuite la fenetre reste, pour le
     * relancer si la confirmation d'Android a ete refermee.
     */
    data class Ready(val version: String, val launchInstaller: Boolean) : UpdateUiState

    /** Le telechargement n'a pas abouti ; la mise a jour reste proposee. */
    data class Failed(@StringRes val message: Int) : UpdateUiState
}

/**
 * Message passager, en reponse a une recherche demandee par l'utilisateur. Le ViewModel
 * porte l'identifiant de ressource et non le texte : la redaction reste dans strings.xml.
 */
data class UpdateNotice(@StringRes val message: Int, val version: String)

/**
 * Mise a jour de l'application depuis les releases GitHub (decision 34).
 *
 * Deux entrees : une recherche automatique au lancement, au plus toutes les six heures,
 * qui ne dit rien tant qu'il n'y a rien de neuf ni quand le reseau manque (regle du
 * projet : sur erreur, ne rien faire) ; et une recherche demandee depuis l'onglet
 * Nouveau scan, qui repond toujours. Rien n'est telecharge ni installe sans un appui.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    val installedVersion: String = repository.installedVersionName

    private val stateFlow = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = stateFlow.asStateFlow()

    private val checkingFlow = MutableStateFlow(false)

    /** Vrai pendant une recherche demandee par l'utilisateur. */
    val checking: StateFlow<Boolean> = checkingFlow.asStateFlow()

    private val noticeFlow = MutableStateFlow<UpdateNotice?>(null)
    val notice: StateFlow<UpdateNotice?> = noticeFlow.asStateFlow()

    private var offer: UpdateOutcome.Available? = null
    private var apk: File? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            // L'APK d'une mise a jour deja installee n'a plus rien a faire dans le cache.
            repository.purgeDownloads()
            if (UpdatePolicy.isAutoCheckDue(settings.lastUpdateCheckAt.first(), System.currentTimeMillis())) {
                check(manual = false)
            }
        }
    }

    /** Recherche demandee par l'utilisateur : elle repond toujours, meme "a jour". */
    fun checkNow() {
        if (checkingFlow.value || stateFlow.value !is UpdateUiState.Idle) return
        viewModelScope.launch {
            checkingFlow.value = true
            check(manual = true)
            checkingFlow.value = false
        }
    }

    private suspend fun check(manual: Boolean) {
        when (val result = repository.check()) {
            is UpdateCheck.Done -> {
                settings.setLastUpdateCheckAt(System.currentTimeMillis())
                when (val outcome = result.outcome) {
                    is UpdateOutcome.Available -> propose(outcome)
                    UpdateOutcome.UpToDate ->
                        if (manual) noticeFlow.value = UpdateNotice(R.string.update_up_to_date, installedVersion)
                }
            }

            UpdateCheck.Unreachable ->
                if (manual) noticeFlow.value = UpdateNotice(R.string.update_check_failed, installedVersion)
        }
    }

    private fun propose(outcome: UpdateOutcome.Available) {
        // Une recherche qui aboutit pendant un telechargement ne doit pas le recouvrir.
        if (stateFlow.value !is UpdateUiState.Idle) return
        offer = outcome
        stateFlow.value = UpdateUiState.Available(
            version = outcome.version.toString(),
            megabytes = outcome.asset.sizeBytes.takeIf { it > 0 }?.let { ((it + BYTES_PER_MB / 2) / BYTES_PER_MB).toInt() },
        )
    }

    fun download() {
        val current = offer ?: return
        if (downloadJob?.isActive == true) return
        val version = current.version.toString()
        stateFlow.value = UpdateUiState.Downloading(version, percent = 0)
        downloadJob = viewModelScope.launch {
            var shown = 0
            val result = repository.download(current.asset) { fraction ->
                // Appele depuis le fil du telechargement, des centaines de fois : l'ecran
                // n'est prevenu qu'a chaque point de pourcentage gagne.
                val percent = fraction?.let { (it * 100).toInt().coerceIn(0, 100) }
                if (percent != shown) {
                    shown = percent ?: -1
                    // Seulement si la fenetre en est encore au telechargement : un point
                    // d'avancement arrive apres « Annuler » ne doit pas la rouvrir.
                    stateFlow.update { current ->
                        if (current is UpdateUiState.Downloading) UpdateUiState.Downloading(version, percent) else current
                    }
                }
            }
            stateFlow.value = when (result) {
                is UpdateDownload.Ready -> {
                    apk = result.apk
                    UpdateUiState.Ready(version, launchInstaller = true)
                }

                UpdateDownload.Failed -> UpdateUiState.Failed(R.string.update_download_failed)
                UpdateDownload.Invalid -> UpdateUiState.Failed(R.string.update_invalid_file)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        stateFlow.value = UpdateUiState.Idle
    }

    /** Intention d'installation de l'APK telecharge, ou `null` s'il n'est plus la. */
    fun installIntent(): Intent? = apk?.takeIf(File::exists)?.let(repository::installIntent)

    fun onInstallerLaunched() {
        val current = stateFlow.value as? UpdateUiState.Ready ?: return
        stateFlow.value = current.copy(launchInstaller = false)
    }

    fun onInstallerUnavailable() {
        stateFlow.value = UpdateUiState.Failed(R.string.update_no_installer)
    }

    /** « Plus tard » : la fenetre se ferme, la mise a jour sera reproposee. */
    fun dismiss() {
        stateFlow.value = UpdateUiState.Idle
    }

    fun consumeNotice() {
        noticeFlow.value = null
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
