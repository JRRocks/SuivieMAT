package com.scanlist.app.domain.model

/**
 * Normalisation d'une valeur scannee.
 *
 * Une seule regle, partagee par la deduplication (index unique en base) et par la
 * comparaison, pour qu'un code refuse comme doublon soit exactement un code reconnu
 * comme deja present dans la reference.
 */
object ScanValue {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Retire les espaces de bord et reduit toute suite d'espaces internes a un seul.
     * L'OCR insere frequemment des espaces parasites dans un marquage imprime.
     */
    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /**
     * Cle de comparaison. La casse est ignoree par defaut : un marquage releve en OCR
     * ne restitue pas toujours la casse d'origine, et un ecart de casse produirait un
     * faux "manquant" en mode Comparer.
     */
    fun key(raw: String, caseSensitive: Boolean = false): String {
        val normalized = normalize(raw)
        return if (caseSensitive) normalized else normalized.uppercase()
    }

    /** Une valeur vide ou uniquement composee d'espaces n'est jamais enregistree. */
    fun isUsable(raw: String): Boolean = normalize(raw).isNotEmpty()
}
