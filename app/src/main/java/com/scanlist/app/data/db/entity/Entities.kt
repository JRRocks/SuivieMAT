package com.scanlist.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scanlist.app.domain.model.CompareSourceKind
import com.scanlist.app.domain.model.FolderKind
import com.scanlist.app.domain.model.ReferenceState
import com.scanlist.app.domain.model.ScanMode

/**
 * Les entites Room servent directement de modeles metier.
 *
 * Choix assume pour un projet de cette taille : une couche de mapping
 * entite <-> modele ajouterait plusieurs centaines de lignes sans benefice,
 * la logique metier etant isolee dans domain/ (CompareEngine, RegexRuleEngine).
 */

@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: FolderKind = FolderKind.SCAN,
    /** Dernier mode utilise : pre-selectionne par "Reprendre le scan". */
    val defaultScanMode: ScanMode = ScanMode.QR_CODE,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Une ligne = un code.
 *
 * L'index unique (folderId, value) applique la regle "pas de doublon, par dossier"
 * au niveau du moteur SQL, pas du code applicatif.
 */
@Entity(
    tableName = "scan_items",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["folderId", "value"], unique = true),
        Index(value = ["folderId"]),
    ],
)
data class ScanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    /** Valeur normalisee : c'est la cle de deduplication et de comparaison. */
    val value: String,
    /** Texte brut avant application de la regex, pour tracer une lecture OCR. */
    val rawValue: String,
    val mode: ScanMode,
    /** EAN_13, CODE_128, QR_CODE... null en OCR. */
    val symbology: String? = null,
    /** Regex ayant produit la valeur, en mode OCR. */
    val regexRuleId: Long? = null,
    val scannedAt: Long,
)

/** Formule regex saisie a la volee puis enregistree pour un futur scan OCR. */
@Entity(
    tableName = "regex_rules",
    indices = [Index(value = ["name"], unique = true)],
)
data class RegexRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pattern: String,
    /** 0 = match complet, n = n-ieme groupe capturant. */
    val groupIndex: Int = 0,
    val createdAt: Long,
    val lastUsedAt: Long,
)

@Entity(tableName = "comparison_sessions")
data class ComparisonSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceKind: CompareSourceKind,
    /** Id du dossier local, ou nom du fichier .csv importe. */
    val sourceRef: String,
    /** Dossier unique ou la session a ete enregistree, null tant qu'elle ne l'est pas. */
    val resultFolderId: Long? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "reference_entries",
    foreignKeys = [
        ForeignKey(
            entity = ComparisonSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "value"], unique = true),
        Index(value = ["sessionId"]),
    ],
)
data class ReferenceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val value: String,
    /** Colonne libre du CSV de reference (description du materiel). */
    val label: String? = null,
    val state: ReferenceState = ReferenceState.MISSING,
    val matchedAt: Long? = null,
)

/**
 * Table dormante.
 *
 * Elle recevait les valeurs lues hors de la liste de reference, du temps ou celles-ci
 * constituaient un troisieme etat. L'utilisateur a tranche : une valeur inconnue de la
 * liste n'est pas prise en compte, donc plus rien n'est ecrit ici.
 *
 * L'entite reste declaree pour ne pas changer l'empreinte du schema Room, ce qui
 * imposerait une migration destructive a l'application deja installee. A supprimer au
 * prochain changement de version de base.
 */
@Entity(
    tableName = "intruder_entries",
    foreignKeys = [
        ForeignKey(
            entity = ComparisonSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "value"], unique = true),
        Index(value = ["sessionId"]),
    ],
)
data class IntruderEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val value: String,
    val mode: ScanMode,
    val scannedAt: Long,
)

/** Projection d'un dossier enrichie de son nombre d'elements, pour la liste deroulante. */
data class FolderSummary(
    val id: Long,
    val name: String,
    val kind: FolderKind,
    val defaultScanMode: ScanMode,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(name = "itemCount") val itemCount: Int,
)
