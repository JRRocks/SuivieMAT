package com.scanlist.app.domain.update

/**
 * Numero de version, tel que le porte `versionName` ("1.11") ou l'etiquette d'une
 * release GitHub ("v1.12").
 *
 * La comparaison est numerique, segment par segment : "1.10" est plus recente que
 * "1.9", ce qu'une comparaison de chaines dirait a l'envers — et c'est precisement le
 * passage que cette application a deja franchi.
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    // "1.9" et "1.9.0" designent la meme version : l'egalite suit la comparaison.
    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {

        /**
         * Lit "1.11", "v1.12" ou "1.9.0". Tout autre texte rend `null` : une etiquette
         * qui n'est pas un numero (essai, brouillon) ne doit jamais passer pour une
         * mise a jour.
         */
        fun parseOrNull(raw: String?): AppVersion? {
            val text = raw?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
            if (text.isEmpty()) return null
            val parts = text.split('.').map { segment ->
                // Chiffres seuls : toInt() accepterait "+1", qui n'est pas un numero de version.
                if (segment.isEmpty() || !segment.all { it in '0'..'9' }) return null
                segment.toIntOrNull() ?: return null
            }
            return AppVersion(parts)
        }
    }
}
