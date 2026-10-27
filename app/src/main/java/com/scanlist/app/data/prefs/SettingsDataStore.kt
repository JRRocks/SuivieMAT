package com.scanlist.app.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanlist.app.R
import com.scanlist.app.domain.model.ScanMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanlist_settings")

/**
 * Preferences locales.
 *
 * Contient uniquement des reglages d'usage : destinataire et objet par defaut de
 * l'export, dernier mode de scan, derniere formule regex. Aucun identifiant de
 * messagerie n'y est stocke, l'envoi passant par un Intent vers le client mail.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {

    private object Keys {
        val EMAIL_RECIPIENT = stringPreferencesKey("email_recipient")
        val EMAIL_SUBJECT = stringPreferencesKey("email_subject")
        val EMAIL_BODY = stringPreferencesKey("email_body")
        val LAST_SCAN_MODE = stringPreferencesKey("last_scan_mode")
        val LAST_REGEX_RULE_ID = longPreferencesKey("last_regex_rule_id")
        val REGEX_DEFAULTS_SEEDED = booleanPreferencesKey("regex_defaults_seeded")
    }

    val emailPrefs: Flow<EmailPrefs> = context.dataStore.data.map { prefs ->
        EmailPrefs(
            recipient = prefs[Keys.EMAIL_RECIPIENT].orEmpty(),
            subject = prefs[Keys.EMAIL_SUBJECT].orDefault(
                legacy = LEGACY_SUBJECT,
                default = R.string.export_default_subject,
            ),
            body = prefs[Keys.EMAIL_BODY].orDefault(
                legacy = LEGACY_BODY,
                default = R.string.export_default_body,
            ),
        )
    }

    /**
     * Valeur enregistree, ou celle par defaut. L'ancien defaut (au nom de ScanList, une
     * autre application) est traite comme absent : il a ete enregistre tel quel par
     * quiconque a valide l'ecran Preferences sans y toucher, et ne doit pas survivre au
     * changement de nom.
     */
    private fun String?.orDefault(legacy: String, @StringRes default: Int): String =
        this?.takeUnless { it == legacy } ?: context.getString(default)

    val lastScanMode: Flow<ScanMode> = context.dataStore.data.map { prefs ->
        ScanMode.fromNameOrNull(prefs[Keys.LAST_SCAN_MODE]) ?: ScanMode.QR_CODE
    }

    val lastRegexRuleId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_REGEX_RULE_ID]
    }

    /** Vrai des que les formules d'exemple (HK416, ARVA) ont ete inserees une premiere fois. */
    val regexDefaultsSeeded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.REGEX_DEFAULTS_SEEDED] ?: false
    }

    suspend fun setEmailPrefs(prefs: EmailPrefs) {
        context.dataStore.edit { store ->
            store[Keys.EMAIL_RECIPIENT] = prefs.recipient
            store[Keys.EMAIL_SUBJECT] = prefs.subject
            store[Keys.EMAIL_BODY] = prefs.body
        }
    }

    suspend fun setLastScanMode(mode: ScanMode) {
        context.dataStore.edit { it[Keys.LAST_SCAN_MODE] = mode.name }
    }

    suspend fun setLastRegexRuleId(id: Long?) {
        context.dataStore.edit { store ->
            if (id == null) store.remove(Keys.LAST_REGEX_RULE_ID) else store[Keys.LAST_REGEX_RULE_ID] = id
        }
    }

    suspend fun markRegexDefaultsSeeded() {
        context.dataStore.edit { it[Keys.REGEX_DEFAULTS_SEEDED] = true }
    }

    private companion object {
        // Defauts des premieres versions, du temps du nom d'origine (decision 23).
        const val LEGACY_SUBJECT = "Export ScanList"
        const val LEGACY_BODY = "Liste exportee depuis ScanList."
    }
}

/**
 * Valeurs pre-remplies dans l'Intent d'envoi de l'export .csv. Les defauts reels
 * viennent des ressources, via [SettingsDataStore.emailPrefs] ; ceux-ci ne servent que
 * de valeur d'attente, le temps d'une lecture.
 */
data class EmailPrefs(
    val recipient: String = "",
    val subject: String = "",
    val body: String = "",
)
