package com.scanlist.app.domain.regex

import com.scanlist.app.domain.model.ScanValue
import java.util.regex.PatternSyntaxException

/**
 * Compilation et application des formules regex du mode OCR.
 *
 * L'utilisateur ecrit sa formule a la volee depuis l'ecran de scan ; elle filtre le
 * texte reconnu pour ne retenir que le marquage utile. Chaque correspondance produit
 * une ligne distincte.
 *
 * Regle transverse du projet : sur erreur, ne rien faire. Une formule invalide ne
 * remonte jamais d'exception au pipeline camera, elle renvoie simplement zero resultat.
 */
object RegexRuleEngine {

    /**
     * Garde-fou : une etiquette entiere peut contenir des centaines de correspondances
     * si la formule est trop large. Au-dela, la frame est ignoree plutot que de noyer
     * la liste de l'utilisateur.
     */
    const val MAX_MATCHES_PER_FRAME = 50

    sealed interface Validation {
        data object Valid : Validation

        /** [message] est destine a l'affichage sous le champ de saisie. */
        data class Invalid(val message: String) : Validation
    }

    fun validate(pattern: String): Validation = when {
        pattern.isBlank() -> Validation.Invalid("Formule vide")
        else -> try {
            Regex(pattern)
            Validation.Valid
        } catch (e: PatternSyntaxException) {
            Validation.Invalid(frenchMessage(e))
        } catch (e: Exception) {
            Validation.Invalid("Formule invalide")
        }
    }

    /**
     * Traduit l'erreur de syntaxe de la JVM.
     *
     * [PatternSyntaxException] ne parle qu'anglais et n'est pas localisable : laisser
     * son message brut afficherait "Missing closing bracket in character class" dans
     * une interface entierement francaise. La position est conservee, c'est elle qui
     * permet de retrouver la faute dans la formule.
     */
    private fun frenchMessage(e: PatternSyntaxException): String {
        val description = e.description.orEmpty()
        val cause = when {
            description.contains("closing bracket", ignoreCase = true) ||
                description.contains("Unclosed character class", ignoreCase = true) ->
                "crochet ] manquant"

            description.contains("Unclosed group", ignoreCase = true) ->
                "parenthèse ) manquante"

            description.contains("Unmatched closing", ignoreCase = true) ->
                "parenthèse ) en trop"

            description.contains("Unclosed counted closure", ignoreCase = true) ->
                "accolade } manquante"

            description.contains("Illegal repetition", ignoreCase = true) ->
                "répétition mal formée"

            description.contains("Dangling meta character", ignoreCase = true) ->
                "caractère spécial mal placé"

            description.contains("escape", ignoreCase = true) ->
                "échappement invalide"

            description.contains("Unknown character property", ignoreCase = true) ->
                "propriété de caractère inconnue"

            description.contains("Unknown inline modifier", ignoreCase = true) ->
                "option inline inconnue"

            else -> "formule invalide"
        }
        val position = e.index
        return if (position >= 0) {
            "Erreur : $cause (position $position)"
        } else {
            "Erreur : $cause"
        }
    }

    fun compileOrNull(pattern: String): Regex? = try {
        if (pattern.isBlank()) null else Regex(pattern)
    } catch (e: Exception) {
        null
    }

    /**
     * Applique [regex] a [text] et renvoie les valeurs a enregistrer.
     *
     * [groupIndex] a 0 renvoie la correspondance complete, sinon le n-ieme groupe
     * capturant. Un groupe absent ou non capture pour une correspondance donnee fait
     * ignorer cette correspondance, sans interrompre les suivantes.
     *
     * Le resultat est dedoublonne en conservant l'ordre de lecture : un meme code lu
     * deux fois dans la meme image ne cree pas deux lignes.
     */
    fun extract(text: String, regex: Regex, groupIndex: Int = 0): List<String> {
        if (text.isBlank()) return emptyList()

        val results = LinkedHashSet<String>()
        var count = 0
        for (match in regex.findAll(text)) {
            if (++count > MAX_MATCHES_PER_FRAME) return emptyList()
            val raw = if (groupIndex == 0) {
                match.value
            } else {
                match.groupValues.getOrNull(groupIndex)
            } ?: continue
            val value = ScanValue.normalize(raw)
            if (value.isNotEmpty()) results.add(value)
        }
        return results.toList()
    }

    /** Variante tolerante : une formule invalide renvoie une liste vide. */
    fun extract(text: String, pattern: String, groupIndex: Int = 0): List<String> {
        val regex = compileOrNull(pattern) ?: return emptyList()
        return extract(text, regex, groupIndex)
    }

    /**
     * Verifie qu'une valeur deja delimitee (code-barres, QR Code) correspond en
     * totalite a la formule. A la difference de [extract], il ne s'agit pas d'isoler
     * un fragment dans du texte libre : la formule doit couvrir toute la valeur, pas
     * seulement en trouver un morceau. [regex] absente = aucun filtre, tout est
     * accepte, comme avant l'ajout de cette etape de conformite.
     */
    fun conforms(value: String, regex: Regex?): Boolean {
        if (regex == null) return true
        return regex.matches(ScanValue.normalize(value))
    }

    /**
     * Nombre de groupes capturants d'une formule, pour proposer le choix du groupe
     * dans l'editeur. Renvoie 0 si la formule est invalide.
     */
    fun capturingGroupCount(pattern: String): Int {
        val regex = compileOrNull(pattern) ?: return 0
        return regex.toPattern().matcher("").groupCount()
    }
}
