package com.scanlist.app.domain.search

import java.text.Normalizer

/**
 * Filtrage des dossiers par nom.
 *
 * Insensible a la casse **et aux accents** : les dossiers sont nommes en francais et
 * souvent horodates automatiquement. Taper "controle" doit trouver « Contrôle rack 3 »,
 * sinon la recherche echoue precisement sur les noms les plus courants.
 */
object FolderSearch {

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun matches(name: String, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        return normalize(name).contains(needle)
    }

    fun <T> filter(items: List<T>, query: String, nameOf: (T) -> String): List<T> {
        if (normalize(query).isEmpty()) return items
        return items.filter { matches(nameOf(it), query) }
    }

    private fun normalize(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
}
