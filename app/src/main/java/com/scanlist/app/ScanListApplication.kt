package com.scanlist.app

import android.app.Application
import com.scanlist.app.data.prefs.SettingsDataStore
import com.scanlist.app.data.repository.RegexRuleRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Point d'entree Hilt de l'application.
 *
 * ScanList fonctionne integralement hors-ligne : base Room locale, modeles ML Kit
 * embarques dans l'APK, aucune permission reseau declaree.
 */
@HiltAndroidApp
class ScanListApplication : Application() {

    @Inject
    lateinit var regexRuleRepository: RegexRuleRepository

    @Inject
    lateinit var settings: SettingsDataStore

    /** Survit a toute Activity : la base ne doit pas dependre du cycle de vie de l'UI. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Precharge la bibliotheque de formules au tout premier lancement, jamais
        // ensuite : l'indicateur persistant est ce qui empeche HK416/ARVA de revenir
        // si l'utilisateur les a supprimees. Hors du thread principal, sans bloquer
        // le demarrage de l'ecran.
        applicationScope.launch {
            if (!settings.regexDefaultsSeeded.first()) {
                regexRuleRepository.seedDefaults()
                settings.markRegexDefaultsSeeded()
            }
        }
    }
}
