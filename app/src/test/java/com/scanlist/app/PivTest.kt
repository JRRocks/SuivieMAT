package com.scanlist.app

import com.scanlist.app.domain.card.BerTlv
import com.scanlist.app.domain.card.CardCertificate
import com.scanlist.app.domain.card.Piv
import com.scanlist.app.domain.card.PivAlgorithm
import com.scanlist.app.domain.card.hexBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PivTest {

    private fun pivObject(certificate: ByteArray, info: Int?): ByteArray {
        val infoTlv = info?.let { BerTlv.encode(0x71, byteArrayOf(it.toByte())) } ?: ByteArray(0)
        return BerTlv.encode(0x53, BerTlv.encode(0x70, certificate) + infoTlv + BerTlv.encode(0xFE, ByteArray(0)))
    }

    @Test
    fun `le champ de GET DATA designe l objet demande`() {
        assertArrayEquals(hexBytes("5C03 5FC101"), Piv.getDataField(Piv.OBJECT_CARD_AUTH_CERT))
    }

    @Test
    fun `le certificat est extrait de son enveloppe PIV`() {
        val der = CardFixtures.rsaCertificate

        val certificate = Piv.unwrapCertificate(pivObject(der, info = 0x00))!!

        assertArrayEquals(der, certificate.der)
        assertFalse(certificate.compressed)
    }

    @Test
    fun `une enveloppe sans octet d information est lue comme non compressee`() {
        val der = CardFixtures.rsaCertificate

        assertArrayEquals(der, Piv.unwrapCertificate(pivObject(der, info = null))!!.der)
    }

    @Test
    fun `un certificat compresse est decompresse`() {
        val der = CardFixtures.ecCertificate

        val certificate = Piv.unwrapCertificate(pivObject(CardFixtures.gzip(der), info = 0x01))!!

        assertArrayEquals(der, certificate.der)
        assertTrue(certificate.compressed)
    }

    @Test
    fun `un certificat annonce compresse mais qui ne l est pas est rejete`() {
        assertNull(Piv.unwrapCertificate(pivObject(CardFixtures.rsaCertificate, info = 0x01)))
    }

    @Test
    fun `une enveloppe sans certificat est illisible`() {
        val sansCertificat = BerTlv.encode(0x53, BerTlv.encode(0x71, byteArrayOf(0x00)))

        assertNull(Piv.unwrapCertificate(sansCertificat))
        assertNull(Piv.unwrapCertificate(hexBytes("6A")))
    }

    @Test
    fun `le gabarit d authentification annonce la reponse attendue puis le defi`() {
        assertArrayEquals(hexBytes("7C06 8200 8102 AABB"), Piv.generalAuthenticateField(hexBytes("AABB")))
    }

    @Test
    fun `un defi RSA 2048 passe en longueur longue et fait 266 octets`() {
        val field = Piv.generalAuthenticateField(ByteArray(256))

        assertEquals(266, field.size)
        assertArrayEquals(hexBytes("7C820106 8200 81820100"), field.copyOfRange(0, 10))
    }

    @Test
    fun `la signature est extraite de la reponse de la carte`() {
        val response = BerTlv.encode(0x7C, BerTlv.encode(0x82, hexBytes("0102030405")))

        assertArrayEquals(hexBytes("0102030405"), Piv.parseSignature(response))
    }

    @Test
    fun `une reponse sans signature est illisible`() {
        assertNull(Piv.parseSignature(hexBytes("7C02 8200")))
        assertNull(Piv.parseSignature(hexBytes("9000")))
    }

    @Test
    fun `l algorithme PIV est deduit de la cle du certificat`() {
        val rsa = CardCertificate.parse(CardFixtures.rsaCertificate)!!.publicKey
        val ec = CardCertificate.parse(CardFixtures.ecCertificate)!!.publicKey

        assertEquals(PivAlgorithm.RSA_2048, PivAlgorithm.of(rsa))
        assertEquals(0x07, PivAlgorithm.of(rsa)!!.id)
        assertEquals(PivAlgorithm.ECC_P256, PivAlgorithm.of(ec))
        assertEquals(0x11, PivAlgorithm.of(ec)!!.id)
    }

    @Test
    fun `le resume du certificat donne titulaire, cle et autosignature`() {
        val summary = CardCertificate.parse(CardFixtures.rsaCertificate)!!

        assertTrue(summary.subject, summary.subject.contains("CN=Badge de test RSA"))
        assertEquals("RSA", summary.keyAlgorithm)
        assertEquals(2048, summary.keySizeBits)
        assertTrue(summary.selfSigned)
        assertTrue(summary.notBefore < summary.notAfter)
    }

    @Test
    fun `un contenu qui n est pas un certificat est rejete sans exception`() {
        assertNull(CardCertificate.parse(hexBytes("3003020101")))
    }
}
