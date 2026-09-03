package com.scanlist.app.data.csv

import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.domain.model.MatchState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mise en forme d'une session de comparaison en lignes CSV.
 *
 * Le fichier porte la liste de reference et rien d'autre, dans ses deux etats :
 * un controle de materiel se relit surtout par ce qui manque encore. La colonne
 * `etat` reprend le code impose — TROUVE (vert) ou MANQUANT (rouge).
 *
 * Les valeurs lues qui ne figurent pas dans la reference ne sont pas exportees :
 * elles ne sont pas prises en compte par la comparaison.
 */
object CompareCsvBuilder {

    val HEADER = listOf("valeur", "etat", "libelle", "date_validation")

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE)

    /**
     * Manquants d'abord, trouves ensuite.
     *
     * Meme ordre qu'a l'ecran : la section sur laquelle il reste a agir ouvre le
     * fichier, elle ne doit pas se lire apres trois cents lignes de vert.
     */
    fun rows(
        missing: List<ReferenceEntryEntity>,
        found: List<ReferenceEntryEntity>,
    ): List<List<String>> = buildList {
        add(HEADER)
        missing.forEach { entry ->
            // Pas de date : un manquant n'a jamais ete valide.
            add(listOf(entry.value, MatchState.MISSING.csvValue, entry.label.orEmpty(), ""))
        }
        found.forEach { entry ->
            add(
                listOf(
                    entry.value,
                    MatchState.FOUND.csvValue,
                    entry.label.orEmpty(),
                    entry.matchedAt?.let { TIMESTAMP.format(Date(it)) }.orEmpty(),
                ),
            )
        }
    }

    fun build(
        missing: List<ReferenceEntryEntity>,
        found: List<ReferenceEntryEntity>,
    ): String = CsvCodec.encode(rows(missing, found))
}
