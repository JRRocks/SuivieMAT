package com.scanlist.app.scanner

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import com.scanlist.app.domain.card.CardDiagnostic
import com.scanlist.app.domain.card.CardReport
import com.scanlist.app.domain.card.CardTransport
import java.io.IOException

/**
 * Ce que le test du badge a pu observer.
 *
 * [report] est null quand la carte ne parle pas ISO 14443-4 ([isoDep] faux) ou que le
 * dialogue n'a pas pu s'ouvrir ([connectionFailed]) : sans ce protocole, aucune commande
 * de carte a microprocesseur n'est possible et les niveaux 2 et 3 n'ont pas de sens.
 */
class CardTestOutcome(
    val technologies: List<String>,
    val isoDep: Boolean,
    val historicalBytes: ByteArray?,
    val report: CardReport?,
    val connectionFailed: Boolean,
    /** Octets d'anticollision du type A : ils classent la famille de puce, pas la carte. */
    val atqa: ByteArray? = null,
    val sak: Int? = null,
    val extendedLength: Boolean? = null,
)

/**
 * Pont entre un [Tag] Android et [CardDiagnostic].
 *
 * Toute la logique vit dans le domaine, testable sans Android ; cette classe ouvre le
 * canal IsoDep et le referme, rien de plus.
 */
object CardTestReader {

    /** Une signature RSA 2048 peut prendre plus d'une seconde sur une puce lente. */
    private const val TIMEOUT_MS = 5_000

    /** Bloquant : a appeler hors du thread principal, depuis le rappel du lecteur NFC. */
    fun read(tag: Tag): CardTestOutcome {
        val technologies = tag.techList.map { it.substringAfterLast('.') }
        val nfcA = NfcA.get(tag)
        val atqa = nfcA?.atqa
        val sak = nfcA?.sak?.toInt()?.and(0xFF)
        val isoDep = IsoDep.get(tag)
            ?: return CardTestOutcome(
                technologies, isoDep = false, historicalBytes = null, report = null, connectionFailed = false,
                atqa = atqa, sak = sak,
            )
        // Octets historiques en type A, reponse de couche haute en type B : les deux
        // portent l'identification du produit que les fabricants y placent.
        val historical = isoDep.historicalBytes ?: isoDep.hiLayerResponse
        val extendedLength = isoDep.isExtendedLengthApduSupported

        val connected = runCatching {
            isoDep.connect()
            isoDep.timeout = TIMEOUT_MS
        }.isSuccess
        if (!connected) {
            return CardTestOutcome(
                technologies, isoDep = true, historicalBytes = historical, report = null, connectionFailed = true,
                atqa = atqa, sak = sak, extendedLength = extendedLength,
            )
        }

        return try {
            val report = CardDiagnostic(IsoDepTransport(isoDep)).run()
            CardTestOutcome(
                technologies, isoDep = true, historicalBytes = historical, report = report, connectionFailed = false,
                atqa = atqa, sak = sak, extendedLength = extendedLength,
            )
        } finally {
            runCatching { isoDep.close() }
        }
    }
}

private class IsoDepTransport(private val isoDep: IsoDep) : CardTransport {

    override fun transceive(command: ByteArray): ByteArray = try {
        isoDep.transceive(command)
    } catch (e: IOException) {
        throw e
    } catch (e: RuntimeException) {
        // Un badge sorti du champ leve TagLostException (une IOException), mais selon les
        // versions d'Android aussi SecurityException ("tag out of date") ou
        // IllegalStateException : meme sens, meme traitement par le diagnostic.
        throw IOException(e)
    }
}
