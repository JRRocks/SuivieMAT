package com.scanlist.app

import com.scanlist.app.domain.card.BerTlv
import com.scanlist.app.domain.card.Cplc
import com.scanlist.app.domain.card.DesfireVersion
import com.scanlist.app.domain.card.hexBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardIdentificationTest {

    private val logiciel = hexBytes("04 01 01 02 00 1A 05")

    // ------------------------------------------------------------- DESFire

    @Test
    fun `les generations DESFire connues sont nommees`() {
        mapOf(0x00 to "D40", 0x01 to "EV1", 0x12 to "EV2", 0x33 to "EV3").forEach { (major, nom) ->
            val materiel = byteArrayOf(0x04, 0x01, 0x01, major.toByte(), 0x00, 0x18, 0x05)

            assertEquals("MIFARE DESFire $nom", DesfireVersion.parse(materiel, logiciel)!!.productName)
        }
    }

    @Test
    fun `une generation DESFire inconnue garde le nom de la famille`() {
        val materiel = hexBytes("04 01 01 42 00 18 05")

        assertEquals("MIFARE DESFire", DesfireVersion.parse(materiel, logiciel)!!.productName)
    }

    @Test
    fun `une puce d un autre fabricant n est pas nommee`() {
        val materiel = hexBytes("05 01 01 12 00 18 05")

        assertNull(DesfireVersion.parse(materiel, logiciel)!!.productName)
    }

    @Test
    fun `la taille memoire se deduit de l octet de taille`() {
        assertEquals(4096, DesfireVersion.parse(hexBytes("04 01 01 01 00 18 05"), logiciel)!!.storageBytes)
        assertEquals(8192, DesfireVersion.parse(hexBytes("04 01 01 01 00 1A 05"), logiciel)!!.storageBytes)
    }

    @Test
    fun `une version DESFire tronquee est rejetee sans exception`() {
        assertNull(DesfireVersion.parse(hexBytes("04 01"), logiciel))
    }

    // ---------------------------------------------------------------- CPLC

    @Test
    fun `le CPLC est lu avec ou sans son etiquette 9F7F`() {
        val cplc = hexBytes("4090 7897 8211 2345") + ByteArray(34)

        assertEquals("Infineon", Cplc.parse(BerTlv.encode(0x9F7F, cplc))!!.fabricatorName)
        assertEquals(0x8211, Cplc.parse(cplc)!!.osId)
    }

    @Test
    fun `un fabricant inconnu garde son code brut`() {
        val cplc = Cplc.parse(hexBytes("1234 0000 0000 0000") + ByteArray(34))!!

        assertNull(cplc.fabricatorName)
        assertEquals(0x1234, cplc.icFabricator)
    }

    @Test
    fun `une reponse qui n est pas un CPLC est rejetee`() {
        assertNull(Cplc.parse(hexBytes("0102")))
    }
}
