package com.scanlist.app.domain.compare

import com.scanlist.app.domain.model.MatchState
import com.scanlist.app.domain.model.ScanValue

/**
 * Moteur de comparaison, charge en memoire pour une session.
 *
 * Il donne un verdict immediat sans aller-retour en base : le pipeline camera doit
 * rester libre pendant le scan a la chaine. La persistance suit de maniere asynchrone.
 *
 * Regle posee par l'utilisateur : la comparaison valide la presence physique des
 * elements de la liste de reference, et rien d'autre. La liste nait entierement en
 * rouge, chaque entree passe au vert quand elle est lue. Une valeur lue qui ne figure
 * pas dans la liste n'est pas prise en compte — ni comptee, ni affichee, ni enregistree.
 */
class CompareEngine(
    reference: Collection<String>,
    private val caseSensitive: Boolean = false,
) {

    /** Cle normalisee -> valeur de reference telle qu'elle sera affichee. */
    private val reference: Map<String, String> = LinkedHashMap<String, String>().apply {
        reference.forEach { raw ->
            val display = ScanValue.normalize(raw)
            if (display.isNotEmpty()) putIfAbsent(ScanValue.key(raw, caseSensitive), display)
        }
    }

    private val foundKeys = LinkedHashSet<String>()

    val referenceCount: Int get() = reference.size
    val foundCount: Int get() = foundKeys.size
    val missingCount: Int get() = reference.size - foundKeys.size

    /** Valeurs de reference jamais lues : affichage rouge. */
    val missingValues: List<String>
        get() = reference.entries.filterNot { it.key in foundKeys }.map { it.value }

    /** Valeurs de reference confirmees : affichage vert. */
    val foundValues: List<String>
        get() = foundKeys.mapNotNull { reference[it] }

    /**
     * Confronte une valeur lue a la liste de reference.
     *
     * Une valeur vide ou illisible ne declenche rien : c'est la regle "cas d'erreur,
     * ne rien faire" appliquee au niveau du moteur.
     */
    fun submit(rawValue: String): CompareOutcome {
        if (!ScanValue.isUsable(rawValue)) return CompareOutcome.Ignored

        val key = ScanValue.key(rawValue, caseSensitive)
        val referenceValue = reference[key]

        return when {
            referenceValue == null -> CompareOutcome.OutOfList(ScanValue.normalize(rawValue))
            !foundKeys.add(key) -> CompareOutcome.AlreadyMatched(referenceValue)
            else -> CompareOutcome.Matched(referenceValue)
        }
    }

    /**
     * Etat courant d'une valeur donnee.
     * [MatchState.INTRUDER] signale ici une valeur etrangere a la liste, sans etat propre.
     */
    fun stateOf(value: String): MatchState {
        val key = ScanValue.key(value, caseSensitive)
        return when {
            key !in reference -> MatchState.INTRUDER
            key in foundKeys -> MatchState.FOUND
            else -> MatchState.MISSING
        }
    }

    /** Restaure les valeurs deja confirmees, pour reprendre une session enregistree. */
    fun restoreFound(values: Collection<String>) {
        values.forEach { raw ->
            val key = ScanValue.key(raw, caseSensitive)
            if (key in reference) foundKeys.add(key)
        }
    }
}

/** Verdict d'une lecture pendant une session de comparaison. */
sealed interface CompareOutcome {

    /** Correspondance : l'entree de reference passe du rouge au vert. */
    data class Matched(val referenceValue: String) : CompareOutcome

    /** Deja confirmee lors d'un passage precedent : aucune ecriture supplementaire. */
    data class AlreadyMatched(val referenceValue: String) : CompareOutcome

    /**
     * Valeur absente de la liste de reference.
     *
     * Elle n'est pas prise en compte : rien n'est enregistre, rien n'est compte. La
     * valeur n'est portee que pour le bandeau qui previent l'utilisateur, le temps
     * d'une seconde, qu'il vient de lire quelque chose d'etranger a son controle.
     */
    data class OutOfList(val value: String) : CompareOutcome

    /** Valeur vide ou illisible : ne rien faire. */
    data object Ignored : CompareOutcome
}
