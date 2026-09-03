package com.scanlist.app

import com.scanlist.app.export.ExportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNameTest {

    @Test
    fun `un nom simple recoit l extension csv`() {
        assertEquals("inventaire.csv", ExportFileName.sanitize("inventaire"))
    }

    @Test
    fun `l extension n est jamais doublee`() {
        assertEquals("inventaire.csv", ExportFileName.sanitize("inventaire.csv"))
    }

    @Test
    fun `les caracteres interdits par Windows sont retires`() {
        assertEquals("ab.csv", ExportFileName.sanitize("a<>:\"/\\|?*b"))
    }

    @Test
    fun `les espaces et tirets sont conserves`() {
        // Ils composent la plupart des noms de dossiers : les retirer rendrait les
        // exports illisibles.
        assertEquals(
            "Inventaire batteries - Atelier A.csv",
            ExportFileName.sanitize("Inventaire batteries - Atelier A"),
        )
    }

    @Test
    fun `les accents sont conserves`() {
        assertEquals("Controle rack 3.csv", ExportFileName.sanitize("Controle rack 3"))
    }

    @Test
    fun `les espaces multiples sont reduits`() {
        assertEquals("a b.csv", ExportFileName.sanitize("a    b"))
    }

    @Test
    fun `un point final est retire`() {
        // Windows ignore le point final : le fichier deviendrait introuvable.
        assertEquals("inventaire.csv", ExportFileName.sanitize("inventaire..."))
    }

    @Test
    fun `un nom vide retombe sur le nom de secours`() {
        assertEquals(ExportFileName.FALLBACK, ExportFileName.sanitize(""))
        assertEquals(ExportFileName.FALLBACK, ExportFileName.sanitize("   "))
    }

    @Test
    fun `un nom entierement interdit retombe sur le nom de secours`() {
        assertEquals(ExportFileName.FALLBACK, ExportFileName.sanitize("///:::***"))
    }

    @Test
    fun `un nom tres long est tronque sans perdre l extension`() {
        val resultat = ExportFileName.sanitize("a".repeat(300))

        assertTrue(resultat.endsWith(ExportFileName.EXTENSION))
        assertTrue("Nom trop long : " + resultat.length, resultat.length <= 84)
    }

    @Test
    fun `les caracteres de controle sont retires`() {
        // Construits par code de caractere : un NUL ecrit litteralement dans le
        // source rendrait le fichier fragile a manipuler.
        val avecControles = "a" + 0.toChar() + 31.toChar() + "b"

        assertEquals("ab.csv", ExportFileName.sanitize(avecControles))
    }

    @Test
    fun `le nom propose contient le dossier et un horodatage`() {
        val propose = ExportFileName.suggest("Atelier A", at = 1_755_693_000_000L)

        assertTrue(propose.startsWith("Atelier A_"))
        assertTrue(propose.endsWith(".csv"))
        // Le deux-points est interdit dans un nom de fichier : l'heure ne doit pas
        // en introduire un.
        assertFalse(propose.contains(":"))
    }

    @Test
    fun `un nom de dossier avec des caracteres interdits reste exportable`() {
        val propose = ExportFileName.suggest("Rack 3/4 : controle")

        assertFalse(propose.contains("/"))
        assertFalse(propose.contains(":"))
        assertTrue(propose.endsWith(".csv"))
    }
}
