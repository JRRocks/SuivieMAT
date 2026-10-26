package com.scanlist.app.domain.regex

import com.scanlist.app.domain.model.ScanValue

/**
 * Generateur de formules regex a partir d'exemples de valeurs, sans IA.
 *
 * Un marquage de materiel a une structure tres reguliere : des blocs de chiffres, de
 * lettres et quelques separateurs. Decouper un exemple en blocs suffit donc a deduire
 * plusieurs formules, de la plus stricte a la plus large. Un petit modele de langage
 * embarque aurait pese des centaines de Mo pour un resultat moins fiable : il se
 * trompe regulierement sur les quantificateurs, alors qu'ici chaque formule est
 * verifiee contre les exemples avant d'etre proposee.
 *
 * Aucun texte affichable ici : les commentaires et la description du format vivent
 * dans strings.xml, ce moteur ne renvoie que des donnees structurees.
 */
object RegexSuggester {

    /**
     * [LETTER] = majuscules et minuscules melangees, soit dans un meme bloc, soit
     * d'un exemple a l'autre.
     */
    enum class BlockType { DIGIT, UPPER, LOWER, LETTER, LITERAL }

    /**
     * Bloc homogene du format. [minLength] et [maxLength] different quand les
     * exemples n'ont pas tous la meme longueur pour ce bloc. [text] n'est renseigne
     * que pour un [BlockType.LITERAL], recopie tel quel dans la formule.
     */
    data class Block(
        val type: BlockType,
        val minLength: Int,
        val maxLength: Int,
        val text: String = "",
    )

    /** Ordre de declaration = ordre d'affichage, de la plus stricte a la plus large. */
    enum class Kind { STRICT, ANY_CASE, SEPARATORS, FIXED_PREFIX, FLEXIBLE }

    data class Suggestion(
        val kind: Kind,
        val pattern: String,
        /** Valeur que cette formule accepte et que la formule stricte refuse. */
        val alsoAccepts: String? = null,
        /** Valeur que la formule stricte accepte et que celle-ci refuse. */
        val rejects: String? = null,
        /** Debut recopie tel quel, pour [Kind.FIXED_PREFIX] uniquement. */
        val fixedPrefix: String? = null,
    )

    sealed interface Result {
        data object Empty : Result

        /** Exemples de structures differentes : aucune formule commune n'existe. */
        data object Incompatible : Result

        data class Found(val blocks: List<Block>, val suggestions: List<Suggestion>) : Result
    }

    /**
     * Separateurs usuels d'un marquage, que [Kind.SEPARATORS] rend facultatifs. Le
     * tiret en dernier : c'est sa seule place litterale dans une classe de caracteres.
     */
    private const val SEPARATORS = " ./_-"

    /** Toleres par [Kind.SEPARATORS] meme quand aucun exemple ne les contient. */
    private const val DEFAULT_SEPARATORS = " -"

    private const val REGEX_META = "\\^$.|?*+()[]{}"

    /**
     * Pas d'espace parmi les delimiteurs : un marquage peut en contenir un
     * (`12 123456`), il fait alors partie de la valeur.
     */
    private val EXAMPLE_DELIMITERS = charArrayOf(',', ';', '\n')

    private data class Token(val type: BlockType, val text: String)

    fun suggest(input: String): Result {
        // Meme normalisation qu'a l'enregistrement : la formule doit reconnaitre la
        // valeur telle qu'elle sera comparee, pas telle qu'elle a ete tapee.
        val examples = input.split(*EXAMPLE_DELIMITERS)
            .map(ScanValue::normalize)
            .filter { it.isNotEmpty() }
            .distinct()
        if (examples.isEmpty()) return Result.Empty

        val tokenized = examples.map(::tokenize)
        val blocks = merge(tokenized) ?: return Result.Incompatible

        val strict = bounded(blocks, blocks.joinToString("") { render(it) })
        // Ne devrait jamais echouer, tout etant echappe ; mais sur erreur, ne rien faire.
        val strictRegex = RegexRuleEngine.compileOrNull(strict) ?: return Result.Empty
        val first = tokenized.first()

        val candidates = listOfNotNull(
            Suggestion(Kind.STRICT, strict),
            anyCase(blocks, examples.first()),
            separators(blocks, first),
            fixedPrefix(blocks, tokenized),
            flexible(blocks, first),
        )

        // Garde-fou : une proposition qui ne reconnaitrait pas la valeur saisie par
        // l'utilisateur ne lui servirait a rien, quelle que soit la raison.
        val suggestions = candidates
            .filter { candidate ->
                val regex = RegexRuleEngine.compileOrNull(candidate.pattern)
                regex != null && examples.all { RegexRuleEngine.conforms(it, regex) }
            }
            .distinctBy { it.pattern }
            .map { verifiedIllustrations(it, strictRegex) }

        return Result.Found(blocks, suggestions)
    }

    // ------------------------------------------------------------ Decoupage

    /** Chiffres, lettres ASCII ou autre : un changement de famille ouvre un bloc. */
    private fun familyOf(c: Char): BlockType = when (c) {
        in '0'..'9' -> BlockType.DIGIT
        in 'A'..'Z', in 'a'..'z' -> BlockType.LETTER
        else -> BlockType.LITERAL
    }

    private fun tokenize(example: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var start = 0
        while (start < example.length) {
            val family = familyOf(example[start])
            var end = start + 1
            while (end < example.length && familyOf(example[end]) == family) end++
            val text = example.substring(start, end)
            val type = when {
                family != BlockType.LETTER -> family
                text.all { it in 'A'..'Z' } -> BlockType.UPPER
                text.all { it in 'a'..'z' } -> BlockType.LOWER
                else -> BlockType.LETTER
            }
            tokens += Token(type, text)
            start = end
        }
        return tokens
    }

    /**
     * Fusionne les exemples en un seul format, ou renvoie null s'ils n'ont pas la
     * meme structure. Seules la longueur d'un bloc et la casse de ses lettres peuvent
     * varier d'un exemple a l'autre ; un separateur ou un caractere special doit etre
     * identique partout.
     */
    private fun merge(examples: List<List<Token>>): List<Block>? {
        val size = examples.first().size
        if (examples.any { it.size != size }) return null

        return (0 until size).map { index ->
            val column = examples.map { it[index] }
            var type = column.first().type
            for (token in column) {
                type = mergeType(type, token.type) ?: return null
            }
            if (type == BlockType.LITERAL) {
                val text = column.first().text
                if (column.any { it.text != text }) return null
                Block(type, text.length, text.length, text)
            } else {
                Block(type, column.minOf { it.text.length }, column.maxOf { it.text.length })
            }
        }
    }

    private fun mergeType(a: BlockType, b: BlockType): BlockType? = when {
        a == b -> a
        isLetter(a) && isLetter(b) -> BlockType.LETTER
        else -> null
    }

    private fun isLetter(type: BlockType): Boolean =
        type == BlockType.UPPER || type == BlockType.LOWER || type == BlockType.LETTER

    // -------------------------------------------------------------- Rendu

    private fun classOf(type: BlockType): String = when (type) {
        BlockType.DIGIT -> "\\d"
        BlockType.UPPER -> "[A-Z]"
        BlockType.LOWER -> "[a-z]"
        BlockType.LETTER -> "[A-Za-z]"
        BlockType.LITERAL -> error("un litteral n'a pas de classe")
    }

    private fun quantifier(min: Int, max: Int): String = when {
        min != max -> "{$min,$max}"
        min == 1 -> ""
        else -> "{$min}"
    }

    private fun render(block: Block): String =
        if (block.type == BlockType.LITERAL) {
            escape(block.text)
        } else {
            classOf(block.type) + quantifier(block.minLength, block.maxLength)
        }

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            if (c in REGEX_META) append('\\')
            append(c)
        }
    }

    /**
     * Encadre la formule de `\b` pour qu'en OCR elle n'extraie pas un fragment d'un
     * mot plus long. Uniquement du cote ou le format commence ou finit par un chiffre
     * ou une lettre : `\b#` exigerait un caractere de mot avant le `#`, et la formule
     * ne reconnaitrait plus `#123` en debut de texte.
     */
    private fun bounded(blocks: List<Block>, body: String): String {
        val start = if (blocks.first().type != BlockType.LITERAL) "\\b" else ""
        val end = if (blocks.last().type != BlockType.LITERAL) "\\b" else ""
        return start + body + end
    }

    // ------------------------------------------------------------ Variantes

    private fun anyCase(blocks: List<Block>, example: String): Suggestion? {
        if (blocks.none { it.type == BlockType.UPPER || it.type == BlockType.LOWER }) return null
        val body = blocks.joinToString("") { block ->
            if (isLetter(block.type)) render(block.copy(type = BlockType.LETTER)) else render(block)
        }
        return Suggestion(Kind.ANY_CASE, bounded(blocks, body), alsoAccepts = swapCase(example))
    }

    /**
     * Separateur unique entre deux blocs : c'est lui que la variante rend facultatif.
     * Un separateur en bordure n'en est pas un, il reste recopie tel quel.
     */
    private fun isInnerSeparator(blocks: List<Block>, index: Int): Boolean {
        val block = blocks[index]
        return index > 0 && index < blocks.lastIndex &&
            block.type == BlockType.LITERAL && block.text.length == 1 &&
            block.text[0] in SEPARATORS
    }

    /** Deux blocs de chiffres ou de lettres colles : un separateur peut s'y glisser. */
    private fun isJunction(blocks: List<Block>, index: Int): Boolean =
        index > 0 && blocks[index].type != BlockType.LITERAL &&
            blocks[index - 1].type != BlockType.LITERAL

    private fun separators(blocks: List<Block>, first: List<Token>): Suggestion? {
        val used = blocks.indices
            .filter { isInnerSeparator(blocks, it) }
            .map { blocks[it].text[0] }
        val allowed = SEPARATORS.filter { it in DEFAULT_SEPARATORS || it in used }
        val separator = "[$allowed]?"

        val body = StringBuilder()
        val sample = StringBuilder()
        blocks.forEachIndexed { index, block ->
            if (isInnerSeparator(blocks, index)) {
                body.append(separator)
                sample.append(if (block.text == " ") '-' else ' ')
            } else {
                if (isJunction(blocks, index)) {
                    body.append(separator)
                    sample.append('-')
                }
                body.append(render(block))
                sample.append(first[index].text)
            }
        }
        return Suggestion(Kind.SEPARATORS, bounded(blocks, body.toString()), alsoAccepts = sample.toString())
    }

    /**
     * Tout est recopie tel quel sauf le dernier bloc : le cas courant d'un prefixe de
     * lot ou de modele suivi d'un numero de serie. Propose seulement si ce debut est
     * le meme dans tous les exemples, sinon il ne serait fixe qu'en apparence.
     */
    private fun fixedPrefix(blocks: List<Block>, tokenized: List<List<Token>>): Suggestion? {
        if (blocks.size < 2 || blocks.last().type == BlockType.LITERAL) return null
        val prefixBlocks = blocks.dropLast(1)
        if (prefixBlocks.all { it.type == BlockType.LITERAL }) return null

        val prefixes = tokenized.map { tokens -> tokens.dropLast(1).joinToString("") { it.text } }
        val prefix = prefixes.first()
        if (prefixes.any { it != prefix }) return null

        val body = escape(prefix) + render(blocks.last())

        // Contre-exemple : le meme format avec un debut different.
        val first = tokenized.first()
        val changed = prefixBlocks.indexOfFirst { it.type != BlockType.LITERAL }
        val rejects = first.mapIndexed { index, token ->
            if (index == changed) next(token.text[0]) + token.text.drop(1) else token.text
        }.joinToString("")

        return Suggestion(
            Kind.FIXED_PREFIX,
            bounded(blocks, body),
            rejects = rejects,
            fixedPrefix = prefix,
        )
    }

    private fun flexible(blocks: List<Block>, first: List<Token>): Suggestion? {
        if (blocks.all { it.type == BlockType.LITERAL }) return null
        val body = blocks.joinToString("") { block ->
            if (block.type == BlockType.LITERAL) escape(block.text) else classOf(block.type) + "+"
        }
        // Chaque bloc allonge au-dela du plus long exemple : la formule stricte le
        // refuse forcement, celle-ci l'accepte.
        val sample = first.mapIndexed { index, token ->
            val block = blocks[index]
            if (block.type == BlockType.LITERAL) token.text else extend(token.text, block.maxLength + 1)
        }.joinToString("")
        return Suggestion(Kind.FLEXIBLE, bounded(blocks, body), alsoAccepts = sample)
    }

    // ------------------------------------------------------- Illustrations

    private fun swapCase(text: String): String = text.map { c ->
        when (c) {
            in 'A'..'Z' -> c.lowercaseChar()
            in 'a'..'z' -> c.uppercaseChar()
            else -> c
        }
    }.joinToString("")

    /** Caractere suivant dans sa propre famille, en boucle : 9 -> 0, Z -> A. */
    private fun next(c: Char): Char = when (c) {
        in '0'..'9' -> '0' + (c - '0' + 1) % 10
        in 'A'..'Z' -> 'A' + (c - 'A' + 1) % 26
        in 'a'..'z' -> 'a' + (c - 'a' + 1) % 26
        else -> c
    }

    private fun extend(text: String, length: Int): String {
        val builder = StringBuilder(text)
        while (builder.length < length) builder.append(next(builder.last()))
        return builder.toString()
    }

    /**
     * Les valeurs d'illustration sont construites a la main : elles ne sont montrees
     * que si le moteur regex confirme ce qu'elles pretendent illustrer, faute de quoi
     * l'utilisateur lirait un "accepte aussi" faux.
     */
    private fun verifiedIllustrations(suggestion: Suggestion, strict: Regex): Suggestion {
        val regex = Regex(suggestion.pattern)
        val accepts = suggestion.alsoAccepts?.takeIf {
            RegexRuleEngine.conforms(it, regex) && !RegexRuleEngine.conforms(it, strict)
        }
        val rejects = suggestion.rejects?.takeIf {
            RegexRuleEngine.conforms(it, strict) && !RegexRuleEngine.conforms(it, regex)
        }
        return suggestion.copy(alsoAccepts = accepts, rejects = rejects)
    }
}
