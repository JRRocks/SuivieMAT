package com.scanlist.app.domain.compare

import com.scanlist.app.domain.model.CompareSourceKind

/**
 * Avancement d'une session de comparaison, tel que l'onglet Comparer l'affiche.
 *
 * Projection calculee par la base (une ligne par session) : l'onglet n'a pas a charger
 * les listes de reference pour savoir ou en est chaque controle.
 */
data class SessionProgress(
    val sessionId: Long,
    val name: String,
    val sourceKind: CompareSourceKind,
    /** Id du dossier local, ou nom du fichier .csv importe. */
    val sourceRef: String,
    /** Dossier d'origine, null pour un import .csv. */
    val sourceFolderId: Long?,
    val createdAt: Long,
    val found: Int,
    val total: Int,
    /** Creation de la session ou derniere validation, la plus recente des deux. */
    val lastActivity: Long,
) {
    val missing: Int get() = total - found

    val isComplete: Boolean get() = total > 0 && found == total
}

/** Une ligne de l'onglet Comparer. */
sealed interface CompareEntry<out F> {
    /** Moment qui range la ligne dans la liste par date. */
    val timestamp: Long

    /** Un dossier, avec la derniere comparaison faite a partir de lui s'il y en a une. */
    data class FromFolder<F>(
        val folder: F,
        val session: SessionProgress?,
        override val timestamp: Long,
    ) : CompareEntry<F>

    /**
     * Une session sans dossier a afficher : import .csv, ou dossier d'origine supprime
     * depuis. Sa liste de reference vit dans la session, elle reste reprenable.
     */
    data class Standalone(val session: SessionProgress) : CompareEntry<Nothing> {
        override val timestamp: Long get() = session.lastActivity
    }
}

/**
 * Fusion des dossiers et des sessions de comparaison en une seule liste par date
 * (etape 6 de la refonte, variante G choisie par l'utilisateur).
 *
 * Un dossier ne porte que sa derniere session : c'est celle qu'on reprend. Les plus
 * anciennes restent en base sans etre montrees, comme avant cette etape ou aucune ne
 * l'etait.
 */
object CompareOverview {

    fun <F> build(
        folders: List<F>,
        idOf: (F) -> Long,
        updatedAtOf: (F) -> Long,
        sessions: List<SessionProgress>,
    ): List<CompareEntry<F>> {
        val latestByFolder = sessions
            .filter { it.sourceFolderId != null }
            .groupBy { it.sourceFolderId }
            .mapValues { (_, list) -> list.maxWith(BY_CREATION) }

        val folderEntries = folders.map { folder ->
            val session = latestByFolder[idOf(folder)]
            // Une validation faite aujourd'hui sur un vieux dossier le ramene en tete :
            // c'est le controle en cours qu'on cherche, pas la date du scan d'origine.
            val timestamp = maxOf(updatedAtOf(folder), session?.lastActivity ?: Long.MIN_VALUE)
            CompareEntry.FromFolder(folder, session, timestamp)
        }

        val folderIds = folders.mapTo(HashSet(), idOf)
        val standalone = sessions.filter { it.sourceFolderId == null } +
            latestByFolder.values.filter { it.sourceFolderId !in folderIds }

        // Tri stable : a moment egal, l'ordre de la base (le plus recent en tete) reste.
        return (folderEntries + standalone.map { CompareEntry.Standalone(it) })
            .sortedByDescending { it.timestamp }
    }

    private val BY_CREATION = compareBy<SessionProgress>({ it.createdAt }, { it.sessionId })
}
