package com.scanlist.app

import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.domain.regex.RegexSuggester
import com.scanlist.app.domain.regex.RegexSuggester.BlockType
import com.scanlist.app.domain.regex.RegexSuggester.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexSuggesterTest {

    private fun found(input: String): RegexSuggester.Result.Found =
        RegexSuggester.suggest(input) as RegexSuggester.Result.Found

    private fun suggestion(input: String, kind: Kind): RegexSuggester.Suggestion? =
        found(input).suggestions.firstOrNull { it.kind == kind }

    private fun pattern(input: String, kind: Kind): String? = suggestion(input, kind)?.pattern

    private fun conforms(value: String, pattern: String?): Boolean =
        RegexRuleEngine.conforms(value, RegexRuleEngine.compileOrNull(pattern!!))

    // ------------------------------------------------------------ Decoupage

    @Test
    fun `un exemple est decoupe en blocs de chiffres et de lettres`() {
        val blocs = found("1GH546").blocks

        assertEquals(listOf(BlockType.DIGIT, BlockType.UPPER, BlockType.DIGIT), blocs.map { it.type })
        assertEquals(listOf(1, 2, 3), blocs.map { it.minLength })
    }

    @Test
    fun `la formule stricte decrit exactement l exemple`() {
        assertEquals("\\b\\d[A-Z]{2}\\d{3}\\b", pattern("1GH546", Kind.STRICT))
    }

    @Test
    fun `la formule stricte est toujours proposee en premier`() {
        assertEquals(Kind.STRICT, found("1GH546").suggestions.first().kind)
    }

    @Test
    fun `un separateur de l exemple est recopie dans la formule stricte`() {
        // C'est la formule HK416 prechargee dans la bibliotheque.
        assertEquals("\\b\\d{2}-\\d{6}\\b", pattern("12-123456", Kind.STRICT))
    }

    @Test
    fun `les caracteres speciaux de regex sont echappes`() {
        val formule = pattern("REF.12(A)", Kind.STRICT)

        assertEquals("\\b[A-Z]{3}\\.\\d{2}\\([A-Z]\\)", formule)
        assertTrue(conforms("REF.12(A)", formule))
        assertFalse(conforms("REFX12(A)", formule))
    }

    @Test
    fun `pas de borne de mot a cote d un caractere qui n est ni chiffre ni lettre`() {
        val formule = pattern("#123", Kind.STRICT)

        assertEquals("#\\d{3}\\b", formule)
        assertEquals(listOf("#123"), RegexRuleEngine.extract("Ref #123 ok", formule!!))
    }

    @Test
    fun `les espaces parasites de l exemple sont normalises`() {
        assertEquals("\\b\\d[A-Z]{2} \\d{3}\\b", pattern("  1GH   546 ", Kind.STRICT))
    }

    @Test
    fun `des lettres melangees dans un meme bloc acceptent les deux casses`() {
        assertEquals("\\b[A-Za-z]{2}\\d\\b", pattern("Ab1", Kind.STRICT))
    }

    // ------------------------------------------------------------ Variantes

    @Test
    fun `la variante sans casse accepte aussi les minuscules`() {
        val variante = suggestion("1GH546", Kind.ANY_CASE)!!

        assertEquals("\\b\\d[A-Za-z]{2}\\d{3}\\b", variante.pattern)
        assertEquals("1gh546", variante.alsoAccepts)
    }

    @Test
    fun `la variante separateurs tolere tiret espace ou rien entre les blocs`() {
        val variante = suggestion("1GH546", Kind.SEPARATORS)!!

        assertEquals("\\b\\d[ -]?[A-Z]{2}[ -]?\\d{3}\\b", variante.pattern)
        assertEquals("1-GH-546", variante.alsoAccepts)
        listOf("1GH546", "1-GH-546", "1 GH 546", "1GH-546").forEach {
            assertTrue(it, conforms(it, variante.pattern))
        }
    }

    @Test
    fun `la variante separateurs rend facultatif le separateur de l exemple`() {
        val variante = suggestion("12-123456", Kind.SEPARATORS)!!

        assertEquals("\\b\\d{2}[ -]?\\d{6}\\b", variante.pattern)
        assertEquals("12 123456", variante.alsoAccepts)
        assertTrue(conforms("12123456", variante.pattern))
    }

    @Test
    fun `un separateur inhabituel de l exemple rejoint les separateurs toleres`() {
        assertEquals("\\b\\d{2}[ /-]?\\d{4}\\b", pattern("03/2024", Kind.SEPARATORS))
    }

    @Test
    fun `le debut fixe ne laisse varier que le dernier bloc`() {
        val variante = suggestion("1GH546", Kind.FIXED_PREFIX)!!

        assertEquals("\\b1GH\\d{3}\\b", variante.pattern)
        assertEquals("1GH", variante.fixedPrefix)
        assertEquals("2GH546", variante.rejects)
        assertTrue(conforms("1GH999", variante.pattern))
    }

    @Test
    fun `la variante longueurs libres accepte des blocs plus longs`() {
        val variante = suggestion("1GH546", Kind.FLEXIBLE)!!

        assertEquals("\\b\\d+[A-Z]+\\d+\\b", variante.pattern)
        assertEquals("12GHI5467", variante.alsoAccepts)
    }

    @Test
    fun `un exemple sans lettre ne propose ni variante de casse ni debut fixe`() {
        assertEquals(listOf(Kind.STRICT, Kind.FLEXIBLE), found("12345").suggestions.map { it.kind })
    }

    @Test
    fun `aucune formule n est proposee deux fois`() {
        listOf("1GH546", "12345", "12-123456", "1GH546, 2ab123", "#123", "--").forEach { saisie ->
            val formules = found(saisie).suggestions.map { it.pattern }
            assertEquals(saisie, formules.distinct(), formules)
        }
    }

    // ------------------------------------------------------- Garde-fous

    private val saisiesVariees = listOf(
        "1GH546",
        "HK416",
        "12-123456",
        "BAT-2024-0117",
        "03/2024",
        "SN: AB1234",
        "REF.12(A)",
        "#123",
        "Ab1",
        "1GH546, 1GH5467",
        "1GH546, 2ab123",
        "Z9",
    )

    @Test
    fun `toutes les propositions reconnaissent chaque exemple saisi`() {
        saisiesVariees.forEach { saisie ->
            val exemples = saisie.split(',').map { it.trim() }
            found(saisie).suggestions.forEach { proposition ->
                exemples.forEach { exemple ->
                    assertTrue(
                        "${proposition.kind} ${proposition.pattern} refuse $exemple",
                        conforms(exemple, proposition.pattern),
                    )
                }
            }
        }
    }

    @Test
    fun `toutes les propositions extraient l exemple d un texte OCR`() {
        saisiesVariees.filter { ',' !in it }.forEach { exemple ->
            found(exemple).suggestions.forEach { proposition ->
                assertEquals(
                    "${proposition.kind} ${proposition.pattern}",
                    listOf(exemple),
                    RegexRuleEngine.extract("Etiquette : $exemple\nFabrication", proposition.pattern),
                )
            }
        }
    }

    @Test
    fun `les valeurs d illustration disent vrai`() {
        saisiesVariees.forEach { saisie ->
            val propositions = found(saisie).suggestions
            val stricte = propositions.first { it.kind == Kind.STRICT }.pattern
            propositions.forEach { proposition ->
                proposition.alsoAccepts?.let {
                    assertTrue(it, conforms(it, proposition.pattern))
                    assertFalse(it, conforms(it, stricte))
                }
                proposition.rejects?.let {
                    assertTrue(it, conforms(it, stricte))
                    assertFalse(it, conforms(it, proposition.pattern))
                }
            }
        }
    }

    @Test
    fun `la formule stricte ne capture pas un fragment de mot plus long`() {
        val formule = pattern("1GH546", Kind.STRICT)!!

        assertEquals(emptyList<String>(), RegexRuleEngine.extract("X1GH5467", formule))
    }

    // ------------------------------------------------- Plusieurs exemples

    @Test
    fun `des exemples de longueurs differentes donnent une plage`() {
        assertEquals("\\b\\d[A-Z]{2}\\d{3,4}\\b", pattern("1GH546, 1GH5467", Kind.STRICT))
    }

    @Test
    fun `une plage est allongee au dela du plus long exemple pour l illustration`() {
        assertEquals("12GHI54678", suggestion("1GH546, 1GH5467", Kind.FLEXIBLE)!!.alsoAccepts)
    }

    @Test
    fun `des lettres de casse differente entre exemples acceptent les deux casses`() {
        val propositions = found("1GH546, 2ab123").suggestions

        assertEquals("\\b\\d[A-Za-z]{2}\\d{3}\\b", propositions.first().pattern)
        assertTrue(propositions.none { it.kind == Kind.ANY_CASE })
    }

    @Test
    fun `les exemples peuvent etre separes par virgule point-virgule ou retour a la ligne`() {
        val attendu = "\\b\\d[A-Z]{2}\\d{3,5}\\b"

        assertEquals(attendu, pattern("1GH546;1GH5467\n1GH54678", Kind.STRICT))
    }

    @Test
    fun `le debut fixe n est propose que s il est commun a tous les exemples`() {
        assertNull(suggestion("1GH546, 2GH546", Kind.FIXED_PREFIX))
        assertEquals("\\b1GH\\d{3,4}\\b", pattern("1GH546, 1GH5467", Kind.FIXED_PREFIX))
    }

    @Test
    fun `des exemples de formats differents sont signales incompatibles`() {
        assertEquals(RegexSuggester.Result.Incompatible, RegexSuggester.suggest("1GH546, AB-12"))
        assertEquals(RegexSuggester.Result.Incompatible, RegexSuggester.suggest("12-123456, 12 123456"))
    }

    @Test
    fun `une saisie vide ne propose rien`() {
        assertEquals(RegexSuggester.Result.Empty, RegexSuggester.suggest(""))
        assertEquals(RegexSuggester.Result.Empty, RegexSuggester.suggest("  , ;\n "))
    }
}
