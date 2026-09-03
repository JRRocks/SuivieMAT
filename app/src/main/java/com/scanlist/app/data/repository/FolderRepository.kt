package com.scanlist.app.data.repository

import com.scanlist.app.data.db.dao.FolderDao
import com.scanlist.app.data.db.entity.FolderEntity
import com.scanlist.app.data.db.entity.FolderSummary
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ScanMode
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao: FolderDao,
) {

    fun observeAll(): Flow<List<FolderSummary>> = folderDao.observeAll()

    fun observeByKind(kind: FolderKind): Flow<List<FolderSummary>> = folderDao.observeByKind(kind)

    fun observeById(id: Long): Flow<FolderEntity?> = folderDao.observeById(id)

    suspend fun getById(id: Long): FolderEntity? = folderDao.getById(id)

    /**
     * Cree un dossier. Le nom est rendu unique par suffixe plutot que de rejeter la
     * creation : sur le terrain, une session interrompue ne doit jamais bloquer la
     * suivante sur une erreur de nommage.
     */
    suspend fun create(
        name: String,
        kind: FolderKind = FolderKind.SCAN,
        defaultScanMode: ScanMode = ScanMode.QR_CODE,
    ): Long {
        val now = System.currentTimeMillis()
        return folderDao.insert(
            FolderEntity(
                name = uniqueName(name.trim().ifEmpty { defaultName(defaultScanMode) }),
                kind = kind,
                defaultScanMode = defaultScanMode,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val target = name.trim()
        if (target.isEmpty()) return
        val current = folderDao.getById(id) ?: return
        if (current.name == target) return
        folderDao.rename(id, uniqueName(target), System.currentTimeMillis())
    }

    suspend fun delete(folder: FolderEntity) = folderDao.delete(folder)

    suspend fun deleteById(id: Long) {
        folderDao.getById(id)?.let { folderDao.delete(it) }
    }

    suspend fun setDefaultScanMode(id: Long, mode: ScanMode) {
        folderDao.setDefaultScanMode(id, mode, System.currentTimeMillis())
    }

    suspend fun touch(id: Long) = folderDao.touch(id, System.currentTimeMillis())

    /** Nom propose a la creation : "Scan QR Code 20-08-2026 10h42". */
    fun defaultName(mode: ScanMode, at: Long = System.currentTimeMillis()): String =
        "Scan ${mode.label} ${TIMESTAMP_FORMAT.format(Date(at))}"

    private suspend fun uniqueName(base: String): String {
        if (folderDao.findName(base) == null) return base
        var suffix = 2
        while (folderDao.findName("$base ($suffix)") != null) suffix++
        return "$base ($suffix)"
    }

    private companion object {
        val TIMESTAMP_FORMAT = SimpleDateFormat("dd-MM-yyyy HH'h'mm", Locale.FRANCE)
    }
}
