package com.scanlist.app

import com.scanlist.app.domain.search.FolderSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSearchTest {

    private val dossiers = listOf(
        "Inventaire batteries — Atelier A",
        "Contrôle rack 3",
        "Relevé OCR étiquettes",
        "Scan QR Code 20-08-2026 10h42",
    )

    @Test
    fun `une requete vide ne filtre rien`() {
        assertEquals(dossiers, FolderSearch.filter(dossiers, "") { it })
        assertEquals(dossiers, FolderSearch.filter(dossiers, "   ") { it })
    }

    @Test
    fun `la recherche ignore la casse`() {
        assertTrue(FolderSearch.matches("Contrôle rack 3", "RACK"))
        assertTrue(FolderSearch.matches("Contrôle rack 3", "rack"))
    }

    @Test
    fun `la recherche ignore les accents saisis par l utilisateur`() {
        // Le clavier du telephone ne met pas d'accent spontanement : "controle"
        // doit trouver « Contrôle rack 3 », sinon la recherche echoue sur les noms
        // francais les plus courants.
        assertTrue(FolderSearch.matches("Contrôle rack 3", "controle"))
        assertTrue(FolderSearch.matches("Relevé OCR étiquettes", "releve"))
        assertTrue(FolderSearch.matches("Relevé OCR étiquettes", "etiquettes"))
    }

    @Test
    fun `la recherche fonctionne aussi si l utilisateur met les accents`() {
        assertTrue(FolderSearch.matches("Controle rack 3", "contrôle"))
    }

    @Test
    fun `une correspondance partielle au milieu du nom suffit`() {
        assertTrue(FolderSearch.matches("Inventaire batteries — Atelier A", "batteries"))
        assertTrue(FolderSearch.matches("Scan QR Code 20-08-2026 10h42", "20-08"))
    }

    @Test
    fun `un nom sans rapport ne correspond pas`() {
        assertFalse(FolderSearch.matches("Contrôle rack 3", "batterie"))
    }

    @Test
    fun `le filtre conserve l ordre d origine`() {
        val resultat = FolderSearch.filter(dossiers, "e") { it }

        assertEquals(resultat, resultat.sortedBy { dossiers.indexOf(it) })
    }

    @Test
    fun `le filtre ne retient que les dossiers correspondants`() {
        assertEquals(
            listOf("Contrôle rack 3"),
            FolderSearch.filter(dossiers, "rack") { it },
        )
    }

    @Test
    fun `les espaces autour de la requete sont ignores`() {
        assertEquals(
            listOf("Contrôle rack 3"),
            FolderSearch.filter(dossiers, "  rack  ") { it },
        )
    }
}
