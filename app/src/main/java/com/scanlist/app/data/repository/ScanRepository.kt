package com.scanlist.app.data.repository

import com.scanlist.app.data.db.dao.FolderDao
import com.scanlist.app.data.db.dao.ScanItemDao
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.domain.model.ScanValue
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Issue de l'enregistrement d'une capture. */
sealed interface ScanRecordResult {

    /** Nouvelle ligne creee. */
    data class Saved(val id: Long, val value: String) : ScanRecordResult

    /** Deja present dans ce dossier : rien n'est ecrit, l'utilisateur est prevenu. */
    data class Duplicate(val value: String) : ScanRecordResult

    /** Valeur vide ou illisible : ne rien faire. */
    data object Ignored : ScanRecordResult
}

@Singleton
class ScanRepository @Inject constructor(
    private val scanItemDao: ScanItemDao,
    private val folderDao: FolderDao,
) {

    fun observeItems(folderId: Long): Flow<List<ScanItemEntity>> =
        scanItemDao.observeByFolder(folderId)

    fun observeCount(folderId: Long): Flow<Int> = scanItemDao.observeCount(folderId)

    suspend fun getItems(folderId: Long): List<ScanItemEntity> = scanItemDao.getByFolder(folderId)

    suspend fun getValues(folderId: Long): List<String> = scanItemDao.getValues(folderId)

    /**
     * Enregistre une capture.
     *
     * La deduplication n'est pas testee avant insertion : l'index unique
     * (folderId, value) tranche en une seule requete, ce qui evite toute course entre
     * deux frames analysees coup sur coup. Une collision renvoie -1.
     */
    suspend fun record(
        folderId: Long,
        rawValue: String,
        mode: ScanMode,
        symbology: String? = null,
        regexRuleId: Long? = null,
        scannedAt: Long = System.currentTimeMillis(),
    ): ScanRecordResult {
        val value = ScanValue.normalize(rawValue)
        if (value.isEmpty()) return ScanRecordResult.Ignored

        val id = scanItemDao.insertIgnore(
            ScanItemEntity(
                folderId = folderId,
                value = value,
                rawValue = rawValue,
                mode = mode,
                symbology = symbology,
                regexRuleId = regexRuleId,
                scannedAt = scannedAt,
            ),
        )
        return if (id == -1L) {
            ScanRecordResult.Duplicate(value)
        } else {
            folderDao.touch(folderId, scannedAt)
            ScanRecordResult.Saved(id, value)
        }
    }

    /**
     * Enregistre toutes les valeurs detectees dans une meme image.
     *
     * Chaque code produit sa propre ligne ; l'ordre des resultats suit celui des
     * valeurs fournies, pour que l'appelant sache lesquelles etaient des doublons.
     */
    suspend fun recordAll(
        folderId: Long,
        rawValues: List<String>,
        mode: ScanMode,
        symbologies: List<String?> = emptyList(),
        regexRuleId: Long? = null,
        scannedAt: Long = System.currentTimeMillis(),
    ): List<ScanRecordResult> = rawValues.mapIndexed { index, raw ->
        record(
            folderId = folderId,
            rawValue = raw,
            mode = mode,
            symbology = symbologies.getOrNull(index),
            regexRuleId = regexRuleId,
            scannedAt = scannedAt,
        )
    }

    suspend fun delete(item: ScanItemEntity) {
        scanItemDao.delete(item)
        folderDao.touch(item.folderId, System.currentTimeMillis())
    }

    /**
     * Remet en base une ligne tout juste supprimee, pour l'annulation.
     *
     * L'identifiant d'origine est conserve : la ligne reprend sa place exacte plutot
     * que de reapparaitre en fin de liste. Si la valeur a ete rescannee entre-temps,
     * l'index unique la refuse et l'annulation ne fait rien — le doublon reste exclu.
     */
    suspend fun restore(item: ScanItemEntity): Boolean {
        val id = scanItemDao.insertIgnore(item)
        if (id != -1L) folderDao.touch(item.folderId, System.currentTimeMillis())
        return id != -1L
    }

    suspend fun clearFolder(folderId: Long) {
        scanItemDao.deleteByFolder(folderId)
        folderDao.touch(folderId, System.currentTimeMillis())
    }
}
