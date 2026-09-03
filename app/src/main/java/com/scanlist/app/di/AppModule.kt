package com.scanlist.app.di

import android.content.Context
import androidx.room.Room
import com.scanlist.app.data.db.ScanListDatabase
import com.scanlist.app.data.db.dao.ComparisonDao
import com.scanlist.app.data.db.dao.FolderDao
import com.scanlist.app.data.db.dao.RegexRuleDao
import com.scanlist.app.data.db.dao.ScanItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanListDatabase =
        Room.databaseBuilder(context, ScanListDatabase::class.java, ScanListDatabase.NAME)
            .build()

    @Provides
    fun provideFolderDao(db: ScanListDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideScanItemDao(db: ScanListDatabase): ScanItemDao = db.scanItemDao()

    @Provides
    fun provideRegexRuleDao(db: ScanListDatabase): RegexRuleDao = db.regexRuleDao()

    @Provides
    fun provideComparisonDao(db: ScanListDatabase): ComparisonDao = db.comparisonDao()
}
