package com.scanlist.app.domain.card

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.PublicKey
import java.security.SecureRandom

sealed interface DirectoryResult {
    data class Listed(val applications: List<CardApplication>) : DirectoryResult

    /** Pas de repertoire EF.DIR : courant sur les cartes Java Card, ce n'est pas une erreur. */
    data class Absent(val sw: Int) : DirectoryResult

    data object NotTested : DirectoryResult
}

/** Niveau 2 : lecture d'un certificat. */
sealed interface CertificateResult {
    data class Read(val summary: CertificateSummary, val compressed: Boolean) : CertificateResult

    data class Refused(val sw: Int) : CertificateResult

    data object Unreadable : CertificateResult

    data object NotTested : CertificateResult
}

/** Niveau 3 : defi-reponse. */
sealed interface ChallengeResult {
    data class Verified(val algorithm: PivAlgorithm, val durationMs: Long) : ChallengeResult

    /** La carte a signe, mais pas avec la cle du certificat lu. */
    data class Rejected(val algorithm: PivAlgorithm, val durationMs: Long) : ChallengeResult

    data class Refused(val sw: Int) : ChallengeResult

    data object UnsupportedKey : ChallengeResult

    data object Unreadable : ChallengeResult

    data object NotTested : ChallengeResult
}

/**
 * Rapport complet. [pivPresent] et [pkcs15Present] valent null quand l'etape n'a pas pu
 * etre atteinte (badge retire avant).
 */
data class CardReport(
    val directory: DirectoryResult,
    val pivPresent: Boolean?,
    val pkcs15Present: Boolean?,
    val cardAuthCertificate: CertificateResult,
    val challenge: ChallengeResult,
    val pivAuthCertificate: CertificateResult,
    val interrupted: Boolean,
    val durationMs: Long,
)

/**
 * Diagnostic d'un badge sans contact : niveaux 2 (certificat) et 3 (defi-reponse).
 *
 * **Aucune commande de verification de code PIN n'est jamais envoyee** : une serie de
 * PIN faux bloque un badge, et un badge professionnel bloque ne se debloque qu'aupres
 * de son emetteur. Le niveau 3 n'utilise donc que la cle d'authentification de la carte
 * (PIV 9E), concue pour repondre sans PIN ; une carte qui exige un PIN pour signer est
 * simplement signalee comme telle.
 */
class CardDiagnostic(
    transport: CardTransport,
    private val nonce: () -> ByteArray = { ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes) },
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private val channel = CardChannel(transport)

    /**
     * Le repertoire d'abord : il se lit par identifiant de fichier, ce que la selection
     * d'une application rendrait impossible ensuite sur certaines cartes. Si le badge est
     * retire en cours de route, tout ce qui a ete obtenu jusque-la reste dans le rapport.
     */
    fun run(): CardReport {
        val start = clockMs()
        var directory: DirectoryResult = DirectoryResult.NotTested
        var pivPresent: Boolean? = null
        var pkcs15Present: Boolean? = null
        var cardAuth: CertificateResult = CertificateResult.NotTested
        var challenge: ChallengeResult = ChallengeResult.NotTested
        var pivAuth: CertificateResult = CertificateResult.NotTested

        val interrupted = try {
            directory = readDirectory()
            val piv = select(Piv.AID).isSuccess
            pivPresent = piv
            if (piv) {
                val certificate = readCertificate(Piv.OBJECT_CARD_AUTH_CERT)
                cardAuth = certificate
                if (certificate is CertificateResult.Read) {
                    challenge = authenticate(certificate.summary.publicKey)
                }
                pivAuth = readCertificate(Piv.OBJECT_PIV_AUTH_CERT)
            }
            pkcs15Present = select(PKCS15_AID).isSuccess
            false
        } catch (e: IOException) {
            true
        }

        return CardReport(
            directory = directory,
            pivPresent = pivPresent,
            pkcs15Present = pkcs15Present,
            cardAuthCertificate = cardAuth,
            challenge = challenge,
            pivAuthCertificate = pivAuth,
            interrupted = interrupted,
            durationMs = clockMs() - start,
        )
    }

    private fun readDirectory(): DirectoryResult {
        val selected = channel.send(0x00, INS_SELECT, P1_SELECT_BY_FID, P2_NO_RESPONSE_DATA, EF_DIR)
        if (!selected.isSuccess) return DirectoryResult.Absent(selected.sw)

        val content = ByteArrayOutputStream()
        for (record in 1..MAX_DIRECTORY_RECORDS) {
            val read = channel.send(0x00, INS_READ_RECORD, record, P2_RECORD_NUMBER, le = Apdu.LE_MAX)
            if (record == 1 && read.sw == ApduResponse.SW_INCOMPATIBLE_FILE_STRUCTURE) {
                // EF.DIR transparent plutot qu'a enregistrements : les deux formes existent.
                val binary = channel.send(0x00, INS_READ_BINARY, 0x00, 0x00, le = Apdu.LE_MAX)
                if (binary.isSuccess) content.write(binary.data)
                break
            }
            if (!read.isSuccess) break
            content.write(read.data)
        }
        return DirectoryResult.Listed(CardDirectory.parse(content.toByteArray()))
    }

    private fun select(aid: ByteArray): ApduResponse =
        channel.send(0x00, INS_SELECT, P1_SELECT_BY_AID, 0x00, aid, le = Apdu.LE_MAX)

    private fun readCertificate(objectTag: Int): CertificateResult {
        val response = channel.send(
            cla = 0x00,
            ins = Piv.INS_GET_DATA,
            p1 = 0x3F,
            p2 = 0xFF,
            data = Piv.getDataField(objectTag),
            le = Apdu.LE_MAX,
        )
        if (!response.isSuccess) return CertificateResult.Refused(response.sw)
        val certificate = Piv.unwrapCertificate(response.data) ?: return CertificateResult.Unreadable
        val summary = CardCertificate.parse(certificate.der) ?: return CertificateResult.Unreadable
        return CertificateResult.Read(summary, certificate.compressed)
    }

    private fun authenticate(publicKey: PublicKey): ChallengeResult {
        val algorithm = PivAlgorithm.of(publicKey) ?: return ChallengeResult.UnsupportedKey
        val nonce = nonce()
        val started = clockMs()
        val response = channel.send(
            cla = 0x00,
            ins = Piv.INS_GENERAL_AUTHENTICATE,
            p1 = algorithm.id,
            p2 = Piv.KEY_CARD_AUTH,
            data = Piv.generalAuthenticateField(CardChallenge.toSign(algorithm, nonce)),
            le = Apdu.LE_MAX,
        )
        val duration = clockMs() - started
        if (!response.isSuccess) return ChallengeResult.Refused(response.sw)
        val signature = Piv.parseSignature(response.data) ?: return ChallengeResult.Unreadable
        return if (CardChallenge.verify(publicKey, algorithm, nonce, signature)) {
            ChallengeResult.Verified(algorithm, duration)
        } else {
            ChallengeResult.Rejected(algorithm, duration)
        }
    }

    companion object {
        /** 32 octets : impossible a deviner, donc impossible a rejouer depuis une reponse enregistree. */
        const val NONCE_SIZE = 32

        /** Application PKCS#15 (ISO 7816-15), autre norme courante de carte a certificats. */
        val PKCS15_AID: ByteArray = hexBytes("A0 00 00 00 63 50 4B 43 53 2D 31 35")

        private val EF_DIR = hexBytes("2F 00")

        private const val INS_SELECT = 0xA4
        private const val INS_READ_RECORD = 0xB2
        private const val INS_READ_BINARY = 0xB0
        private const val P1_SELECT_BY_FID = 0x00
        private const val P1_SELECT_BY_AID = 0x04
        private const val P2_NO_RESPONSE_DATA = 0x0C
        private const val P2_RECORD_NUMBER = 0x04
        private const val MAX_DIRECTORY_RECORDS = 16
    }
}
