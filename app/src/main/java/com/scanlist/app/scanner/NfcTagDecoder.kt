package com.scanlist.app.scanner

import android.nfc.Tag
import android.nfc.tech.Ndef
import com.scanlist.app.domain.nfc.NfcValue

/**
 * Un tag NFC/RFID decode.
 *
 * [symbology] distingue une valeur issue du contenu NDEF ("NDEF") d'un simple UID
 * materiel ("UID"), au meme titre que le champ symbologie d'un code-barres.
 */
data class DetectedNfcTag(val value: String, val symbology: String)

/**
 * Pont entre un [Tag] Android et [NfcValue].
 *
 * Toute la logique de choix et de decodage vit dans le domaine, testable sans Android ;
 * cette classe se contente d'extraire les octets bruts du tag. La lecture NDEF est
 * proteguee par [runCatching] : certains tags mal formes levent une exception au lieu de
 * renvoyer un resultat vide, et une exception ici ne doit jamais remonter jusqu'au
 * callback systeme — cela interromprait le scan a la chaine.
 */
object NfcTagDecoder {

    fun decode(tag: Tag): DetectedNfcTag? {
        val uid = tag.id ?: return null
        val ndefText = runCatching { readNdefText(tag) }.getOrNull()
        return DetectedNfcTag(
            value = NfcValue.resolve(ndefText, uid),
            symbology = if (ndefText != null) "NDEF" else "UID",
        )
    }

    private fun readNdefText(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        val records = ndef.cachedNdefMessage?.records ?: return null
        return records.firstNotNullOfOrNull { NfcValue.decodeTextRecord(it.payload) }
    }
}
