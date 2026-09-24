package com.scanlist.app.domain.card

/** Application declaree par la carte dans son repertoire EF.DIR. */
data class CardApplication(val aid: String, val label: String?)

/**
 * Lecture du repertoire d'applications EF.DIR (ISO 7816-4 § 8.2.1.1).
 *
 * C'est ce qui permet d'identifier la norme suivie par un badge inconnu : chaque
 * application y figure avec son identifiant (AID) et souvent un libelle en clair.
 */
object CardDirectory {

    private const val TAG_APPLICATION_TEMPLATE = 0x61
    private const val TAG_AID = 0x4F
    private const val TAG_LABEL = 0x50

    fun parse(content: ByteArray): List<CardApplication> =
        BerTlv.parse(content).orEmpty()
            .filter { it.tag == TAG_APPLICATION_TEMPLATE }
            .mapNotNull { template ->
                val inner = BerTlv.parse(template.value) ?: return@mapNotNull null
                val aid = inner.firstOrNull { it.tag == TAG_AID }?.value ?: return@mapNotNull null
                val label = inner.firstOrNull { it.tag == TAG_LABEL }?.value?.let(::printable)
                CardApplication(aid.toHex(), label)
            }

    /** Libelle ISO 8859-1 reduit a ses caracteres imprimables, null s'il n'en reste rien. */
    private fun printable(bytes: ByteArray): String? =
        String(bytes, Charsets.ISO_8859_1)
            .filter { it in ' '..'~' || it.code >= 0xA0 }
            .trim()
            .ifEmpty { null }
}
