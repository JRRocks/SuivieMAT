package com.scanlist.app

import com.scanlist.app.domain.card.CardCertificate
import com.scanlist.app.domain.card.CardChallenge
import com.scanlist.app.domain.card.PivAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.PrivateKey
import java.security.Signature
import javax.crypto.Cipher

class CardChallengeTest {

    private val nonce = ByteArray(32) { it.toByte() }

    private val rsaPublicKey = CardCertificate.parse(CardFixtures.rsaCertificate)!!.publicKey
    private val ecPublicKey = CardCertificate.parse(CardFixtures.ecCertificate)!!.publicKey

    /** Ce que fait une carte PIV RSA : cle privee appliquee brute au bloc recu. */
    private fun rawRsa(key: PrivateKey, block: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key)
            doFinal(block)
        }

    /** Ce que fait une carte PIV ECC : signature de l'empreinte recue, sans la recalculer. */
    private fun rawEcdsa(key: PrivateKey, hash: ByteArray): ByteArray =
        Signature.getInstance("NONEwithECDSA").run {
            initSign(key)
            update(hash)
            sign()
        }

    @Test
    fun `le bloc RSA a la taille du module et le bourrage PKCS1 v1_5`() {
        val block = CardChallenge.toSign(PivAlgorithm.RSA_2048, nonce)

        assertEquals(256, block.size)
        assertEquals(0x00, block[0].toInt())
        assertEquals(0x01, block[1].toInt())
        assertEquals(0xFF.toByte(), block[2])
        // DigestInfo SHA-256 (19 octets) + empreinte (32) en fin de bloc, precedes d'un 00.
        assertEquals(0x00, block[256 - 51 - 1].toInt())
        assertEquals(0xFF.toByte(), block[256 - 51 - 2])
    }

    @Test
    fun `une empreinte ECC fait la taille de la courbe`() {
        assertEquals(32, CardChallenge.toSign(PivAlgorithm.ECC_P256, nonce).size)
        assertEquals(48, CardChallenge.toSign(PivAlgorithm.ECC_P384, nonce).size)
    }

    @Test
    fun `la signature brute d une carte RSA est reconnue valide`() {
        val signature = rawRsa(CardFixtures.rsaPrivateKey, CardChallenge.toSign(PivAlgorithm.RSA_2048, nonce))

        assertTrue(CardChallenge.verify(rsaPublicKey, PivAlgorithm.RSA_2048, nonce, signature))
    }

    @Test
    fun `la signature d une carte ECC est reconnue valide`() {
        val signature = rawEcdsa(CardFixtures.ecPrivateKey, CardChallenge.toSign(PivAlgorithm.ECC_P256, nonce))

        assertTrue(CardChallenge.verify(ecPublicKey, PivAlgorithm.ECC_P256, nonce, signature))
    }

    @Test
    fun `une signature produite pour un autre nonce est rejetee`() {
        val autre = ByteArray(32) { 7 }
        val signature = rawRsa(CardFixtures.rsaPrivateKey, CardChallenge.toSign(PivAlgorithm.RSA_2048, autre))

        assertFalse(CardChallenge.verify(rsaPublicKey, PivAlgorithm.RSA_2048, nonce, signature))
    }

    @Test
    fun `une signature corrompue est rejetee sans exception`() {
        assertFalse(CardChallenge.verify(rsaPublicKey, PivAlgorithm.RSA_2048, nonce, byteArrayOf(1, 2, 3)))
        assertFalse(CardChallenge.verify(ecPublicKey, PivAlgorithm.ECC_P256, nonce, byteArrayOf(1, 2, 3)))
    }
}
