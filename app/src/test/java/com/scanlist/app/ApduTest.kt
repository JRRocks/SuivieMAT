package com.scanlist.app

import com.scanlist.app.domain.card.Apdu
import com.scanlist.app.domain.card.ApduResponse
import com.scanlist.app.domain.card.CardChannel
import com.scanlist.app.domain.card.hexBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApduTest {

    // ------------------------------------------------------------- commandes

    @Test
    fun `une commande sans donnees ni reponse attendue tient en quatre octets`() {
        assertArrayEquals(hexBytes("00A40000"), Apdu.command(0x00, 0xA4, 0x00, 0x00))
    }

    @Test
    fun `une commande avec donnees et reponse attendue porte Lc puis Le`() {
        val command = Apdu.command(0x00, 0xA4, 0x04, 0x00, hexBytes("2F00"), le = 16)

        assertArrayEquals(hexBytes("00A40400 02 2F00 10"), command)
    }

    @Test
    fun `un Le de 256 est encode par l octet zero`() {
        assertArrayEquals(hexBytes("00C00000 00"), Apdu.command(0x00, 0xC0, 0x00, 0x00, le = Apdu.LE_MAX))
    }

    @Test
    fun `une commande courte n est pas chainee`() {
        val links = Apdu.chained(0x00, 0x87, 0x11, 0x9E, ByteArray(40), le = Apdu.LE_MAX)

        assertEquals(1, links.size)
        assertEquals(0x00, links[0][0].toInt())
    }

    @Test
    fun `un defi RSA 2048 de 266 octets est decoupe en deux maillons chaines`() {
        val data = ByteArray(266) { it.toByte() }

        val links = Apdu.chained(0x00, 0x87, 0x07, 0x9E, data, le = Apdu.LE_MAX)

        assertEquals(2, links.size)
        // Premier maillon : bit de chainage leve, 255 octets, pas de Le.
        assertEquals(0x10, links[0][0].toInt())
        assertEquals(255, links[0][4].toInt() and 0xFF)
        assertEquals(5 + 255, links[0].size)
        // Dernier maillon : CLA d'origine, le reste des donnees, puis Le = 00.
        assertEquals(0x00, links[1][0].toInt())
        assertEquals(11, links[1][4].toInt())
        assertEquals(5 + 11 + 1, links[1].size)
        assertEquals(0x00, links[1].last().toInt())
        assertArrayEquals(data, links[0].copyOfRange(5, 260) + links[1].copyOfRange(5, 16))
    }

    // ------------------------------------------------------------- reponses

    @Test
    fun `le mot d etat est lu sur les deux derniers octets`() {
        val response = ApduResponse.parse(hexBytes("0102 6A82"))

        assertArrayEquals(hexBytes("0102"), response.data)
        assertEquals(0x6A82, response.sw)
        assertFalse(response.isSuccess)
    }

    @Test
    fun `une reponse trop courte donne un mot d etat nul sans exception`() {
        val response = ApduResponse.parse(byteArrayOf(0x90.toByte()))

        assertEquals(ApduResponse.SW_NONE, response.sw)
        assertEquals(0, response.data.size)
    }

    // ---------------------------------------------------------------- canal

    @Test
    fun `le canal reassemble une reponse longue annoncee par 61xx`() {
        val payload = ByteArray(600) { it.toByte() }
        val responses = ArrayDeque(
            listOf(
                payload.copyOfRange(0, 256) + hexBytes("6100"),
                payload.copyOfRange(256, 512) + hexBytes("6158"),
                payload.copyOfRange(512, 600) + hexBytes("9000"),
            ),
        )
        val sent = mutableListOf<ByteArray>()
        val channel = CardChannel { command ->
            sent += command
            responses.removeFirst()
        }

        val response = channel.send(0x00, 0xCB, 0x3F, 0xFF, hexBytes("5C035FC101"), le = Apdu.LE_MAX)

        assertTrue(response.isSuccess)
        assertArrayEquals(payload, response.data)
        // SW2 = 00 annonce 256 octets restants, SW2 = 58 en annonce 88 : le Le suit l'annonce.
        assertArrayEquals(hexBytes("00C0000000"), sent[1])
        assertArrayEquals(hexBytes("00C0000058"), sent[2])
    }

    @Test
    fun `le canal s arrete au premier maillon refuse`() {
        val sent = mutableListOf<ByteArray>()
        val channel = CardChannel { command ->
            sent += command
            hexBytes("6982")
        }

        val response = channel.send(0x00, 0x87, 0x07, 0x9E, ByteArray(300), le = Apdu.LE_MAX)

        assertEquals(0x6982, response.sw)
        assertEquals(1, sent.size)
    }

    @Test
    fun `une carte qui annonce des donnees sans fin ne fait pas boucler le canal`() {
        var calls = 0
        val channel = CardChannel {
            calls++
            hexBytes("AA 6101")
        }

        channel.send(0x00, 0xCB, 0x3F, 0xFF, le = Apdu.LE_MAX)

        assertTrue("borne de GET RESPONSE depassee : $calls appels", calls <= 65)
    }
}
