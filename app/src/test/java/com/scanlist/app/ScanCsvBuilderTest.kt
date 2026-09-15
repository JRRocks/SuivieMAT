package com.scanlist.app

import com.scanlist.app.data.csv.CsvCodec
import com.scanlist.app.data.csv.ScanCsvBuilder
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.domain.model.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCsvBuilderTest {

    private fun item(
        value: String,
        mode: ScanMode = ScanMode.QR_CODE,
        symbology: String? = "QR_CODE",
        scannedAt: Long,
    ) = ScanItemEntity(
        id = 0,
        folderId = 1,
        value = value,
        rawValue = value,
        mode = mode,
        symbology = symbology,
        scannedAt = scannedAt,
    )

    @Test
    fun `la premiere ligne est l en-tete attendu`() {
        val rows = ScanCsvBuilder.rows(emptyList())

        assertEquals(listOf(ScanCsvBuilder.HEADER), rows)
        assertEquals(listOf("valeur", "mode", "symbologie", "date_scan"), rows.first())
    }

    @Test
    fun `les lignes sortent dans l ordre chronologique`() {
        // L'ecran de scan affiche l'inverse, mais un inventaire se relit dans l'ordre
        // ou il a ete releve.
        val rows = ScanCsvBuilder.rows(
            listOf(
                item("BAT-3", scannedAt = 3_000),
                item("BAT-1", scannedAt = 1_000),
                item("BAT-2", scannedAt = 2_000),
            ),
        )

        assertEquals(listOf("BAT-1", "BAT-2", "BAT-3"), rows.drop(1).map { it[0] })
    }

    @Test
    fun `le mode est exporte sous son libelle lisible`() {
        val rows = ScanCsvBuilder.rows(
            listOf(item("A", mode = ScanMode.BARCODE, symbology = "EAN_13", scannedAt = 1)),
        )

        assertEquals("Code-barres", rows[1][1])
        assertEquals("EAN_13", rows[1][2])
    }

    @Test
    fun `une symbologie absente donne une cellule vide et non le mot null`() {
        val rows = ScanCsvBuilder.rows(
            listOf(item("A", mode = ScanMode.OCR, symbology = null, scannedAt = 1)),
        )

        assertEquals("", rows[1][2])
    }

    @Test
    fun `la date est au format ISO triable`() {
        val rows = ScanCsvBuilder.rows(listOf(item("A", scannedAt = 1_755_693_000_000L)))

        assertTrue(
            "Format inattendu : " + rows[1][3],
            Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}").matches(rows[1][3]),
        )
    }

    @Test
    fun `le csv complet porte le BOM et le separateur point-virgule`() {
        val csv = ScanCsvBuilder.build(listOf(item("BAT-1", scannedAt = 1)))

        assertTrue(csv.startsWith(CsvCodec.BOM))
        assertTrue(csv.contains("valeur;mode;symbologie;date_scan"))
        // La colonne "mode" porte le libelle du mode de scan, devenu "Code 2D" avec la
        // decision 21 : un export anterieur au 2026-09-15 dit "QR Code" a cet endroit.
        // La colonne "symbologie" reste, elle, QR_CODE : c'est elle qui identifie le
        // format lu, donc deux exports de part et d'autre du renommage restent
        // rapprochables sans perte d'information.
        assertTrue(csv.contains("BAT-1;Code 2D;QR_CODE;"))
    }

    @Test
    fun `une valeur contenant un point-virgule est protegee`() {
        val csv = ScanCsvBuilder.build(listOf(item("A;B", scannedAt = 1)))

        assertTrue("Cellule non protegee : " + csv, csv.contains("\"A;B\";"))
    }

    @Test
    fun `un export relu redonne les memes valeurs`() {
        val items = listOf(
            item("BAT-1", scannedAt = 1_000),
            item("A;B", scannedAt = 2_000),
            item("guillemet \" interne", mode = ScanMode.OCR, symbology = null, scannedAt = 3_000),
        )

        val relu = CsvCodec.parse(ScanCsvBuilder.build(items))

        assertEquals(ScanCsvBuilder.HEADER, relu.first())
        assertEquals(
            listOf("BAT-1", "A;B", "guillemet \" interne"),
            relu.drop(1).map { it[0] },
        )
    }

    @Test
    fun `un dossier vide produit un csv reduit a son en-tete`() {
        val relu = CsvCodec.parse(ScanCsvBuilder.build(emptyList()))

        assertEquals(1, relu.size)
        assertEquals(ScanCsvBuilder.HEADER, relu.first())
    }
}
