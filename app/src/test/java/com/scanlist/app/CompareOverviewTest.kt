package com.scanlist.app

import com.scanlist.app.domain.compare.CompareEntry
import com.scanlist.app.domain.compare.CompareOverview
import com.scanlist.app.domain.compare.SessionProgress
import com.scanlist.app.domain.model.CompareSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareOverviewTest {

    /** Un dossier reduit a ce que la fusion lit : son id et sa derniere activite. */
    private data class Dossier(val id: Long, val updatedAt: Long)

    private fun depuisDossier(
        id: Long,
        dossier: Long,
        creee: Long,
        activite: Long = creee,
        trouves: Int = 0,
        total: Int = 10,
    ) = SessionProgress(
        sessionId = id,
        name = "Comparaison $dossier",
        sourceKind = CompareSourceKind.LOCAL_FOLDER,
        sourceRef = dossier.toString(),
        sourceFolderId = dossier,
        createdAt = creee,
        found = trouves,
        total = total,
        lastActivity = activite,
    )

    private fun depuisCsv(id: Long, creee: Long, activite: Long = creee) = SessionProgress(
        sessionId = id,
        name = "Comparaison armurerie.csv",
        sourceKind = CompareSourceKind.CSV_IMPORT,
        sourceRef = "armurerie.csv",
        sourceFolderId = null,
        createdAt = creee,
        found = 0,
        total = 140,
        lastActivity = activite,
    )

    private fun fusion(dossiers: List<Dossier>, sessions: List<SessionProgress>) =
        CompareOverview.build(dossiers, { it.id }, { it.updatedAt }, sessions)

    @Test
    fun `un dossier jamais compare reste une simple ligne a sa date`() {
        val lignes = fusion(listOf(Dossier(1, updatedAt = 500)), emptyList())

        val ligne = lignes.single() as CompareEntry.FromFolder
        assertNull(ligne.session)
        assertEquals(500, ligne.timestamp)
    }

    @Test
    fun `un dossier porte la derniere de ses comparaisons`() {
        val ancienne = depuisDossier(id = 1, dossier = 7, creee = 100)
        val recente = depuisDossier(id = 2, dossier = 7, creee = 300)

        val lignes = fusion(listOf(Dossier(7, updatedAt = 50)), listOf(recente, ancienne))

        val ligne = lignes.single() as CompareEntry.FromFolder
        assertEquals(2L, ligne.session?.sessionId)
    }

    @Test
    fun `a creation egale la session la plus recemment inseree l emporte`() {
        val premiere = depuisDossier(id = 3, dossier = 7, creee = 100)
        val seconde = depuisDossier(id = 4, dossier = 7, creee = 100)

        val ligne = fusion(listOf(Dossier(7, 50)), listOf(premiere, seconde)).single()

        assertEquals(4L, (ligne as CompareEntry.FromFolder).session?.sessionId)
    }

    @Test
    fun `une validation recente fait remonter un vieux dossier en tete`() {
        val vieux = Dossier(1, updatedAt = 100)
        val recent = Dossier(2, updatedAt = 500)
        val controleDuJour = depuisDossier(id = 9, dossier = 1, creee = 200, activite = 900)

        val lignes = fusion(listOf(recent, vieux), listOf(controleDuJour))

        assertEquals(listOf(1L, 2L), lignes.map { (it as CompareEntry.FromFolder).folder.id })
        assertEquals(900, lignes.first().timestamp)
    }

    @Test
    fun `un import csv prend place a sa date entre les dossiers`() {
        val lignes = fusion(
            listOf(Dossier(1, updatedAt = 900), Dossier(2, updatedAt = 100)),
            listOf(depuisCsv(id = 5, creee = 400)),
        )

        assertTrue(lignes[0] is CompareEntry.FromFolder)
        assertEquals(5L, (lignes[1] as CompareEntry.Standalone).session.sessionId)
        assertTrue(lignes[2] is CompareEntry.FromFolder)
    }

    @Test
    fun `chaque import csv est une ligne a part`() {
        val imports = listOf(depuisCsv(id = 5, creee = 400), depuisCsv(id = 6, creee = 300))

        val lignes = fusion(emptyList(), imports)

        assertEquals(
            listOf(5L, 6L),
            lignes.map { (it as CompareEntry.Standalone).session.sessionId },
        )
    }

    @Test
    fun `la comparaison d un dossier supprime reste reprenable`() {
        val orpheline = depuisDossier(id = 8, dossier = 42, creee = 300)
        val plusAncienne = depuisDossier(id = 7, dossier = 42, creee = 100)

        val lignes = fusion(listOf(Dossier(1, 50)), listOf(orpheline, plusAncienne))

        // Seule la derniere est montree, comme sous un dossier encore present.
        val seules = lignes.filterIsInstance<CompareEntry.Standalone>()
        assertEquals(listOf(8L), seules.map { it.session.sessionId })
    }

    @Test
    fun `a moment egal l ordre de la base est conserve`() {
        val lignes = fusion(listOf(Dossier(3, 100), Dossier(1, 100), Dossier(2, 100)), emptyList())

        assertEquals(listOf(3L, 1L, 2L), lignes.map { (it as CompareEntry.FromFolder).folder.id })
    }

    @Test
    fun `une session est complete quand toute la liste est validee`() {
        val complete = depuisDossier(id = 1, dossier = 1, creee = 0, trouves = 10, total = 10)
        val enCours = depuisDossier(id = 2, dossier = 1, creee = 0, trouves = 9, total = 10)
        val vide = depuisDossier(id = 3, dossier = 1, creee = 0, trouves = 0, total = 0)

        assertTrue(complete.isComplete)
        assertFalse(enCours.isComplete)
        assertEquals(1, enCours.missing)
        // Une liste vide n'a rien de valide : elle n'est pas « complete ».
        assertFalse(vide.isComplete)
    }
}
