package com.scanlist.app

import com.scanlist.app.data.csv.CsvCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {

    // ------------------------------------------------------------------ Export

    @Test
    fun `encode ajoute le BOM et termine les lignes en CRLF`() {
        val csv = CsvCodec.encode(listOf(listOf("valeur"), listOf("BAT-001")))

        assertTrue(csv.startsWith(CsvCodec.BOM))
        assertEquals(CsvCodec.BOM + "valeur\r\nBAT-001\r\n", csv)
    }

    @Test
    fun `encode protege les champs contenant le separateur`() {
        val csv = CsvCodec.encode(listOf(listOf("a;b", "c")), withBom = false)

        assertEquals("\"a;b\";c\r\n", csv)
    }

    @Test
    fun `encode double les guillemets internes`() {
        assertEquals("\"batterie 12\"\"\"", CsvCodec.escapeField("batterie 12\""))
    }

    @Test
    fun `encode protege les espaces de bord qu un tableur rognerait`() {
        assertEquals("\" BAT-001\"", CsvCodec.escapeField(" BAT-001"))
    }

    // ------------------------------------------------------------------ Import

    @Test
    fun `detectSeparator reconnait la virgule quand elle domine`() {
        assertEquals(',', CsvCodec.detectSeparator("a,b,c\n1,2,3"))
    }

    @Test
    fun `detectSeparator retombe sur le point-virgule par defaut`() {
        assertEquals(';', CsvCodec.detectSeparator("BAT-001\nBAT-002"))
    }

    @Test
    fun `detectSeparator ignore les separateurs proteges par des guillemets`() {
        assertEquals(';', CsvCodec.detectSeparator("\"a,b,c,d\";x\n\"e,f,g,h\";y"))
    }

    @Test
    fun `parse gere un champ multiligne entre guillemets`() {
        val rows = CsvCodec.parse("a;\"ligne1\nligne2\";c\r\n")

        assertEquals(1, rows.size)
        assertEquals(listOf("a", "ligne1\nligne2", "c"), rows[0])
    }

    @Test
    fun `parse ignore les lignes vides`() {
        val rows = CsvCodec.parse("BAT-001\n\n\nBAT-002\n")

        assertEquals(listOf(listOf("BAT-001"), listOf("BAT-002")), rows)
    }

    @Test
    fun `parse retire le BOM en tete de fichier`() {
        val rows = CsvCodec.parse(CsvCodec.BOM + "BAT-001;chargeur\r\n")

        assertEquals(listOf("BAT-001", "chargeur"), rows[0])
    }

    // -------------------------------------------------------------- References

    @Test
    fun `parseReferences saute l en-tete reconnu`() {
        val refs = CsvCodec.parseReferences("valeur;designation\nBAT-001;Batterie A\n")

        assertEquals(1, refs.size)
        assertEquals("BAT-001", refs[0].value)
        assertEquals("Batterie A", refs[0].label)
    }

    @Test
    fun `parseReferences conserve la premiere ligne quand il n y a pas d en-tete`() {
        val refs = CsvCodec.parseReferences("BAT-001\nBAT-002\n")

        assertEquals(listOf("BAT-001", "BAT-002"), refs.map { it.value })
    }

    @Test
    fun `parseReferences ecarte les doublons internes au fichier`() {
        val refs = CsvCodec.parseReferences("BAT-001;A\nBAT-002;B\nBAT-001;C\n")

        assertEquals(2, refs.size)
        // La premiere occurrence gagne : son libelle est conserve.
        assertEquals("A", refs.first { it.value == "BAT-001" }.label)
    }

    @Test
    fun `parseReferences ignore les valeurs vides`() {
        val refs = CsvCodec.parseReferences("BAT-001;A\n;B\n   ;C\n")

        assertEquals(listOf("BAT-001"), refs.map { it.value })
    }

    @Test
    fun `parseReferences accepte l en-tete accentue`() {
        val refs = CsvCodec.parseReferences("Référence;Désignation\nBAT-001;Batterie\n")

        assertEquals(listOf("BAT-001"), refs.map { it.value })
    }

    @Test
    fun `isLikelyHeader ne se declenche pas sur un code reel`() {
        assertFalse(CsvCodec.isLikelyHeader(listOf("BAT-2024-0001")))
        assertTrue(CsvCodec.isLikelyHeader(listOf("valeur", "mode")))
    }

    // ------------------------------------------------------------- Aller-retour

    @Test
    fun `un export relu redonne les memes cellules`() {
        val rows = listOf(
            listOf("valeur", "mode"),
            listOf("BAT;001", "QR_CODE"),
            listOf("texte \"cite\"", "OCR"),
            listOf("multi\nligne", "OCR"),
        )

        assertEquals(rows, CsvCodec.parse(CsvCodec.encode(rows)))
    }
}
