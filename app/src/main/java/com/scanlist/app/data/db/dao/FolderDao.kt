package com.scanlist.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.scanlist.app.data.db.entity.FolderEntity
import com.scanlist.app.data.db.entity.FolderSummary
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ScanMode
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query(
        """
        SELECT f.id, f.name, f.kind, f.defaultScanMode, f.createdAt, f.updatedAt,
               (SELECT COUNT(*) FROM scan_items s WHERE s.folderId = f.id) AS itemCount
        FROM folders f
        ORDER BY f.updatedAt DESC
        """,
    )
    fun observeAll(): Flow<List<FolderSummary>>

    @Query(
        """
        SELECT f.id, f.name, f.kind, f.defaultScanMode, f.createdAt, f.updatedAt,
               (SELECT COUNT(*) FROM scan_items s WHERE s.folderId = f.id) AS itemCount
        FROM folders f
        WHERE f.kind = :kind
        ORDER BY f.updatedAt DESC
        """,
    )
    fun observeByKind(kind: FolderKind): Flow<List<FolderSummary>>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: Long): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT name FROM folders WHERE name = :name LIMIT 1")
    suspend fun findName(name: String): String?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("UPDATE folders SET defaultScanMode = :mode, updatedAt = :timestamp WHERE id = :id")
    suspend fun setDefaultScanMode(id: Long, mode: ScanMode, timestamp: Long)

    @Query("UPDATE folders SET name = :name, updatedAt = :timestamp WHERE id = :id")
    suspend fun rename(id: Long, name: String, timestamp: Long)
}
