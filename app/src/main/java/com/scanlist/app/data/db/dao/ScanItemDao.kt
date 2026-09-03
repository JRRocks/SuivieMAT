package com.scanlist.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scanlist.app.data.db.entity.ScanItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanItemDao {

    /** Ordre antechronologique : la derniere capture reste visible en haut pendant le scan. */
    @Query("SELECT * FROM scan_items WHERE folderId = :folderId ORDER BY scannedAt DESC, id DESC")
    fun observeByFolder(folderId: Long): Flow<List<ScanItemEntity>>

    @Query("SELECT * FROM scan_items WHERE folderId = :folderId ORDER BY scannedAt ASC, id ASC")
    suspend fun getByFolder(folderId: Long): List<ScanItemEntity>

    @Query("SELECT value FROM scan_items WHERE folderId = :folderId")
    suspend fun getValues(folderId: Long): List<String>

    @Query("SELECT COUNT(*) FROM scan_items WHERE folderId = :folderId")
    fun observeCount(folderId: Long): Flow<Int>

    /**
     * IGNORE et non REPLACE : sur collision de l'index unique (folderId, value)
     * la ligne existante est preservee et l'insertion renvoie -1, ce qui permet
     * de declencher le feedback "deja scanne" sans jamais interrompre le flux.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: ScanItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<ScanItemEntity>): List<Long>

    @Delete
    suspend fun delete(item: ScanItemEntity)

    @Query("DELETE FROM scan_items WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)
}
