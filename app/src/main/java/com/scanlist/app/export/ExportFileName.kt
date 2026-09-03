package com.scanlist.app.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Construction et assainissement du nom de fichier d'export.
 *
 * L'utilisateur choisit ce nom librement, mais le fichier finit en piece jointe d'un
 * mail puis sur le disque d'un destinataire : les caracteres interdits par Windows,
 * Android ou les serveurs de messagerie doivent disparaitre avant l'ecriture, sans
 * quoi l'ouverture echoue chez le destinataire sans message clair.
 */
object ExportFileName {

    const val EXTENSION = ".csv"

    const val FALLBACK = "export.csv"

    /**
     * Reserves par Windows, plus le slash des systemes Unix et les caracteres de
     * controle. Espaces et tirets sont **conserves** : ils composent la plupart des
     * noms de dossiers et ne posent aucun probleme de systeme de fichiers.
     */
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    private val WHITESPACE = Regex("\\s+")

    /** Au-dela, certains clients mail tronquent le nom et cassent l'extension. */
    private const val MAX_BASE_LENGTH = 80

    private val DATE_SUFFIX = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.FRANCE)

    /** Nom propose par defaut : le nom du dossier, horodate. */
    fun suggest(folderName: String, at: Long = System.currentTimeMillis()): String =
        sanitize(folderName.trim() + "_" + DATE_SUFFIX.format(Date(at)))

    /**
     * Rend [raw] utilisable comme nom de fichier et garantit l'extension `.csv`.
     *
     * Un nom vide ou entierement compose de caracteres interdits retombe sur
     * [FALLBACK] plutot que de produire un fichier sans nom.
     */
    fun sanitize(raw: String): String {
        val base = raw.trim()
            .removeSuffix(EXTENSION)
            .replace(ILLEGAL, "")
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_BASE_LENGTH)
            // Un point ou un espace final est ignore par Windows : le fichier
            // deviendrait introuvable pour le destinataire.
            .trimEnd('.', ' ')

        return if (base.isEmpty()) FALLBACK else base + EXTENSION
    }
}
