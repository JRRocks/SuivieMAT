package com.scanlist.app.data.repository

import com.scanlist.app.data.db.dao.RegexRuleDao
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.domain.regex.RegexRuleEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Issue de l'enregistrement d'une formule regex. */
sealed interface RegexSaveResult {
    data class Saved(val id: Long) : RegexSaveResult
    data class Invalid(val message: String) : RegexSaveResult
    data object NameTaken : RegexSaveResult
}

@Singleton
class RegexRuleRepository @Inject constructor(
    private val regexRuleDao: RegexRuleDao,
) {

    fun observeAll(): Flow<List<RegexRuleEntity>> = regexRuleDao.observeAll()

    suspend fun getById(id: Long): RegexRuleEntity? = regexRuleDao.getById(id)

    /**
     * Enregistre une formule saisie a la volee, apres validation.
     *
     * Une formule invalide n'est jamais persistee : elle serait rechargee plus tard et
     * echouerait silencieusement en plein scan.
     */
    suspend fun save(name: String, pattern: String, groupIndex: Int = 0): RegexSaveResult {
        val validation = RegexRuleEngine.validate(pattern)
        if (validation is RegexRuleEngine.Validation.Invalid) {
            return RegexSaveResult.Invalid(validation.message)
        }
        val trimmedName = name.trim().ifEmpty { pattern.take(24) }
        if (regexRuleDao.getByName(trimmedName) != null) return RegexSaveResult.NameTaken

        val now = System.currentTimeMillis()
        val id = regexRuleDao.insert(
            RegexRuleEntity(
                name = trimmedName,
                pattern = pattern,
                groupIndex = groupIndex,
                createdAt = now,
                lastUsedAt = now,
            ),
        )
        return RegexSaveResult.Saved(id)
    }

    suspend fun update(rule: RegexRuleEntity): RegexSaveResult {
        val validation = RegexRuleEngine.validate(rule.pattern)
        if (validation is RegexRuleEngine.Validation.Invalid) {
            return RegexSaveResult.Invalid(validation.message)
        }
        regexRuleDao.update(rule)
        return RegexSaveResult.Saved(rule.id)
    }

    suspend fun delete(rule: RegexRuleEntity) = regexRuleDao.delete(rule)

    suspend fun markUsed(id: Long) = regexRuleDao.markUsed(id, System.currentTimeMillis())

    /**
     * Precharge deux formules d'exemple.
     *
     * A appeler une seule fois : c'est a l'appelant (voir `ScanListApplication`) de ne
     * declencher ceci qu'au tout premier lancement, via un indicateur persistant dans
     * `SettingsDataStore`. Un utilisateur qui supprime ensuite ces formules ne doit
     * jamais les voir revenir toutes seules.
     *
     * Idempotent par construction malgre tout : une formule dont le nom existe deja
     * n'est jamais reinseree (l'index `name` est unique, un doublon ferait planter
     * l'insertion), au cas ou l'appelant serait invoque une seconde fois par erreur.
     * Insere directement par l'entite plutot que par [save] pour maitriser
     * l'horodatage et garantir un ordre d'affichage stable (bibliotheque triee par
     * `lastUsedAt DESC`) ; les deux formules sont deja validees, la verification de
     * [save] serait redondante.
     */
    suspend fun seedDefaults() {
        val now = System.currentTimeMillis()
        DEFAULT_RULES.forEachIndexed { index, (name, pattern) ->
            if (regexRuleDao.getByName(name) != null) return@forEachIndexed
            regexRuleDao.insert(
                RegexRuleEntity(
                    name = name,
                    pattern = pattern,
                    groupIndex = 0,
                    createdAt = now,
                    // Decroissant avec l'index : le premier de la liste reste en tete.
                    lastUsedAt = now - index,
                ),
            )
        }
    }

    private companion object {
        /**
         * HK416 : deux chiffres, un tiret, six chiffres (ex. numero de serie d'arme).
         * ARVA : cinq chiffres (ex. identifiant d'appareil de recherche de victimes
         * d'avalanche). Bornees par \b pour ne retenir que la sequence complete.
         */
        val DEFAULT_RULES = listOf(
            "HK416" to """\b\d{2}-\d{6}\b""",
            "ARVA" to """\b\d{5}\b""",
        )
    }
}
