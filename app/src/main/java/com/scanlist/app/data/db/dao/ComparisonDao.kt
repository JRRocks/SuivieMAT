package com.scanlist.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scanlist.app.data.db.entity.ComparisonSessionEntity
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.domain.model.ReferenceState
import kotlinx.coroutines.flow.Flow

@Dao
interface ComparisonDao {

    // --- Sessions ---

    @Query("SELECT * FROM comparison_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<ComparisonSessionEntity>>

    @Query("SELECT * FROM comparison_sessions WHERE id = :id")
    suspend fun getSession(id: Long): ComparisonSessionEntity?

    /** Observe la session : le rattachement a un dossier doit se voir sans recharger. */
    @Query("SELECT * FROM comparison_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<ComparisonSessionEntity?>

    @Insert
    suspend fun insertSession(session: ComparisonSessionEntity): Long

    @Query("UPDATE comparison_sessions SET resultFolderId = :folderId WHERE id = :sessionId")
    suspend fun attachResultFolder(sessionId: Long, folderId: Long)

    @Query("DELETE FROM comparison_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // --- Entrees de reference ---

    @Query("SELECT * FROM reference_entries WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeReferences(sessionId: Long): Flow<List<ReferenceEntryEntity>>

    @Query("SELECT value FROM reference_entries WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getReferenceValues(sessionId: Long): List<String>

    @Query("SELECT * FROM reference_entries WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getReferences(sessionId: Long): List<ReferenceEntryEntity>

    /** Valeurs deja confirmees : sert a reprendre une session interrompue. */
    @Query(
        """
        SELECT value FROM reference_entries
        WHERE sessionId = :sessionId AND state = 'FOUND'
        ORDER BY matchedAt ASC
        """,
    )
    suspend fun getFoundValues(sessionId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReferences(entries: List<ReferenceEntryEntity>): List<Long>

    @Query(
        """
        UPDATE reference_entries
        SET state = :state, matchedAt = :matchedAt
        WHERE sessionId = :sessionId AND value = :value
        """,
    )
    suspend fun markReference(
        sessionId: Long,
        value: String,
        state: ReferenceState,
        matchedAt: Long?,
    ): Int

    @Query("SELECT COUNT(*) FROM reference_entries WHERE sessionId = :sessionId")
    fun observeReferenceCount(sessionId: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM reference_entries WHERE sessionId = :sessionId AND state = 'FOUND'",
    )
    fun observeFoundCount(sessionId: Long): Flow<Int>

    // La table intruder_entries n'est plus alimentee : une valeur lue hors de la liste
    // de reference n'est pas prise en compte par la comparaison. Elle reste declaree
    // dans le schema pour ne pas imposer une migration destructive aux installations
    // existantes ; elle pourra disparaitre au prochain changement de version.
}
