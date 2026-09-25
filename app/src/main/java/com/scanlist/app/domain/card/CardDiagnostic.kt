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
 * Rapport complet. [pivPresent] vaut null quand les sondes d'applications n'ont pas pu
 * etre menees jusqu'au bout (badge retire avant) ; [applications] garde alors celles qui
 * ont eu lieu.
 */
data class CardReport(
    val directory: DirectoryResult,
    val applications: List<ApplicationProbe>,
    val pivPresent: Boolean?,
    val cardAuthCertificate: CertificateResult,
    val challenge: ChallengeResult,
    val pivAuthCertificate: CertificateResult,
    val globalPlatform: GlobalPlatformResult,
    val desfire: DesfireResult,
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
     * d'une application rendrait impossible ensuite sur certaines cartes. Les commandes
     * natives DESFire en dernier : elles sortent du cadre ISO 7816-4 que suivent toutes les
     * autres. Si le badge est retire en cours de route, tout ce qui a ete obtenu jusque-la
     * reste dans le rapport.
     */
    fun run(): CardReport {
        val start = clockMs()
        var directory: DirectoryResult = DirectoryResult.NotTested
        val applications = mutableListOf<ApplicationProbe>()
        var pivPresent: Boolean? = null
        var cardAuth: CertificateResult = CertificateResult.NotTested
        var challenge: ChallengeResult = ChallengeResult.NotTested
        var pivAuth: CertificateResult = CertificateResult.NotTested
        var globalPlatform: GlobalPlatformResult = GlobalPlatformResult.NotTested
        var desfire: DesfireResult = DesfireResult.NotTested

        val interrupted = try {
            directory = readDirectory()
            KnownApplications.ALL.forEach { application ->
                applications += ApplicationProbe(application.name, application.aid.toHex(), select(application.aid).sw)
            }
            val piv = applications.any { it.name == KnownApplications.PIV.name && it.present }
            pivPresent = piv
            if (piv) {
                // Nouvelle selection : les sondes suivantes ont change l'application courante.
                select(Piv.AID)
                val certificate = readCertificate(Piv.OBJECT_CARD_AUTH_CERT)
                cardAuth = certificate
                if (certificate is CertificateResult.Read) {
                    challenge = authenticate(certificate.summary.publicKey)
                }
                pivAuth = readCertificate(Piv.OBJECT_PIV_AUTH_CERT)
            }
            globalPlatform = probeGlobalPlatform()
            desfire = probeDesfire()
            false
        } catch (e: IOException) {
            true
        }

        return CardReport(
            directory = directory,
            applications = applications,
            pivPresent = pivPresent,
            cardAuthCertificate = cardAuth,
            challenge = challenge,
            pivAuthCertificate = pivAuth,
            globalPlatform = globalPlatform,
            desfire = desfire,
            interrupted = interrupted,
            durationMs = clockMs() - start,
        )
    }

    /**
     * SELECT sans AID : sur une carte GlobalPlatform, il selectionne le domaine de securite
     * de l'emetteur, qui repond au GET DATA du CPLC sans authentification.
     */
    private fun probeGlobalPlatform(): GlobalPlatformResult {
        val implicit = channel.send(0x00, INS_SELECT, P1_SELECT_BY_AID, 0x00, le = Apdu.LE_MAX)
        // Une carte durcie peut refuser la selection implicite (AID vide) par prudence :
        // on retente alors avec les AID de domaine de securite par defaut les plus repandus,
        // plutot que de conclure trop vite a l'absence de GlobalPlatform.
        val selected = if (implicit.isSuccess) {
            implicit
        } else {
            KNOWN_ISD_AIDS.firstNotNullOfOrNull { aid ->
                channel.send(0x00, INS_SELECT, P1_SELECT_BY_AID, 0x00, aid, le = Apdu.LE_MAX)
                    .takeIf { it.isSuccess }
            } ?: implicit
        }
        val isdAid = if (selected.isSuccess) {
            BerTlv.find(selected.data, TAG_FCI)?.let { BerTlv.find(it, TAG_DF_NAME) }?.toHex()
        } else {
            null
        }
        val cplcResponse = channel.send(CLA_GLOBAL_PLATFORM, INS_GET_DATA, 0x9F, 0x7F, le = Apdu.LE_MAX)
        val cplc = if (cplcResponse.isSuccess) Cplc.parse(cplcResponse.data) else null
        if (isdAid == null && cplc == null) {
            return GlobalPlatformResult.Absent(if (selected.isSuccess) cplcResponse.sw else selected.sw)
        }
        return GlobalPlatformResult.Found(isdAid, cplc)
    }

    /**
     * GetVersion natif DESFire, enveloppe ISO (CLA 90). La reponse arrive en trois trames
     * (91AF = suite) ; la troisieme, qui porte l'UID, est lue pour clore la commande puis
     * ignoree.
     */
    private fun probeDesfire(): DesfireResult {
        val hardware = channel.send(CLA_DESFIRE, INS_DESFIRE_GET_VERSION, 0x00, 0x00, le = Apdu.LE_MAX)
        if (hardware.sw != SW_DESFIRE_MORE) return DesfireResult.Absent(hardware.sw)
        val software = channel.send(CLA_DESFIRE, INS_DESFIRE_MORE, 0x00, 0x00, le = Apdu.LE_MAX)
        if (software.sw != SW_DESFIRE_MORE) return DesfireResult.Absent(software.sw)
        val production = channel.send(CLA_DESFIRE, INS_DESFIRE_MORE, 0x00, 0x00, le = Apdu.LE_MAX)
        if (production.sw != SW_DESFIRE_OK) return DesfireResult.Absent(production.sw)
        val version = DesfireVersion.parse(hardware.data, software.data)
            ?: return DesfireResult.Absent(production.sw)
        return DesfireResult.Found(version)
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

        private val EF_DIR = hexBytes("2F 00")

        private const val TAG_FCI = 0x6F
        private const val TAG_DF_NAME = 0x84
        private const val CLA_GLOBAL_PLATFORM = 0x80

        /**
         * AID de domaine de securite par defaut les plus repandus, essayes si la selection
         * implicite (AID vide) est refusee. A000000151000000 : ISD de reference
         * GlobalPlatform, tres largement utilise (JCOP, GlobalPlatformPro...).
         * A000000003000000 : forme plus ancienne, encore rencontree sur des cartes anterieures
         * a GlobalPlatform 2.1.1.
         */
        private val KNOWN_ISD_AIDS: List<ByteArray> = listOf(
            hexBytes("A0 00 00 01 51 00 00 00"),
            hexBytes("A0 00 00 00 03 00 00 00"),
        )
        private const val INS_GET_DATA = 0xCA
        private const val CLA_DESFIRE = 0x90
        private const val INS_DESFIRE_GET_VERSION = 0x60
        private const val INS_DESFIRE_MORE = 0xAF
        private const val SW_DESFIRE_MORE = 0x91AF
        private const val SW_DESFIRE_OK = 0x9100

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
