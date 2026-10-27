package com.scanlist.app.ui.navigation

import com.scanlist.app.domain.model.ScanMode

/**
 * Routes de navigation.
 *
 * Transcription de la hierarchie de menus specifiee par l'utilisateur. Depuis la
 * decision 28, le niveau MENU PRINCIPAL est porte par la barre de navigation du bas :
 * ses trois branches en sont les onglets, et l'application s'ouvre sur Nouveau scan.
 *
 * ```
 * BARRE DE NAVIGATION
 * |- DOSSIERS (liste deroulante)
 * |   |- Consulter -> Lancer la fonction Comparer
 * |   |- Exporter en .csv -> nom du fichier / preferences d'envoi
 * |   |- Reprendre le scan -> OCR | Code 2D | Code-barres | NFC/RFID
 * |- COMPARER
 * |   |- Depuis un dossier local -> Lancer le scan (OCR | Code 2D | Code-barres | NFC/RFID)
 * |   |- Depuis un import .csv   -> Lancer le scan (OCR | Code 2D | Code-barres | NFC/RFID)
 * |- NOUVEAU SCAN (ecran d'ouverture)
 *     |- OCR | Code 2D | Code-barres | NFC/RFID
 * ```
 */
object Routes {

    // --- Branche Dossiers ---
    const val FOLDERS = "folders"
    const val FOLDER_ACTIONS = "folders/{folderId}/actions"
    const val FOLDER_DETAIL = "folders/{folderId}/detail"
    const val FOLDER_EXPORT = "folders/{folderId}/export"
    const val FOLDER_RESUME_MODE = "folders/{folderId}/resume"

    // --- Branche Comparer ---
    const val COMPARE_SOURCE = "compare/source"
    const val COMPARE_PICK_FOLDER = "compare/source/folder"
    const val COMPARE_SESSION = "compare/{sessionId}/session"
    const val COMPARE_SCAN_MODE = "compare/{sessionId}/mode"

    // --- Branche Nouveau scan ---
    const val NEW_SCAN_MODE = "scan/new"

    /**
     * Ecran de scan, unique pour les quatre modes et les deux contextes.
     *
     * [folderId] alimente une liste de scan ; [sessionId] alimente une comparaison.
     * Exactement l'un des deux est renseigne.
     */
    const val SCAN = "scan/{mode}?folderId={folderId}&sessionId={sessionId}"

    // --- Reglages ---
    const val EMAIL_PREFS = "settings/email"

    const val ARG_FOLDER_ID = "folderId"
    const val ARG_SESSION_ID = "sessionId"
    const val ARG_MODE = "mode"

    /** Valeur d'argument signifiant "non renseigne" (les arguments Long ne sont pas nullables). */
    const val NO_ID = -1L

    fun folderActions(folderId: Long) = "folders/$folderId/actions"

    fun folderDetail(folderId: Long) = "folders/$folderId/detail"

    fun folderExport(folderId: Long) = "folders/$folderId/export"

    fun folderResumeMode(folderId: Long) = "folders/$folderId/resume"

    fun compareSession(sessionId: Long) = "compare/$sessionId/session"

    fun compareScanMode(sessionId: Long) = "compare/$sessionId/mode"

    /** Scan alimentant un dossier. */
    fun scanIntoFolder(mode: ScanMode, folderId: Long) =
        "scan/${mode.name}?folderId=$folderId&sessionId=$NO_ID"

    /** Scan alimentant une session de comparaison. */
    fun scanIntoSession(mode: ScanMode, sessionId: Long) =
        "scan/${mode.name}?folderId=$NO_ID&sessionId=$sessionId"

    /** Nouveau scan : le dossier sera cree au premier code capture. */
    fun scanNew(mode: ScanMode) = "scan/${mode.name}?folderId=$NO_ID&sessionId=$NO_ID"
}
