package com.scanlist.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.scanlist.app.data.db.entity.RegexRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegexRuleDao {

    /** Bibliotheque triee par usage recent : la formule du dernier scan remonte en tete. */
    @Query("SELECT * FROM regex_rules ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<RegexRuleEntity>>

    @Query("SELECT * FROM regex_rules WHERE id = :id")
    suspend fun getById(id: Long): RegexRuleEntity?

    @Query("SELECT * FROM regex_rules WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): RegexRuleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RegexRuleEntity): Long

    @Update
    suspend fun update(rule: RegexRuleEntity)

    @Delete
    suspend fun delete(rule: RegexRuleEntity)

    @Query("UPDATE regex_rules SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markUsed(id: Long, timestamp: Long)
}
