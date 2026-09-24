package com.scanlist.app.domain.card

/** Octets en hexadecimal majuscule, sans separateur : forme lisible des AID et des reponses. */
fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

/** Mot d'etat ISO 7816-4 sur quatre chiffres hexadecimaux, tel qu'il figure dans les normes. */
fun formatStatusWord(sw: Int): String = "%04X".format(sw)

/** Inverse de [toHex], espaces toleres : reserve aux constantes ecrites dans le code. */
fun hexBytes(text: String): ByteArray {
    val clean = text.filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "hexadecimal de longueur impaire : $text" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
