package com.scanlist.app.domain.card

import java.io.ByteArrayOutputStream

/** Objet BER-TLV : etiquette (1 a 3 octets, ex. 0x5F20) et valeur. */
class Tlv(val tag: Int, val value: ByteArray)

/**
 * Codage BER-TLV (ISO 7816-4 § 5.2), format de toutes les reponses structurees d'une carte.
 */
object BerTlv {

    /**
     * Decode une suite d'objets. Renvoie null si elle est mal formee : une carte qui
     * repond n'importe quoi doit produire un resultat "illisible", jamais une exception.
     */
    fun parse(bytes: ByteArray): List<Tlv>? {
        val result = mutableListOf<Tlv>()
        var i = 0
        while (i < bytes.size) {
            val first = bytes[i++].toInt() and 0xFF
            // 00 et FF ne sont jamais un debut d'etiquette : ISO 7816-4 les reserve au
            // bourrage entre deux objets, que certaines cartes laissent en fin d'enregistrement.
            if (first == 0x00 || first == 0xFF) continue

            var tag = first
            if (first and 0x1F == 0x1F) {
                do {
                    if (i >= bytes.size || tag > 0xFFFF) return null
                    val next = bytes[i++].toInt() and 0xFF
                    tag = (tag shl 8) or next
                } while (next and 0x80 != 0)
            }

            if (i >= bytes.size) return null
            val lengthByte = bytes[i++].toInt() and 0xFF
            val length = when {
                lengthByte < 0x80 -> lengthByte
                lengthByte in 0x81..0x83 -> {
                    val count = lengthByte - 0x80
                    if (i + count > bytes.size) return null
                    var value = 0
                    repeat(count) { value = (value shl 8) or (bytes[i++].toInt() and 0xFF) }
                    value
                }
                else -> return null
            }
            if (length > bytes.size - i) return null
            result += Tlv(tag, bytes.copyOfRange(i, i + length))
            i += length
        }
        return result
    }

    /** Valeur du premier objet d'etiquette [tag] au premier niveau, ou null. */
    fun find(bytes: ByteArray, tag: Int): ByteArray? =
        parse(bytes)?.firstOrNull { it.tag == tag }?.value

    fun encode(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        if (tag > 0xFFFF) out.write(tag ushr 16)
        if (tag > 0xFF) out.write(tag ushr 8)
        out.write(tag)
        when {
            value.size < 0x80 -> out.write(value.size)
            value.size <= 0xFF -> {
                out.write(0x81)
                out.write(value.size)
            }
            else -> {
                out.write(0x82)
                out.write(value.size ushr 8)
                out.write(value.size)
            }
        }
        out.write(value)
        return out.toByteArray()
    }
}
