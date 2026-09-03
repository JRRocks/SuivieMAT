package com.scanlist.app

import com.scanlist.app.scanner.ScanDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebouncerTest {

    @Test
    fun `une valeur inedite passe`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        assertTrue(debouncer.accept("BAT-001", now = 0))
    }

    @Test
    fun `la meme valeur est ignoree pendant la fenetre`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        debouncer.accept("BAT-001", now = 0)

        assertFalse(debouncer.accept("BAT-001", now = 100))
        assertFalse(debouncer.accept("BAT-001", now = 999))
    }

    @Test
    fun `la valeur repasse une fois la fenetre ecoulee`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        debouncer.accept("BAT-001", now = 0)

        assertTrue(debouncer.accept("BAT-001", now = 1_000))
    }

    @Test
    fun `la fenetre repart du dernier passage accepte`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        debouncer.accept("BAT-001", now = 0)
        debouncer.accept("BAT-001", now = 1_000)

        assertFalse(debouncer.accept("BAT-001", now = 1_500))
    }

    @Test
    fun `deux valeurs differentes ne se bloquent pas`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        assertTrue(debouncer.accept("BAT-001", now = 0))
        assertTrue(debouncer.accept("BAT-002", now = 0))
    }

    @Test
    fun `une valeur vide ne passe jamais`() {
        val debouncer = ScanDebouncer()

        assertFalse(debouncer.accept("   ", now = 0))
        assertFalse(debouncer.accept("", now = 0))
    }

    @Test
    fun `le filtrage suit la meme cle que la deduplication`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        debouncer.accept("BAT-001", now = 0)

        // Casse et espaces parasites : meme code, donc meme blocage.
        assertFalse(debouncer.accept("bat-001", now = 100))
        assertFalse(debouncer.accept("  BAT-001  ", now = 100))
    }

    @Test
    fun `acceptAll conserve l ordre de lecture des codes d une image`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        val retenus = debouncer.acceptAll(
            listOf("BAT-003", "BAT-001", "BAT-003", "BAT-002"),
            now = 0,
        )

        assertEquals(listOf("BAT-003", "BAT-001", "BAT-002"), retenus)
    }

    @Test
    fun `une image sans nouveaute ne remonte rien`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        debouncer.acceptAll(listOf("BAT-001", "BAT-002"), now = 0)

        assertEquals(
            emptyList<String>(),
            debouncer.acceptAll(listOf("BAT-001", "BAT-002"), now = 200),
        )
    }

    @Test
    fun `reset rend immediatement une valeur rescannable`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        debouncer.accept("BAT-001", now = 0)
        debouncer.reset()

        assertTrue(debouncer.accept("BAT-001", now = 100))
    }

    @Test
    fun `une longue session ne fait pas grossir la table indefiniment`() {
        val debouncer = ScanDebouncer(windowMs = 1_000)

        // 500 codes distincts espaces d'une seconde : les anciens doivent etre purges.
        repeat(500) { index ->
            debouncer.accept("BAT-$index", now = index * 1_000L)
        }

        // Le tout premier code est sorti de la fenetre : il repasse.
        assertTrue(debouncer.accept("BAT-0", now = 500_000))
    }
}
