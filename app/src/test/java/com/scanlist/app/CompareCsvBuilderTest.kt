package com.scanlist.app

import com.scanlist.app.data.csv.CompareCsvBuilder
import com.scanlist.app.data.csv.CsvCodec
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.domain.model.ReferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareCsvBuilderTest {

    private fun found(value: String, label: String? = null, at: Long = 1_755_693_000_000L) =
        ReferenceEntryEntity(
            id = 0,
            sessionId = 1,
            value = value,
            label = label,
            state = ReferenceState.FOUND,
            matchedAt = at,
        )

    private fun missing(value: String, label: String? = null) = ReferenceEntryEntity(
        id = 0,
        sessionId = 1,
        value = value,
        label = label,
        state = ReferenceState.MISSING,
    )

    @Test
    fun `la premiere ligne est l en-tete attendu`() {
        val rows = CompareCsvBuilder.rows(missing = emptyList(), found = emptyList())

        assertEquals(listOf(CompareCsvBuilder.HEADER), rows)
        assertEquals(listOf("valeur", "etat", "libelle", "date_validation"), rows.first())
    }

    @Test
    fun `les manquants sortent avant les trouves`() {
        val rows = CompareCsvBuilder.rows(
            missing = listOf(missing("BAT-002")),
            found = listOf(found("BAT-001")),
        )

        assertEquals(listOf("BAT-002", "BAT-001"), rows.drop(1).map { it[0] })
    }

    @Test
    fun `les deux etats portent le code impose`() {
        val rows = CompareCsvBuilder.rows(
            missing = listOf(missing("BAT-002")),
            found = listOf(found("BAT-001")),
        )

        assertEquals("MANQUANT", rows[1][1])
        assertEquals("TROUVE", rows[2][1])
    }

    @Test
    fun `un manquant n a pas de date de validation`() {
        val rows = CompareCsvBuilder.rows(missing = listOf(missing("BAT-002")), found = emptyList())

        assertEquals("", rows[1][3])
    }

    @Test
    fun `un trouve porte sa date au format ISO triable`() {
        val rows = CompareCsvBuilder.rows(missing = emptyList(), found = listOf(found("BAT-001")))

        assertTrue(
            "Format inattendu : " + rows[1][3],
            Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}").matches(rows[1][3]),
        )
    }

    @Test
    fun `un trouve sans horodatage donne une cellule vide et non le mot null`() {
        val rows = CompareCsvBuilder.rows(
            missing = emptyList(),
            found = listOf(found("BAT-001").copy(matchedAt = null)),
        )

        assertEquals("", rows[1][3])
    }

    @Test
    fun `le libelle du csv de reference est conserve`() {
        val rows = CompareCsvBuilder.rows(
            missing = listOf(missing("BAT-002", "Batterie de secours")),
            found = emptyList(),
        )

        assertEquals("Batterie de secours", rows[1][2])
    }

    @Test
    fun `un libelle absent donne une cellule vide`() {
        val rows = CompareCsvBuilder.rows(missing = listOf(missing("BAT-002")), found = emptyList())

        assertEquals("", rows[1][2])
    }

    @Test
    fun `le csv complet porte le BOM et le separateur point-virgule`() {
        val csv = CompareCsvBuilder.build(
            missing = listOf(missing("BAT-002")),
            found = emptyList(),
        )

        assertTrue(csv.startsWith(CsvCodec.BOM))
        assertTrue(csv.contains("valeur;etat;libelle;date_validation"))
        assertTrue(csv.contains("BAT-002;MANQUANT;;"))
    }

    @Test
    fun `une valeur contenant un point-virgule est protegee`() {
        val csv = CompareCsvBuilder.build(missing = listOf(missing("A;B")), found = emptyList())

        assertTrue("Cellule non protegee : " + csv, csv.contains("\"A;B\";"))
    }

    @Test
    fun `un export relu redonne les memes valeurs`() {
        val relu = CsvCodec.parse(
            CompareCsvBuilder.build(
                missing = listOf(missing("A;B"), missing("guillemet \" interne")),
                found = listOf(found("BAT-001", "Rack 3")),
            ),
        )

        assertEquals(CompareCsvBuilder.HEADER, relu.first())
        assertEquals(
            listOf("A;B", "guillemet \" interne", "BAT-001"),
            relu.drop(1).map { it[0] },
        )
        assertEquals("Rack 3", relu.last()[2])
    }

    @Test
    fun `une session sans reference produit un csv reduit a son en-tete`() {
        val relu = CsvCodec.parse(
            CompareCsvBuilder.build(missing = emptyList(), found = emptyList()),
        )

        assertEquals(1, relu.size)
        assertEquals(CompareCsvBuilder.HEADER, relu.first())
    }

    @Test
    fun `toute la reference se retrouve dans le fichier`() {
        // Le .csv de session est le seul document qui porte les manquants : aucune
        // ligne ne doit se perdre entre l'ecran et le fichier.
        val manquants = List(7) { missing("M-" + it) }
        val trouves = List(3) { found("T-" + it) }

        val rows = CompareCsvBuilder.rows(missing = manquants, found = trouves)

        assertEquals(1 + 7 + 3, rows.size)
    }
}
