package com.scanlist.app.domain.card

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.zip.GZIPInputStream

/**
 * Algorithmes de cle PIV (NIST SP 800-78), deduits de la cle publique du certificat.
 *
 * [modulusBytes] ne concerne que RSA : la carte y applique la cle privee brute, c'est au
 * telephone de lui fournir un bloc deja mis a la taille du module.
 */
enum class PivAlgorithm(
    val id: Int,
    val label: String,
    val signatureAlgorithm: String,
    val modulusBytes: Int,
) {
    RSA_1024(0x06, "RSA 1024", "SHA256withRSA", 128),
    RSA_2048(0x07, "RSA 2048", "SHA256withRSA", 256),
    RSA_3072(0x05, "RSA 3072", "SHA256withRSA", 384),
    ECC_P256(0x11, "ECC P-256", "SHA256withECDSA", 0),
    ECC_P384(0x14, "ECC P-384", "SHA384withECDSA", 0),
    ;

    companion object {
        fun of(key: PublicKey): PivAlgorithm? = when (key) {
            is RSAPublicKey -> when (key.modulus.bitLength()) {
                1024 -> RSA_1024
                2048 -> RSA_2048
                3072 -> RSA_3072
                else -> null
            }
            is ECPublicKey -> when (key.params.curve.field.fieldSize) {
                256 -> ECC_P256
                384 -> ECC_P384
                else -> null
            }
            else -> null
        }
    }
}

/** Certificat extrait d'un objet PIV, en DER. */
class PivCertificate(val der: ByteArray, val compressed: Boolean)

/**
 * Application PIV (NIST SP 800-73-4) : la norme de badge professionnel la plus repandue
 * qui prevoit une authentification de la carte **sans code PIN et sans contact** — celle
 * des lecteurs de porte. C'est ce qui la rend testable sans risque de bloquer le badge.
 */
object Piv {

    /** AID tronque (SP 800-73-4 § 2.2) : accepte quelle que soit la version de l'application. */
    val AID: ByteArray = hexBytes("A0 00 00 03 08 00 00 10 00")

    /** Certificat de la cle d'authentification de la carte : lisible sans contact, sans PIN. */
    const val OBJECT_CARD_AUTH_CERT = 0x5FC101

    /** Certificat d'authentification du titulaire : cle protegee par PIN, souvent reserve au contact. */
    const val OBJECT_PIV_AUTH_CERT = 0x5FC105

    /** Cle d'authentification de la carte (reference 9E) : jamais de PIN, par construction. */
    const val KEY_CARD_AUTH = 0x9E

    const val INS_GET_DATA = 0xCB
    const val INS_GENERAL_AUTHENTICATE = 0x87

    private const val TAG_DATA_OBJECT = 0x53
    private const val TAG_CERTIFICATE = 0x70
    private const val TAG_CERT_INFO = 0x71
    private const val TAG_TAG_LIST = 0x5C
    private const val TAG_DYNAMIC_AUTH = 0x7C
    private const val TAG_CHALLENGE = 0x81
    private const val TAG_RESPONSE = 0x82

    /** Borne de decompression : un certificat reel fait 1 a 3 Ko. */
    private const val MAX_CERTIFICATE_SIZE = 64 * 1024

    /** Champ de donnees de GET DATA : liste d'etiquettes 5C contenant l'objet voulu. */
    fun getDataField(objectTag: Int): ByteArray =
        BerTlv.encode(
            TAG_TAG_LIST,
            byteArrayOf((objectTag ushr 16).toByte(), (objectTag ushr 8).toByte(), objectTag.toByte()),
        )

    /**
     * Extrait le certificat d'un objet PIV : 53 { 70 certificat, 71 info, FE controle }.
     * Bit 0 de l'info = certificat compresse en gzip, frequent sur les cartes a la memoire
     * comptee. Null si l'enveloppe ou la compression est illisible.
     */
    fun unwrapCertificate(objectData: ByteArray): PivCertificate? {
        val container = BerTlv.find(objectData, TAG_DATA_OBJECT) ?: return null
        val inner = BerTlv.parse(container) ?: return null
        val certificate = inner.firstOrNull { it.tag == TAG_CERTIFICATE }?.value ?: return null
        val info = inner.firstOrNull { it.tag == TAG_CERT_INFO }?.value
        val compressed = info != null && info.isNotEmpty() && (info[0].toInt() and 0x01) != 0
        if (!compressed) return PivCertificate(certificate, compressed = false)
        return gunzip(certificate)?.let { PivCertificate(it, compressed = true) }
    }

    /** Gabarit d'authentification dynamique : 7C { 82 vide = reponse attendue, 81 defi }. */
    fun generalAuthenticateField(toSign: ByteArray): ByteArray =
        BerTlv.encode(
            TAG_DYNAMIC_AUTH,
            BerTlv.encode(TAG_RESPONSE, ByteArray(0)) + BerTlv.encode(TAG_CHALLENGE, toSign),
        )

    /** Signature renvoyee par la carte : 7C { 82 signature }. */
    fun parseSignature(response: ByteArray): ByteArray? {
        val template = BerTlv.find(response, TAG_DYNAMIC_AUTH) ?: return null
        return BerTlv.find(template, TAG_RESPONSE)?.takeIf { it.isNotEmpty() }
    }

    private fun gunzip(data: ByteArray): ByteArray? = runCatching {
        GZIPInputStream(ByteArrayInputStream(data)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_CERTIFICATE_SIZE) return null
            }
            out.toByteArray()
        }
    }.getOrNull()
}
