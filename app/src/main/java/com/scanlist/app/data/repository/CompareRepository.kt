package com.scanlist.app.data.repository

import com.scanlist.app.data.csv.CsvCodec
import com.scanlist.app.data.db.dao.ComparisonDao
import com.scanlist.app.data.db.dao.FolderDao
import com.scanlist.app.data.db.dao.ScanItemDao
import com.scanlist.app.data.db.entity.ComparisonSessionEntity
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.domain.compare.CompareEngine
import com.scanlist.app.domain.compare.CompareOutcome
import com.scanlist.app.domain.compare.SessionProgress
import com.scanlist.app.domain.model.CompareSourceKind
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.domain.model.ScanValue
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultat consolide d'une session de comparaison.
 *
 * Seule la liste de reference porte un etat : [missing] = rouge, non encore validee,
 * [found] = vert, presence physique confirmee. Une valeur lue hors reference ne
 * figure nulle part, elle n'est pas prise en compte par la comparaison.
 */
data class CompareResult(
    val session: ComparisonSessionEntity,
    val found: List<ReferenceEntryEntity>,
    val missing: List<ReferenceEntryEntity>,
) {
    val referenceCount: Int get() = found.size + missing.size
}

@Singleton
class CompareRepository @Inject constructor(
    private val comparisonDao: ComparisonDao,
    private val folderDao: FolderDao,
    private val scanItemDao: ScanItemDao,
    private val folderRepository: FolderRepository,
) {

    fun observeSessions(): Flow<List<ComparisonSessionEntity>> = comparisonDao.observeSessions()

    fun observeSessionProgress(): Flow<List<SessionProgress>> =
        comparisonDao.observeSessionProgress()

    fun observeSession(id: Long): Flow<ComparisonSessionEntity?> = comparisonDao.observeSession(id)

    /**
     * Supprime une session et, par cascade, sa liste de reference. Ni le dossier
     * d'origine ni le dossier de resultat ne sont touches : ce sont des dossiers a part
     * entiere, que l'onglet Dossiers gere.
     */
    suspend fun deleteSession(id: Long) = comparisonDao.deleteSession(id)

    fun observeReferences(sessionId: Long): Flow<List<ReferenceEntryEntity>> =
        comparisonDao.observeReferences(sessionId)

    suspend fun getSession(id: Long): ComparisonSessionEntity? = comparisonDao.getSession(id)

    /** Cree une session dont la reference provient d'un dossier deja scanne. */
    suspend fun createFromFolder(folderId: Long, name: String? = null): Long? {
        val folder = folderDao.getById(folderId) ?: return null
        val values = scanItemDao.getValues(folderId)
        return createSession(
            name = name ?: "Comparaison ${folder.name}",
            sourceKind = CompareSourceKind.LOCAL_FOLDER,
            sourceRef = folderId.toString(),
            references = values.map { CsvCodec.ReferenceRow(it, null) },
        )
    }

    /** Cree une session dont la reference provient d'un fichier .csv importe. */
    suspend fun createFromCsv(fileName: String, csvContent: String, name: String? = null): Long? {
        val references = CsvCodec.parseReferences(csvContent)
        if (references.isEmpty()) return null
        return createSession(
            name = name ?: "Comparaison $fileName",
            sourceKind = CompareSourceKind.CSV_IMPORT,
            sourceRef = fileName,
            references = references,
        )
    }

    private suspend fun createSession(
        name: String,
        sourceKind: CompareSourceKind,
        sourceRef: String,
        references: List<CsvCodec.ReferenceRow>,
    ): Long {
        val sessionId = comparisonDao.insertSession(
            ComparisonSessionEntity(
                name = name,
                sourceKind = sourceKind,
                sourceRef = sourceRef,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Normalisation a l'insertion, et non a la lecture : la valeur stockee doit
        // etre exactement celle que le moteur renverra, sinon markReference ne
        // retrouverait pas sa ligne et un "trouve" ne serait jamais persiste.
        comparisonDao.insertReferences(
            references.mapNotNull { row ->
                val value = ScanValue.normalize(row.value)
                if (value.isEmpty()) {
                    null
                } else {
                    ReferenceEntryEntity(
                        sessionId = sessionId,
                        value = value,
                        label = row.label,
                    )
                }
            },
        )
        return sessionId
    }

    /**
     * Charge le moteur de comparaison en memoire, en restaurant l'etat deja persiste.
     * Permet de reprendre une session interrompue sans repartir de zero.
     */
    suspend fun loadEngine(sessionId: Long): CompareEngine? {
        comparisonDao.getSession(sessionId) ?: return null
        val engine = CompareEngine(comparisonDao.getReferenceValues(sessionId))
        engine.restoreFound(comparisonDao.getFoundValues(sessionId))
        return engine
    }

    /**
     * Persiste le verdict d'une capture.
     *
     * Le moteur en memoire a deja tranche : cette methode ne fait qu'ecrire, hors du
     * thread d'analyse, pour ne jamais suspendre le pipeline camera.
     *
     * Une seule ecriture possible : le passage d'une entree de reference au vert. Une
     * valeur hors reference n'est pas enregistree — regle posee par l'utilisateur, la
     * comparaison valide la presence physique des elements de la liste et ne s'occupe
     * de rien d'autre.
     */
    suspend fun persistOutcome(
        sessionId: Long,
        outcome: CompareOutcome,
        at: Long = System.currentTimeMillis(),
    ) {
        when (outcome) {
            is CompareOutcome.Matched -> comparisonDao.markReference(
                sessionId = sessionId,
                value = outcome.referenceValue,
                state = ReferenceState.FOUND,
                matchedAt = at,
            )

            is CompareOutcome.OutOfList -> Unit
            is CompareOutcome.AlreadyMatched -> Unit
            CompareOutcome.Ignored -> Unit
        }
    }

    /**
     * Enregistre la session dans un dossier unique.
     *
     * Le detail colore rouge / vert reste porte par la session elle-meme, dans
     * reference_entries. Le dossier cree sert de point d'entree dans le menu
     * "Dossiers" et recoit une ligne par element dont la presence a ete confirmee.
     * Les manquants n'ont jamais ete lus, ils n'ont donc pas leur place dans
     * scan_items : c'est le .csv de la session qui les porte.
     *
     * Appel idempotent : une session deja enregistree renvoie son dossier existant.
     */
    suspend fun saveSessionToFolder(sessionId: Long, folderName: String? = null): Long? {
        val session = comparisonDao.getSession(sessionId) ?: return null
        session.resultFolderId?.let { return it }

        val folderId = folderRepository.create(
            name = folderName ?: session.name,
            kind = FolderKind.COMPARISON,
        )
        comparisonDao.attachResultFolder(sessionId, folderId)

        val found = comparisonDao.getReferences(sessionId)
            .filter { it.state == ReferenceState.FOUND }
            .map { entry ->
                ScanItemEntity(
                    folderId = folderId,
                    value = entry.value,
                    rawValue = entry.value,
                    mode = ScanMode.QR_CODE,
                    scannedAt = entry.matchedAt ?: session.createdAt,
                )
            }
        scanItemDao.insertAllIgnore(found)
        return folderId
    }

    /**
     * Vue a deux etats d'une session, pour l'affichage et l'export.
     * Ordre stable : trouves puis manquants, l'appelant choisit son ordre d'affichage.
     */
    suspend fun buildResult(sessionId: Long): CompareResult? {
        val session = comparisonDao.getSession(sessionId) ?: return null
        val references = comparisonDao.getReferences(sessionId)
        return CompareResult(
            session = session,
            found = references.filter { it.state == ReferenceState.FOUND },
            missing = references.filter { it.state == ReferenceState.MISSING },
        )
    }
}
