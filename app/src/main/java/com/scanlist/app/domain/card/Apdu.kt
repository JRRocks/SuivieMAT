package com.scanlist.app.domain.card

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Transport brut vers une carte a microprocesseur.
 *
 * Interface et non `IsoDep` directement : tout le dialogue (chainage, reponses longues,
 * enchainement des etapes) reste ainsi testable en JVM pur contre une carte simulee.
 * L'emulateur n'ayant pas de puce NFC, c'est le seul moyen de le verifier avant le test
 * physique du badge.
 */
fun interface CardTransport {
    @Throws(IOException::class)
    fun transceive(command: ByteArray): ByteArray
}

/** Reponse d'une carte : donnees utiles et mot d'etat ISO 7816-4 (SW1-SW2). */
class ApduResponse(val data: ByteArray, val sw: Int) {

    val isSuccess: Boolean get() = sw == SW_SUCCESS

    val sw1: Int get() = sw ushr 8

    companion object {
        const val SW_SUCCESS = 0x9000
        const val SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982
        const val SW_INCOMPATIBLE_FILE_STRUCTURE = 0x6981

        /** Reponse trop courte pour porter un mot d'etat : aucun mot ISO ne vaut 0000. */
        const val SW_NONE = 0x0000

        fun parse(raw: ByteArray): ApduResponse {
            if (raw.size < 2) return ApduResponse(ByteArray(0), SW_NONE)
            val sw = ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or (raw[raw.size - 1].toInt() and 0xFF)
            return ApduResponse(raw.copyOfRange(0, raw.size - 2), sw)
        }
    }
}

/** Commandes ISO 7816-4 en forme courte : donnees <= 255 octets, reponse <= 256 octets. */
object Apdu {

    const val MAX_SHORT_DATA = 255

    /** Le maximal de la forme courte, encode par l'octet 00. */
    const val LE_MAX = 256

    private const val CLA_CHAINING = 0x10

    fun command(
        cla: Int,
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray = ByteArray(0),
        le: Int? = null,
    ): ByteArray {
        require(data.size <= MAX_SHORT_DATA) { "donnees trop longues pour la forme courte" }
        require(le == null || le in 1..LE_MAX) { "Le hors de la forme courte : $le" }
        val out = ByteArrayOutputStream()
        out.write(cla)
        out.write(ins)
        out.write(p1)
        out.write(p2)
        if (data.isNotEmpty()) {
            out.write(data.size)
            out.write(data)
        }
        if (le != null) out.write(if (le == LE_MAX) 0 else le)
        return out.toByteArray()
    }

    /**
     * Decoupe une commande trop longue pour la forme courte en maillons chaines.
     *
     * Chainage (bit 0x10 de CLA) plutot que forme longue : la forme longue depend de la
     * puce NFC du telephone (`IsoDep.isExtendedLengthApduSupported`), le chainage ne
     * depend que de la carte. Un defi RSA 2048 fait 266 octets une fois enveloppe :
     * c'est le cas qui l'impose. Seul le dernier maillon attend une reponse.
     */
    fun chained(
        cla: Int,
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray,
        le: Int? = null,
    ): List<ByteArray> {
        if (data.size <= MAX_SHORT_DATA) return listOf(command(cla, ins, p1, p2, data, le))
        val starts = data.indices step MAX_SHORT_DATA
        return starts.map { start ->
            val end = minOf(start + MAX_SHORT_DATA, data.size)
            val last = end == data.size
            command(
                cla = if (last) cla else cla or CLA_CHAINING,
                ins = ins,
                p1 = p1,
                p2 = p2,
                data = data.copyOfRange(start, end),
                le = if (last) le else null,
            )
        }
    }
}

/** Dialogue de haut niveau : chainage des commandes longues, collecte des reponses longues. */
class CardChannel(private val transport: CardTransport) {

    @Throws(IOException::class)
    fun send(
        cla: Int,
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray = ByteArray(0),
        le: Int? = null,
    ): ApduResponse {
        val commands = Apdu.chained(cla, ins, p1, p2, data, le)
        for (command in commands.dropLast(1)) {
            val link = ApduResponse.parse(transport.transceive(command))
            // Une carte qui refuse un maillon ne verra jamais la suite : son refus est la reponse.
            if (!link.isSuccess) return link
        }
        return collect(ApduResponse.parse(transport.transceive(commands.last())))
    }

    /**
     * SW1 = 61 : la carte a encore SW2 octets a rendre (00 = 256). GET RESPONSE jusqu'a
     * epuisement, avec une borne : une carte defaillante ne doit pas faire boucler le test.
     */
    private fun collect(first: ApduResponse): ApduResponse {
        if (first.sw1 != SW1_MORE_DATA) return first
        val buffer = ByteArrayOutputStream().apply { write(first.data) }
        var response = first
        var rounds = 0
        while (response.sw1 == SW1_MORE_DATA && rounds < MAX_GET_RESPONSE) {
            val remaining = response.sw and 0xFF
            val getResponse = Apdu.command(
                cla = 0x00,
                ins = INS_GET_RESPONSE,
                p1 = 0x00,
                p2 = 0x00,
                le = if (remaining == 0) Apdu.LE_MAX else remaining,
            )
            response = ApduResponse.parse(transport.transceive(getResponse))
            buffer.write(response.data)
            rounds++
        }
        return ApduResponse(buffer.toByteArray(), response.sw)
    }

    private companion object {
        const val SW1_MORE_DATA = 0x61
        const val INS_GET_RESPONSE = 0xC0

        /** 64 x 256 octets : bien au-dela d'un certificat, meme non compresse. */
        const val MAX_GET_RESPONSE = 64
    }
}
