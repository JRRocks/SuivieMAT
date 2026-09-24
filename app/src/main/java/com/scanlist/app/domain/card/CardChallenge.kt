package com.scanlist.app.domain.card

import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

/**
 * Defi envoye a la carte et verification de sa reponse — le coeur du niveau 3.
 *
 * La preuve tient ici : si la signature d'un nonce tire au hasard se verifie avec la cle
 * publique du certificat, la carte presentee detient la cle privee correspondante. Cette
 * cle privee ne quitte jamais la carte ; ni l'application ni l'organisation n'ont besoin
 * de la posseder.
 */
object CardChallenge {

    /** Prefixe DigestInfo de SHA-256 (RFC 8017 § 9.2, note 1). */
    private val SHA256_DIGEST_INFO = hexBytes("3031300D060960864801650304020105000420")

    /**
     * Donnees a faire signer pour [nonce].
     *
     * RSA : la carte PIV applique sa cle privee brute, sans bourrage ; le telephone lui
     * fournit donc un bloc PKCS#1 v1.5 complet, a la taille du module — ce qui fait de la
     * reponse une signature SHA256withRSA ordinaire du nonce. ECC : la carte signe une
     * empreinte deja calculee.
     */
    fun toSign(algorithm: PivAlgorithm, nonce: ByteArray): ByteArray = when (algorithm) {
        PivAlgorithm.RSA_1024,
        PivAlgorithm.RSA_2048,
        PivAlgorithm.RSA_3072,
        -> pkcs1Block(digest("SHA-256", nonce), algorithm.modulusBytes)
        PivAlgorithm.ECC_P256 -> digest("SHA-256", nonce)
        PivAlgorithm.ECC_P384 -> digest("SHA-384", nonce)
    }

    /** Faux pour toute signature invalide ou mal formee, sans exception. */
    fun verify(publicKey: PublicKey, algorithm: PivAlgorithm, nonce: ByteArray, signature: ByteArray): Boolean =
        runCatching {
            Signature.getInstance(algorithm.signatureAlgorithm).run {
                initVerify(publicKey)
                update(nonce)
                verify(signature)
            }
        }.getOrDefault(false)

    private fun pkcs1Block(hash: ByteArray, size: Int): ByteArray {
        val digestInfo = SHA256_DIGEST_INFO + hash
        val padding = size - 3 - digestInfo.size
        return byteArrayOf(0x00, 0x01) +
            ByteArray(padding) { 0xFF.toByte() } +
            byteArrayOf(0x00) +
            digestInfo
    }

    private fun digest(algorithm: String, data: ByteArray): ByteArray =
        MessageDigest.getInstance(algorithm).digest(data)
}
