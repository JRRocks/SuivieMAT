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
}
