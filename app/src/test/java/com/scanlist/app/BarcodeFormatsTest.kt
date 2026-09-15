package com.scanlist.app

import com.google.mlkit.vision.barcode.common.Barcode
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.scanner.BarcodeFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le decodage reel n'est exercable ni sur emulateur ni en integration continue : ces
 * tests sont le seul filet qui verrouille la table des symbologies. Un format retire
 * par megarde ne se verrait, sinon, qu'au premier inventaire rate sur le terrain.
 */
class BarcodeFormatsTest {

    private fun couvre(masque: Int, format: Int) = masque and format == format

    // ------------------------------------------------------------- mode Code 2D

    @Test
    fun `le mode Code 2D couvre les quatre familles 2D`() {
        val masque = BarcodeFormats.forMode(ScanMode.QR_CODE)

        assertTrue("QR Code", couvre(masque, Barcode.FORMAT_QR_CODE))
        assertTrue("PDF417", couvre(masque, Barcode.FORMAT_PDF417))
        assertTrue("Data Matrix", couvre(masque, Barcode.FORMAT_DATA_MATRIX))
        assertTrue("Aztec", couvre(masque, Barcode.FORMAT_AZTEC))
    }

    @Test
    fun `le PDF417 est le seul code empile que le mode Code 2D sait lire`() {
        // Codablock F, Code 16K et Code 49 sont aussi des codes multi-rangees, mais
        // ML Kit ne les expose pas : aucune constante ne leur correspond. Ce test
        // documente la limite autant qu'il verifie la presence du PDF417.
        assertTrue(couvre(BarcodeFormats.TWO_D, Barcode.FORMAT_PDF417))
    }

    // --------------------------------------------------------- mode Code-barres

    @Test
    fun `le mode Code-barres conserve les neuf symbologies lineaires`() {
        val masque = BarcodeFormats.forMode(ScanMode.BARCODE)

        listOf(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODABAR,
        ).forEach { format ->
            assertTrue("format $format", couvre(masque, format))
        }
    }

    @Test
    fun `le mode Code-barres ne decode aucun code 2D`() {
        val masque = BarcodeFormats.forMode(ScanMode.BARCODE)

        assertEquals(0, masque and Barcode.FORMAT_PDF417)
        assertEquals(0, masque and Barcode.FORMAT_QR_CODE)
        assertEquals(0, masque and Barcode.FORMAT_DATA_MATRIX)
        assertEquals(0, masque and Barcode.FORMAT_AZTEC)
    }

    /**
     * Le coeur de l'exigence anti-erreur : une etiquette portant a la fois un Code 128
     * et un PDF417 ne doit jamais produire deux lignes pour un seul objet. C'est la
     * disjonction des deux masques qui l'empeche, pas un filtrage apres coup.
     */
    @Test
    fun `les deux modes optiques sont rigoureusement disjoints`() {
        assertEquals(0, BarcodeFormats.ONE_D and BarcodeFormats.TWO_D)
    }

    // ------------------------------------------------------------ symbologyName

    @Test
    fun `chaque symbologie 2D porte son nom dans l export`() {
        assertEquals("QR_CODE", BarcodeFormats.symbologyName(Barcode.FORMAT_QR_CODE))
        assertEquals("PDF417", BarcodeFormats.symbologyName(Barcode.FORMAT_PDF417))
        assertEquals("DATA_MATRIX", BarcodeFormats.symbologyName(Barcode.FORMAT_DATA_MATRIX))
        assertEquals("AZTEC", BarcodeFormats.symbologyName(Barcode.FORMAT_AZTEC))
    }

    @Test
    fun `les symbologies lineaires gardent le nom deja exporte`() {
        // Non-regression : ces libelles sont deja presents dans des .csv envoyes et
        // dans des dossiers scannes. Les renommer rendrait deux exports incomparables.
        assertEquals("EAN_13", BarcodeFormats.symbologyName(Barcode.FORMAT_EAN_13))
        assertEquals("CODE_128", BarcodeFormats.symbologyName(Barcode.FORMAT_CODE_128))
        assertEquals("CODE_39", BarcodeFormats.symbologyName(Barcode.FORMAT_CODE_39))
        assertEquals("ITF", BarcodeFormats.symbologyName(Barcode.FORMAT_ITF))
        assertEquals("CODABAR", BarcodeFormats.symbologyName(Barcode.FORMAT_CODABAR))
    }

    @Test
    fun `un format inconnu est nomme AUTRE plutot que laisse vide`() {
        assertEquals("AUTRE", BarcodeFormats.symbologyName(Barcode.FORMAT_UNKNOWN))
    }
}
