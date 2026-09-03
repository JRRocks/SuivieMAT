package com.scanlist.app.data.csv

import java.text.Normalizer

/**
 * Encodage et decodage CSV, implementes en interne.
 *
 * Le besoin (RFC 4180 + BOM UTF-8 pour Excel FR + separateur point-virgule) est trop
 * simple pour justifier une dependance externe, et l'import doit rester tolerant aux
 * fichiers produits par n'importe quel tableur.
 *
 * Export  : separateur ';', guillemets RFC 4180, BOM UTF-8, fins de ligne CRLF.
 * Import  : separateur auto-detecte (';' ou ','), BOM optionnel, en-tete optionnel.
 */
object CsvCodec {

    /** BOM UTF-8, ecrit sous forme d'echappement pour rester visible dans le source. */
    const val BOM = "\uFEFF"
    const val DEFAULT_SEPARATOR = ';'
    private const val LINE_END = "\r\n"

    private val HEADER_KEYWORDS = setOf(
        "valeur", "value", "code", "reference", "ref", "id", "identifiant",
        "serie", "numero", "sn", "designation", "libelle", "label", "description",
        "mode", "etat", "state", "date", "symbologie",
    )

    // ---------------------------------------------------------------- Export

    /**
     * Serialise [rows] en CSV. Le BOM permet a Excel en locale FR d'ouvrir le fichier
     * en UTF-8 sans mojibake sur les accents.
     */
    fun encode(
        rows: List<List<String>>,
        separator: Char = DEFAULT_SEPARATOR,
        withBom: Boolean = true,
    ): String = buildString {
        if (withBom) append(BOM)
        rows.forEach { row ->
            append(row.joinToString(separator.toString()) { escapeField(it, separator) })
            append(LINE_END)
        }
    }

    /**
     * Entoure le champ de guillemets si necessaire et double les guillemets internes.
     * Un champ commencant par un espace est aussi protege, sinon certains tableurs
     * le rognent silencieusement.
     */
    fun escapeField(field: String, separator: Char = DEFAULT_SEPARATOR): String {
        val needsQuoting = field.any { it == separator || it == '"' || it == '\n' || it == '\r' } ||
            field.startsWith(" ") ||
            field.endsWith(" ")
        if (!needsQuoting) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    // ---------------------------------------------------------------- Import

    /**
     * Devine le separateur en comparant les occurrences hors guillemets de ';' et ','
     * sur les premieres lignes. Egalite ou absence : ';' par defaut.
     */
    fun detectSeparator(text: String): Char {
        var semicolons = 0
        var commas = 0
        var tabs = 0
        var inQuotes = false
        var lines = 0
        for (ch in text) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                inQuotes -> Unit
                ch == ';' -> semicolons++
                ch == ',' -> commas++
                ch == '\t' -> tabs++
                ch == '\n' -> {
                    lines++
                    if (lines >= 5) break
                }
            }
        }
        return when {
            tabs > semicolons && tabs > commas -> '\t'
            commas > semicolons -> ','
            else -> DEFAULT_SEPARATOR
        }
    }

    /**
     * Parse un CSV complet. Gere les champs entre guillemets contenant separateur,
     * retour a la ligne ou guillemet double. Les lignes entierement vides sont ignorees.
     */
    fun parse(text: String, separator: Char? = null): List<List<String>> {
        val content = text.removePrefix(BOM)
        val sep = separator ?: detectSeparator(content)

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
            row = mutableListOf()
        }

        while (index < content.length) {
            val ch = content[index]
            when {
                inQuotes -> when {
                    ch == '"' && index + 1 < content.length && content[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == sep -> endField()
                ch == '\r' -> {
                    // CRLF : on laisse le \n suivant fermer la ligne.
                    if (index + 1 >= content.length || content[index + 1] != '\n') endRow()
                }
                ch == '\n' -> endRow()
                else -> field.append(ch)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows
    }

    /**
     * Extrait les entrees de reference d'un CSV importe.
     *
     * Convention : premiere colonne = valeur, deuxieme colonne (si presente) = libelle.
     * L'en-tete est saute s'il est reconnu. Les valeurs vides et les doublons internes
     * au fichier sont ecartes, la premiere occurrence gagne.
     */
    fun parseReferences(text: String): List<ReferenceRow> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()

        val body = if (isLikelyHeader(rows.first())) rows.drop(1) else rows
        val seen = LinkedHashMap<String, ReferenceRow>()
        body.forEach { cells ->
            val value = cells.getOrNull(0)?.trim().orEmpty()
            if (value.isEmpty()) return@forEach
            val label = cells.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            seen.putIfAbsent(value, ReferenceRow(value, label))
        }
        return seen.values.toList()
    }

    /**
     * Une premiere ligne est consideree comme un en-tete si sa premiere cellule est un
     * mot-cle connu. Sur un fichier sans en-tete, une reference reelle ne ressemble
     * pratiquement jamais a "valeur" ou "reference", le faux positif est negligeable.
     */
    fun isLikelyHeader(row: List<String>): Boolean {
        val first = row.firstOrNull()?.let(::normalize) ?: return false
        return first in HEADER_KEYWORDS
    }

    private fun normalize(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    /** Ligne de reference issue d'un CSV importe. */
    data class ReferenceRow(val value: String, val label: String?)
}
