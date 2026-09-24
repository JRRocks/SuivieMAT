package com.scanlist.app

import com.scanlist.app.domain.card.BerTlv
import com.scanlist.app.domain.card.hexBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BerTlvTest {

    @Test
    fun `une etiquette sur un octet et une longueur courte sont decodees`() {
        val tlvs = BerTlv.parse(hexBytes("4F 02 A000"))

        assertNotNull(tlvs)
        assertEquals(1, tlvs!!.size)
        assertEquals(0x4F, tlvs[0].tag)
        assertArrayEquals(hexBytes("A000"), tlvs[0].value)
    }

    @Test
    fun `une etiquette sur plusieurs octets est decodee`() {
        assertEquals(0x5F20, BerTlv.parse(hexBytes("5F20 01 41"))!![0].tag)
        assertEquals(0x5FC101, BerTlv.parse(hexBytes("5FC101 01 41"))!![0].tag)
    }

    @Test
    fun `les longueurs longues 81 et 82 sont decodees`() {
        val bytes = hexBytes("70 81 C8") + ByteArray(200) + hexBytes("71 82 012C") + ByteArray(300)

        val tlvs = BerTlv.parse(bytes)!!

        assertEquals(200, tlvs[0].value.size)
        assertEquals(300, tlvs[1].value.size)
    }

    @Test
    fun `l encodage se relit a l identique quelle que soit la forme de la longueur`() {
        listOf(0, 1, 127, 128, 255, 256, 1000).forEach { size ->
            val value = ByteArray(size) { it.toByte() }

            val tlv = BerTlv.parse(BerTlv.encode(0x5FC101, value))!!.single()

            assertEquals(0x5FC101, tlv.tag)
            assertArrayEquals("taille $size", value, tlv.value)
        }
    }

    @Test
    fun `les octets de bourrage 00 et FF entre deux objets sont ignores`() {
        val tlvs = BerTlv.parse(hexBytes("00 4F01AA FFFF 5001 42 0000"))!!

        assertEquals(listOf(0x4F, 0x50), tlvs.map { it.tag })
    }

    @Test
    fun `une longueur qui depasse les donnees rend la suite illisible sans exception`() {
        assertNull(BerTlv.parse(hexBytes("70 05 0102")))
    }

    @Test
    fun `une etiquette ou une longueur tronquee rend la suite illisible`() {
        assertNull(BerTlv.parse(hexBytes("5F")))
        assertNull(BerTlv.parse(hexBytes("70")))
        assertNull(BerTlv.parse(hexBytes("70 82 01")))
    }

    @Test
    fun `find renvoie la valeur du premier objet demande au premier niveau`() {
        val bytes = BerTlv.encode(0x4F, hexBytes("01")) + BerTlv.encode(0x50, hexBytes("02"))

        assertArrayEquals(hexBytes("02"), BerTlv.find(bytes, 0x50))
        assertNull(BerTlv.find(bytes, 0x61))
    }
}
