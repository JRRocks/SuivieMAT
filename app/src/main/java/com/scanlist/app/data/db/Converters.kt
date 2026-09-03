package com.scanlist.app.data.db

import androidx.room.TypeConverter
import com.scanlist.app.domain.model.CompareSourceKind
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.domain.model.ScanMode

/**
 * Les enums sont stockees sous leur nom et non leur ordinal : ajouter une valeur
 * plus tard ne corrompt pas les lignes existantes.
 */
class Converters {

    @TypeConverter
    fun scanModeToString(value: ScanMode): String = value.name

    @TypeConverter
    fun stringToScanMode(value: String): ScanMode = ScanMode.valueOf(value)

    @TypeConverter
    fun folderKindToString(value: FolderKind): String = value.name

    @TypeConverter
    fun stringToFolderKind(value: String): FolderKind = FolderKind.valueOf(value)

    @TypeConverter
    fun sourceKindToString(value: CompareSourceKind): String = value.name

    @TypeConverter
    fun stringToSourceKind(value: String): CompareSourceKind = CompareSourceKind.valueOf(value)

    @TypeConverter
    fun referenceStateToString(value: ReferenceState): String = value.name

    @TypeConverter
    fun stringToReferenceState(value: String): ReferenceState = ReferenceState.valueOf(value)
}
