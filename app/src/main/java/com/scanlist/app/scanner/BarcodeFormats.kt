package com.scanlist.app.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import com.scanlist.app.domain.model.ScanMode

/**
 * Table des symbologies reconnues, mode par mode.
 *
 * Isolee de [BarcodeAnalyzer] pour rester verifiable en test JVM. Le decodage reel
 * n'est exercable ni sur emulateur (la scene virtuelle ne contient aucun code) ni en
 * test instrumente : un format oublie dans cette table ne se verrait donc qu'au
 * premier inventaire rate. `BarcodeFormatsTest` est le seul filet qui verrouille le
 * contenu de ces masques, et surtout leur disjonction.
 */
internal object BarcodeFormats {

    /**
     * Codes lineaires : une seule rangee de barres, lue sur un seul axe.
     *
     * EAN, UPC et Code 128 portent leur propre cle de controle ; CODE_39 (sans cle)
     * et ITF n'en ont aucune, d'ou le filtre de conformite optionnel de la decision 20.
     */
    const val ONE_D = Barcode.FORMAT_EAN_13 or
        Barcode.FORMAT_EAN_8 or
        Barcode.FORMAT_UPC_A or
        Barcode.FORMAT_UPC_E or
        Barcode.FORMAT_CODE_39 or
        Barcode.FORMAT_CODE_93 or
        Barcode.FORMAT_CODE_128 or
        Barcode.FORMAT_ITF or
        Barcode.FORMAT_CODABAR

    /**
     * Codes 2D : matriciels (QR, Data Matrix, Aztec) et empiles (PDF417).
     *
     * PDF417 est le seul code multi-rangees que ML Kit sache lire. Les autres familles
     * empilees — Codablock F, Code 16K, Code 49, MicroPDF417, reconnaissables a leurs
     * barres de separation horizontales entre rangees — ne sont couvertes par aucun
     * moteur embarque gratuit : voir la limite connue de `consignes/workflow.md` § 8.
     *
     * Les quatre formats partagent une correction d'erreur Reed-Solomon, ce qui rend
     * une lecture erronee mais structurellement valide pratiquement impossible. C'est
     * ce qui justifie que la garde d'ambiguite du consensus reste desactivee sur ce
     * mode (decision 19), exactement comme du temps ou il ne portait que le QR Code.
     */
    const val TWO_D = Barcode.FORMAT_QR_CODE or
        Barcode.FORMAT_PDF417 or
        Barcode.FORMAT_DATA_MATRIX or
        Barcode.FORMAT_AZTEC

    /**
     * Masque a appliquer au decodeur pour [mode].
     *
     * Le filtrage se fait ici, dans les options de ML Kit, et jamais sur le resultat :
     * en mode Code-barres un PDF417 present dans le champ n'est meme pas decode, et
     * reciproquement. C'est cette disjonction qui garantit qu'une etiquette portant a
     * la fois un 1D et un 2D ne produit jamais deux lignes pour un seul objet.
     *
     * L'OCR et le NFC passent par d'autres chemins et n'atteignent jamais ce decodeur ;
     * leurs branches existent pour l'exhaustivite du `when`.
     */
    fun forMode(mode: ScanMode): Int = when (mode) {
        ScanMode.QR_CODE -> TWO_D
        ScanMode.BARCODE -> ONE_D
        ScanMode.OCR, ScanMode.NFC -> TWO_D
    }

    /** Nom affiche et exporte en colonne `symbologie` du .csv. */
    fun symbologyName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        else -> "AUTRE"
    }
}
