package com.scanlist.app.domain.card

import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.security.auth.x500.X500Principal

/** Ce qu'un certificat X.509 lu sur la carte donne a voir, plus sa cle publique. */
class CertificateSummary(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBefore: Long,
    val notAfter: Long,
    val keyAlgorithm: String,
    val keySizeBits: Int,
    val selfSigned: Boolean,
    val publicKey: PublicKey,
)

object CardCertificate {

    /** Null si le contenu n'est pas un certificat X.509 DER valide. */
    fun parse(der: ByteArray): CertificateSummary? = runCatching {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        CertificateSummary(
            subject = certificate.subjectX500Principal.getName(X500Principal.RFC2253),
            issuer = certificate.issuerX500Principal.getName(X500Principal.RFC2253),
            serialNumber = certificate.serialNumber.toString(16).uppercase(),
            notBefore = certificate.notBefore.time,
            notAfter = certificate.notAfter.time,
            keyAlgorithm = certificate.publicKey.algorithm,
            keySizeBits = keySize(certificate.publicKey),
            // Egalite des noms seulement : suffit a signaler un certificat de test ou
            // emis hors de toute autorite, sans pretendre verifier une chaine de confiance.
            selfSigned = certificate.subjectX500Principal == certificate.issuerX500Principal,
            publicKey = certificate.publicKey,
        )
    }.getOrNull()

    fun keySize(key: PublicKey): Int = when (key) {
        is RSAPublicKey -> key.modulus.bitLength()
        is ECPublicKey -> key.params.curve.field.fieldSize
        else -> 0
    }
}
