package com.scanlist.app

import com.scanlist.app.domain.compare.CompareEngine
import com.scanlist.app.domain.compare.CompareOutcome
import com.scanlist.app.domain.model.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareEngineTest {

    private fun engine(vararg reference: String) = CompareEngine(reference.toList())

    @Test
    fun `toute la reference part en rouge`() {
        val moteur = engine("BAT-001", "BAT-002")

        assertEquals(2, moteur.missingCount)
        assertEquals(0, moteur.foundCount)
        assertEquals(MatchState.MISSING, moteur.stateOf("BAT-001"))
        assertEquals(MatchState.MISSING, moteur.stateOf("BAT-002"))
    }

    @Test
    fun `un element de la reference lu passe au vert`() {
        val moteur = engine("BAT-001", "BAT-002")

        val verdict = moteur.submit("BAT-001")

        assertTrue(verdict is CompareOutcome.Matched)
        assertEquals(MatchState.FOUND, moteur.stateOf("BAT-001"))
        assertEquals(1, moteur.foundCount)
    }

    @Test
    fun `un element jamais lu reste rouge`() {
        val moteur = engine("BAT-001", "BAT-002")

        moteur.submit("BAT-001")

        assertEquals(MatchState.MISSING, moteur.stateOf("BAT-002"))
        assertEquals(listOf("BAT-002"), moteur.missingValues)
        assertEquals(1, moteur.missingCount)
    }

    @Test
    fun `une valeur hors reference n est pas prise en compte`() {
        // Regle posee par l'utilisateur : seule la liste de base change de couleur.
        val moteur = engine("BAT-001")

        val verdict = moteur.submit("BAT-999")

        assertTrue(verdict is CompareOutcome.OutOfList)
        assertEquals(1, moteur.referenceCount)
        assertEquals(0, moteur.foundCount)
        assertEquals(1, moteur.missingCount)
        assertEquals(listOf("BAT-001"), moteur.missingValues)
    }

    @Test
    fun `une valeur hors reference lue deux fois ne laisse toujours aucune trace`() {
        val moteur = engine("BAT-001")

        moteur.submit("BAT-999")
        moteur.submit("BAT-999")

        assertEquals(1, moteur.referenceCount)
        assertEquals(1, moteur.missingCount)
    }

    @Test
    fun `la valeur hors reference est renvoyee normalisee pour le bandeau`() {
        val moteur = engine("BAT-001")

        val verdict = moteur.submit("  BAT   999 ")

        assertEquals("BAT 999", (verdict as CompareOutcome.OutOfList).value)
    }

    @Test
    fun `un element deja confirme ne declenche pas de seconde ecriture`() {
        val moteur = engine("BAT-001")

        moteur.submit("BAT-001")
        val second = moteur.submit("BAT-001")

        assertTrue(second is CompareOutcome.AlreadyMatched)
        assertEquals(1, moteur.foundCount)
    }

    @Test
    fun `une valeur vide ne declenche rien`() {
        val moteur = engine("BAT-001")

        assertEquals(CompareOutcome.Ignored, moteur.submit("   "))
        assertEquals(0, moteur.foundCount)
    }

    @Test
    fun `la casse est ignoree par defaut`() {
        val moteur = engine("BAT-001")

        assertTrue(moteur.submit("bat-001") is CompareOutcome.Matched)
    }

    @Test
    fun `la casse est respectee quand le mode strict est demande`() {
        val moteur = CompareEngine(listOf("BAT-001"), caseSensitive = true)

        assertTrue(moteur.submit("bat-001") is CompareOutcome.OutOfList)
    }

    @Test
    fun `les espaces parasites n empechent pas la correspondance`() {
        val moteur = engine("BAT 001")

        assertTrue(moteur.submit("  BAT   001 ") is CompareOutcome.Matched)
    }

    @Test
    fun `une reference dupliquee n est comptee qu une fois`() {
        val moteur = engine("BAT-001", "BAT-001", "  ", "BAT-002")

        assertEquals(2, moteur.referenceCount)
    }

    @Test
    fun `une session interrompue reprend son etat`() {
        val moteur = engine("BAT-001", "BAT-002", "BAT-003")

        moteur.restoreFound(listOf("BAT-001"))

        assertEquals(MatchState.FOUND, moteur.stateOf("BAT-001"))
        assertEquals(1, moteur.foundCount)
        assertEquals(listOf("BAT-002", "BAT-003"), moteur.missingValues)
    }

    @Test
    fun `restoreFound ignore une valeur absente de la reference`() {
        val moteur = engine("BAT-001")

        moteur.restoreFound(listOf("BAT-404"))

        assertEquals(0, moteur.foundCount)
    }

    @Test
    fun `rouge et vert couvrent toujours toute la reference`() {
        val moteur = engine("BAT-001", "BAT-002", "BAT-003")

        moteur.submit("BAT-001")
        moteur.submit("BAT-003")
        moteur.submit("BAT-999")

        assertEquals(3, moteur.referenceCount)
        assertEquals(2, moteur.foundCount)
        assertEquals(1, moteur.missingCount)
        assertEquals(moteur.referenceCount, moteur.foundCount + moteur.missingCount)
        assertEquals(listOf("BAT-001", "BAT-003"), moteur.foundValues)
        assertEquals(listOf("BAT-002"), moteur.missingValues)
    }

    @Test
    fun `la valeur affichee est celle de la reference et non celle qui a ete lue`() {
        // L'OCR peut restituer une casse differente : la liste doit rester lisible
        // telle qu'elle a ete importee.
        val moteur = engine("BAT-001")

        val verdict = moteur.submit("bat-001")

        assertEquals("BAT-001", (verdict as CompareOutcome.Matched).referenceValue)
        assertEquals(listOf("BAT-001"), moteur.foundValues)
    }
}
