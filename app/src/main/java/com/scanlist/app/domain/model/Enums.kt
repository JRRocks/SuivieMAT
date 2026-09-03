package com.scanlist.app.domain.model

/**
 * Mode de scan, choisi explicitement avant chaque session.
 *
 * Les modes sont mutuellement exclusifs et appliques au decodeur lui-meme
 * (BarcodeScannerOptions), pas au resultat : en mode [QR_CODE] un code-barres
 * present dans le champ n'est meme pas decode. C'est la garantie anti-erreur.
 */
enum class ScanMode(val label: String) {
    OCR("OCR"),
    QR_CODE("QR Code"),
    BARCODE("Code-barres"),
    NFC("NFC/RFID"),
    ;

    companion object {
        fun fromNameOrNull(value: String?): ScanMode? =
            entries.firstOrNull { it.name == value }
    }
}

/** Nature d'un dossier : liste de scan simple ou resultat d'une session de comparaison. */
enum class FolderKind {
    SCAN,
    COMPARISON,
}

/** Origine de la liste de reference d'une session de comparaison. */
enum class CompareSourceKind {
    LOCAL_FOLDER,
    CSV_IMPORT,
}

/** Etat d'une entree de reference pendant une comparaison. */
enum class ReferenceState {
    MISSING,
    FOUND,
}

/**
 * Resultat de la confrontation d'une valeur scannee a la liste de reference.
 *
 * Code couleur impose par l'utilisateur, a ne pas modifier sans validation :
 * - [FOUND]    -> vert  : present dans la reference ET scanne
 * - [MISSING]  -> rouge : present dans la reference, jamais scanne
 * - [INTRUDER] -> gris  : scanne mais absent de la reference
 */
enum class MatchState(val label: String, val csvValue: String) {
    FOUND("Trouve", "TROUVE"),
    MISSING("Manquant", "MANQUANT"),
    INTRUDER("Intrus", "INTRUS"),
}
