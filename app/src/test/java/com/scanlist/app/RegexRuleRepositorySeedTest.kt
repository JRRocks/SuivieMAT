package com.scanlist.app

import com.scanlist.app.data.db.dao.RegexRuleDao
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.data.repository.RegexRuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * seedDefaults() n'est jamais exerce sur appareil reel ni sur emulateur : c'est
 * l'unique filet qui verrouille le contenu des formules livrees par defaut et leur
 * caractere idempotent.
 */
class RegexRuleRepositorySeedTest {

    private class FakeRegexRuleDao : RegexRuleDao {
        val rules = mutableListOf<RegexRuleEntity>()
        private var nextId = 1L

        override fun observeAll() = MutableStateFlow(rules.toList())
        override suspend fun getById(id: Long) = rules.firstOrNull { it.id == id }
        override suspend fun getByName(name: String) = rules.firstOrNull { it.name == name }

        override suspend fun insert(rule: RegexRuleEntity): Long {
            val withId = rule.copy(id = nextId++)
            rules += withId
            return withId.id
        }

        override suspend fun update(rule: RegexRuleEntity) {
            val index = rules.indexOfFirst { it.id == rule.id }
            if (index >= 0) rules[index] = rule
        }

        override suspend fun delete(rule: RegexRuleEntity) {
            rules.removeAll { it.id == rule.id }
        }

        override suspend fun markUsed(id: Long, timestamp: Long) {
            val index = rules.indexOfFirst { it.id == id }
            if (index >= 0) rules[index] = rules[index].copy(lastUsedAt = timestamp)
        }
    }

    @Test
    fun `HK416 et ARVA sont creees avec leurs formules exactes`() = runBlocking {
        val dao = FakeRegexRuleDao()
        val repository = RegexRuleRepository(dao)

        repository.seedDefaults()

        assertEquals(2, dao.rules.size)
        assertEquals("""\b\d{2}-\d{6}\b""", dao.rules.first { it.name == "HK416" }.pattern)
        assertEquals("""\b\d{5}\b""", dao.rules.first { it.name == "ARVA" }.pattern)
    }

    @Test
    fun `HK416 reste en tete, triee par derniere utilisation`() = runBlocking {
        val dao = FakeRegexRuleDao()
        val repository = RegexRuleRepository(dao)

        repository.seedDefaults()

        val parOrdreAffiche = dao.rules.sortedByDescending { it.lastUsedAt }
        assertEquals("HK416", parOrdreAffiche.first().name)
        assertEquals("ARVA", parOrdreAffiche.last().name)
    }

    @Test
    fun `un appel repete ne duplique jamais une formule deja presente`() = runBlocking {
        val dao = FakeRegexRuleDao()
        val repository = RegexRuleRepository(dao)
        repository.seedDefaults()

        repository.seedDefaults()

        assertEquals(2, dao.rules.size)
        assertEquals(1, dao.rules.count { it.name == "HK416" })
        assertEquals(1, dao.rules.count { it.name == "ARVA" })
    }

    @Test
    fun `seedDefaults seul ne protege pas une suppression - c est le role de l appelant`() = runBlocking {
        // Documente une limite volontaire : seedDefaults() garantit seulement de ne
        // pas dupliquer un nom deja present, pas de se souvenir d'une suppression.
        // C'est ScanListApplication, via l'indicateur persistant de SettingsDataStore,
        // qui garantit de ne jamais rappeler cette methode une seconde fois.
        val dao = FakeRegexRuleDao()
        val repository = RegexRuleRepository(dao)
        repository.seedDefaults()
        dao.rules.removeAll { it.name == "ARVA" }

        repository.seedDefaults()

        assertTrue(dao.rules.any { it.name == "ARVA" })
    }
}
