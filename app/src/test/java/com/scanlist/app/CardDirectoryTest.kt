package com.scanlist.app

import com.scanlist.app.domain.card.BerTlv
import com.scanlist.app.domain.card.CardApplication
import com.scanlist.app.domain.card.CardDirectory
import com.scanlist.app.domain.card.hexBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDirectoryTest {

    private fun application(aid: String, label: ByteArray? = null): ByteArray =
        BerTlv.encode(
            0x61,
            BerTlv.encode(0x4F, hexBytes(aid)) + (label?.let { BerTlv.encode(0x50, it) } ?: ByteArray(0)),
        )

    @Test
    fun `les applications du repertoire sont listees avec leur libelle`() {
        val content = application("A000000063504B43532D3135", "PKCS-15".toByteArray()) +
            application("A0000003080000100001")

        val applications = CardDirectory.parse(content)

        assertEquals(
            listOf(
                CardApplication("A000000063504B43532D3135", "PKCS-15"),
                CardApplication("A0000003080000100001", null),
            ),
            applications,
        )
    }

    @Test
    fun `un modele d application sans AID est ignore`() {
        val sansAid = BerTlv.encode(0x61, BerTlv.encode(0x50, "ORPHELIN".toByteArray()))

        assertTrue(CardDirectory.parse(sansAid).isEmpty())
    }

    @Test
    fun `un libelle est reduit a ses caracteres imprimables`() {
        val label = byteArrayOf(0x41, 0x01, 0x42, 0x20, 0x20)

        assertEquals("AB", CardDirectory.parse(application("A0", label)).single().label)
    }

    @Test
    fun `un libelle sans caractere imprimable devient absent`() {
        assertNull(CardDirectory.parse(application("A0", byteArrayOf(0x01, 0x02))).single().label)
    }

    @Test
    fun `un contenu illisible donne une liste vide sans exception`() {
        assertTrue(CardDirectory.parse(hexBytes("61 10 4F")).isEmpty())
    }
}
