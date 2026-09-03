package com.scanlist.app.domain.nfc

/**
 * Interpretation d'un tag NFC/RFID detecte : choix entre contenu NDEF et UID materiel,
 * decodage du format de texte NFC Forum (RTD_TEXT). Isolee du SDK Android (android.nfc.*)
 * pour rester testable en JVM, comme [com.scanlist.app.domain.regex.RegexRuleEngine].
 */
object NfcValue {

    /**
     * Valeur retenue pour un tag.
     *
     * De nombreux tags d'inventaire bon marche (Mifare, RFID colles sur du materiel) ne
     * portent aucun contenu NDEF : seul l'UID materiel les identifie. Un contenu NDEF
     * texte, quand il existe, prime — c'est une valeur choisie au marquage du tag,
     * plus lisible qu'un UID hexadecimal.
     */
    fun resolve(ndefText: String?, uid: ByteArray): String =
        ndefText?.trim()?.takeIf { it.isNotEmpty() } ?: formatUid(uid)

    /** UID en hexadecimal majuscule, sans separateur : forme stable, sans ambiguite de casse. */
    fun formatUid(uid: ByteArray): String = uid.joinToString("") { "%02X".format(it) }

    /**
     * Decode un enregistrement NDEF de type texte (RTD_TEXT, NFC Forum).
     *
     * Le premier octet du payload code la langue (longueur sur les bits 0-5) et
     * l'encodage (bit 7 : UTF-16 si pose, UTF-8 sinon). Renvoie null pour tout autre
     * format ou payload corrompu — jamais d'exception, conformement a la regle
     * "cas d'erreur, ne rien faire".
     */
    fun decodeTextRecord(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val header = payload[0].toInt()
        val languageCodeLength = header and 0x3F
        val isUtf16 = (header and 0x80) != 0
        val textStart = 1 + languageCodeLength
        if (textStart > payload.size) return null
        val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
        return runCatching {
            String(payload, textStart, payload.size - textStart, charset)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
