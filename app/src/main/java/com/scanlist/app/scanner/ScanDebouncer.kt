package com.scanlist.app.scanner

import com.scanlist.app.domain.model.ScanValue

/**
 * Anti-rafale du scan a la chaine.
 *
 * A 30 images par seconde, un code reste dans le champ pendant des dizaines de frames
 * et serait soumis autant de fois. La deduplication en base suffirait a l'ecarter, mais
 * elle declencherait un feedback "deja scanne" en continu, ce qui rendrait l'ecran
 * illisible. Ce filtre laisse passer une valeur, puis l'ignore pendant [windowMs].
 *
 * Il ne remplace pas la deduplication : il evite le bruit, l'index unique tranche.
 *
 * Classe non thread-safe : elle est appelee depuis l'unique thread d'analyse CameraX.
 */
class ScanDebouncer(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    private val lastSeen = HashMap<String, Long>()

    /**
     * Renvoie true si la valeur doit etre traitee, false si elle vient d'etre vue.
     * La comparaison utilise la meme cle que la deduplication et la fonction Comparer.
     */
    fun accept(rawValue: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!ScanValue.isUsable(rawValue)) return false

        prune(now)
        val key = ScanValue.key(rawValue)
        val previous = lastSeen[key]
        if (previous != null && now - previous < windowMs) return false

        lastSeen[key] = now
        return true
    }

    /** Filtre une detection multi-codes en conservant l'ordre de lecture. */
    fun acceptAll(rawValues: List<String>, now: Long = System.currentTimeMillis()): List<String> =
        rawValues.filter { accept(it, now) }

    fun reset() = lastSeen.clear()

    /**
     * Purge les entrees sorties de la fenetre. Sans cela, une session de plusieurs
     * milliers de codes ferait croitre la table indefiniment.
     */
    private fun prune(now: Long) {
        if (lastSeen.size < PRUNE_THRESHOLD) return
        lastSeen.entries.removeAll { now - it.value >= windowMs }
    }

    companion object {
        /**
         * 1,5 s : assez long pour couvrir le temps ou un code reste visible apres
         * capture, assez court pour rescanner volontairement un meme code.
         */
        const val DEFAULT_WINDOW_MS = 1_500L
        private const val PRUNE_THRESHOLD = 64
    }
}
