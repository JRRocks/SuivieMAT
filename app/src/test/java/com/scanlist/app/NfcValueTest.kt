package com.scanlist.app

import com.scanlist.app.domain.nfc.NfcValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfcValueTest {

    // ------------------------------------------------------------- resolve

    @Test
    fun `un contenu NDEF texte est prefere a l UID`() {
        val valeur = NfcValue.resolve("BAT-0042", byteArrayOf(0x04, 0x1A.toByte()))

        assertEquals("BAT-0042", valeur)
    }

    @Test
    fun `un contenu NDEF vide ou blanc retombe sur l UID`() {
        assertEquals("041A", NfcValue.resolve("", byteArrayOf(0x04, 0x1A.toByte())))
        assertEquals("041A", NfcValue.resolve("   ", byteArrayOf(0x04, 0x1A.toByte())))
        assertEquals("041A", NfcValue.resolve(null, byteArrayOf(0x04, 0x1A.toByte())))
    }

    @Test
    fun `les espaces de bord du contenu NDEF sont retires`() {
        assertEquals("BAT-0042", NfcValue.resolve("  BAT-0042  ", byteArrayOf(0x00)))
    }

    // ------------------------------------------------------------- formatUid

    @Test
    fun `l UID est forme en hexadecimal majuscule sans separateur`() {
        val uid = byteArrayOf(0x04, 0x1A.toByte(), 0xFF.toByte(), 0x00)

        assertEquals("041AFF00", NfcValue.formatUid(uid))
    }

    @Test
    fun `un octet nul est complete d un zero de tete`() {
        // "%02X" et non "%X" : un octet 0x04 doit rendre "04", pas "4" — sinon deux
        // UID differents pourraient produire la meme chaine une fois concatenes.
        assertEquals("00", NfcValue.formatUid(byteArrayOf(0x00)))
    }

    // ------------------------------------------------------- decodeTextRecord

    @Test
    fun `un enregistrement texte UTF-8 en francais est decode`() {
        // Header 0x02 : langue sur 2 octets ("fr"), encodage UTF-8 (bit 7 a 0).
        val payload = byteArrayOf(0x02) + "fr".toByteArray(Charsets.UTF_8) +
            "Rack A - travée 2".toByteArray(Charsets.UTF_8)

        assertEquals("Rack A - travée 2", NfcValue.decodeTextRecord(payload))
    }

    @Test
    fun `un enregistrement texte UTF-16 est decode`() {
        // Bit 7 du header a 1 : encodage UTF-16.
        val header = (0x02 or 0x80).toByte()
        val payload = byteArrayOf(header) + "fr".toByteArray(Charsets.UTF_8) +
            "BAT-0007".toByteArray(Charsets.UTF_16)

        assertEquals("BAT-0007", NfcValue.decodeTextRecord(payload))
    }

    @Test
    fun `un payload vide ne produit aucune exception`() {
        assertNull(NfcValue.decodeTextRecord(ByteArray(0)))
    }

    @Test
    fun `une longueur de langue superieure au payload est ignoree sans exception`() {
        // Header annonce 60 octets de code langue pour un payload de 3 octets au total.
        val payload = byteArrayOf(60, 0x41, 0x42)

        assertNull(NfcValue.decodeTextRecord(payload))
    }

    @Test
    fun `un texte apres le code langue vide donne une chaine vide et non un manquant`() {
        // Header 0x02 ("fr"), aucun octet de texte ensuite : ce n'est pas un payload
        // corrompu, juste un enregistrement sans contenu — traite comme absent.
        val payload = byteArrayOf(0x02) + "fr".toByteArray(Charsets.UTF_8)

        assertNull(NfcValue.decodeTextRecord(payload))
    }
}
