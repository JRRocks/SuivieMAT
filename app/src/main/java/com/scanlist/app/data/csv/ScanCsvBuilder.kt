package com.scanlist.app.data.csv

import com.scanlist.app.data.db.entity.ScanItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mise en forme d'un dossier en lignes CSV.
 *
 * Separe de [CsvCodec], qui ne sait qu'encoder des cellules : ici on decide **quelles**
 * colonnes exporter et dans quel ordre. Fonction pure, donc verifiable sans Android.
 */
object ScanCsvBuilder {

    val HEADER = listOf("valeur", "mode", "symbologie", "date_scan")

    /**
     * Format ISO plutot que le format francais : il se trie correctement dans un
     * tableur, ne depend pas de la locale du destinataire, et reste lisible.
     */
    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE)

    /**
     * Ordre chronologique, du premier scan au dernier.
     *
     * L'ecran de scan affiche l'inverse — la derniere capture en haut, pour la voir
     * apparaitre — mais un inventaire se relit dans l'ordre ou il a ete releve.
     */
    fun rows(items: List<ScanItemEntity>): List<List<String>> =
        listOf(HEADER) + items.sortedBy { it.scannedAt }.map { item ->
            listOf(
                item.value,
                item.mode.label,
                item.symbology.orEmpty(),
                TIMESTAMP.format(Date(item.scannedAt)),
            )
        }

    /** CSV complet, pret a etre ecrit : separateur `;`, BOM UTF-8, fins de ligne CRLF. */
    fun build(items: List<ScanItemEntity>): String = CsvCodec.encode(rows(items))
}
