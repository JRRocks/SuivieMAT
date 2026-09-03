package com.scanlist.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scanlist.app.data.db.dao.ComparisonDao
import com.scanlist.app.data.db.dao.FolderDao
import com.scanlist.app.data.db.dao.RegexRuleDao
import com.scanlist.app.data.db.dao.ScanItemDao
import com.scanlist.app.data.db.entity.ComparisonSessionEntity
import com.scanlist.app.data.db.entity.FolderEntity
import com.scanlist.app.data.db.entity.IntruderEntryEntity
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.data.db.entity.ScanItemEntity

@Database(
    entities = [
        FolderEntity::class,
        ScanItemEntity::class,
        RegexRuleEntity::class,
        ComparisonSessionEntity::class,
        ReferenceEntryEntity::class,
        IntruderEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ScanListDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao

    abstract fun scanItemDao(): ScanItemDao

    abstract fun regexRuleDao(): RegexRuleDao

    abstract fun comparisonDao(): ComparisonDao

    companion object {
        const val NAME = "scanlist.db"
    }
}
