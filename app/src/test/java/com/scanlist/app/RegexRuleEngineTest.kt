package com.scanlist.app

import com.scanlist.app.domain.regex.RegexRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexRuleEngineTest {

    @Test
    fun `une formule correcte est validee`() {
        assertTrue(RegexRuleEngine.validate("BAT-\\d{4}") is RegexRuleEngine.Validation.Valid)
    }

    @Test
    fun `une formule vide est refusee`() {
        assertTrue(RegexRuleEngine.validate("   ") is RegexRuleEngine.Validation.Invalid)
    }

    @Test
    fun `une formule syntaxiquement invalide est refusee sans lever d exception`() {
        assertTrue(RegexRuleEngine.validate("BAT-[0-9") is RegexRuleEngine.Validation.Invalid)
        assertNull(RegexRuleEngine.compileOrNull("BAT-[0-9"))
    }

    // ------------------------------------- Messages d'erreur en francais

    private fun messageFor(pattern: String): String =
        (RegexRuleEngine.validate(pattern) as RegexRuleEngine.Validation.Invalid).message

    @Test
    fun `un crochet non ferme est signale en francais avec sa position`() {
        val message = messageFor("BAT-[0-9")

        assertEquals("Erreur : crochet ] manquant (position 7)", message)
    }

    @Test
    fun `une parenthese non fermee est signalee en francais`() {
        assertTrue(messageFor("(BAT").contains("parenthèse ) manquante"))
    }

    @Test
    fun `une parenthese en trop est signalee en francais`() {
        assertTrue(messageFor("BAT)").contains("parenthèse ) en trop"))
    }

    @Test
    fun `un quantificateur en tete de formule est signale en francais`() {
        // La JVM classe ce cas en "Dangling meta character", pas en repetition.
        assertTrue(messageFor("*BAT").contains("caractère spécial mal placé"))
    }

    @Test
    fun `une accolade non fermee est signalee en francais`() {
        assertTrue(messageFor("BAT{2").contains("accolade } manquante"))
    }

    @Test
    fun `aucun message d erreur ne contient de texte anglais de la JVM`() {
        val formulesCassees = listOf("BAT-[0-9", "(BAT", "BAT)", "*BAT", "BAT{2", "\\p{Inconnu}")

        formulesCassees.forEach { formule ->
            val message = messageFor(formule)
            assertTrue(
                "Message non traduit pour \"$formule\" : $message",
                message.startsWith("Erreur : "),
            )
            listOf("Unclosed", "Unmatched", "Missing", "Illegal", "Dangling", "Unknown")
                .forEach { motAnglais ->
                    assertTrue(
                        "Anglais residuel dans : $message",
                        !message.contains(motAnglais),
                    )
                }
        }
    }

    @Test
    fun `une formule vide a son propre message`() {
        assertEquals("Formule vide", messageFor("   "))
    }

    @Test
    fun `chaque correspondance produit une valeur distincte`() {
        val texte = "Etiquette BAT-0001 puis BAT-0002 et enfin BAT-0003"

        val valeurs = RegexRuleEngine.extract(texte, "BAT-\\d{4}")

        assertEquals(listOf("BAT-0001", "BAT-0002", "BAT-0003"), valeurs)
    }

    @Test
    fun `le groupe capturant demande est extrait`() {
        val valeurs = RegexRuleEngine.extract("SN: AB1234 / SN: CD5678", "SN:\\s*(\\w+)", groupIndex = 1)

        assertEquals(listOf("AB1234", "CD5678"), valeurs)
    }

    @Test
    fun `une meme valeur lue deux fois dans l image ne cree qu une ligne`() {
        val valeurs = RegexRuleEngine.extract("BAT-0001 ... BAT-0001", "BAT-\\d{4}")

        assertEquals(listOf("BAT-0001"), valeurs)
    }

    @Test
    fun `les espaces parasites de l OCR sont normalises`() {
        val valeurs = RegexRuleEngine.extract("  BAT   0001  ", "BAT\\s+\\d{4}")

        assertEquals(listOf("BAT 0001"), valeurs)
    }

    @Test
    fun `une formule invalide ne remonte aucune erreur et ne capture rien`() {
        assertEquals(emptyList<String>(), RegexRuleEngine.extract("BAT-0001", "BAT-[0-9"))
    }

    @Test
    fun `un texte vide ne capture rien`() {
        assertEquals(emptyList<String>(), RegexRuleEngine.extract("   ", "\\w+"))
    }

    @Test
    fun `une formule trop large est neutralisee par le garde-fou`() {
        val texte = "a ".repeat(RegexRuleEngine.MAX_MATCHES_PER_FRAME + 10)

        assertEquals(emptyList<String>(), RegexRuleEngine.extract(texte, "a"))
    }

    @Test
    fun `un groupe absent d une correspondance ne bloque pas les suivantes`() {
        // Le groupe 1 n'est capture que pour la seconde correspondance.
        val valeurs = RegexRuleEngine.extract("REF / REF-9", "REF(?:-(\\d))?", groupIndex = 1)

        assertEquals(listOf("9"), valeurs)
    }

    @Test
    fun `capturingGroupCount compte les groupes de la formule`() {
        assertEquals(2, RegexRuleEngine.capturingGroupCount("(\\w+)-(\\d+)"))
        assertEquals(0, RegexRuleEngine.capturingGroupCount("BAT-[0-9"))
    }

    @Test
    fun `capturingGroupCount ignore les groupes non capturants`() {
        assertEquals(1, RegexRuleEngine.capturingGroupCount("(?:REF-)?(\\d+)"))
    }

    // ---------------------------------------------------------- Texte reel d'OCR

    /**
     * ML Kit restitue le texte bloc par bloc, separes par des retours a la ligne.
     * C'est la forme exacte de ce qui arrive dans [RegexRuleEngine.extract] en phase 5.
     */
    private val etiquetteLue = """
        ACME POWER SYSTEMS
        Batterie Li-Ion 48V
        S/N: BAT-2024-0117
        Fabrication 03/2024
        CE  RoHS
    """.trimIndent()

    @Test
    fun `une formule ciblee extrait le marquage au milieu du bruit de l etiquette`() {
        val valeurs = RegexRuleEngine.extract(etiquetteLue, "BAT-\\d{4}-\\d{4}")

        assertEquals(listOf("BAT-2024-0117"), valeurs)
    }

    @Test
    fun `un groupe capturant isole le numero de serie sans son prefixe`() {
        val valeurs = RegexRuleEngine.extract(etiquetteLue, "S/N:\\s*(\\S+)", groupIndex = 1)

        assertEquals(listOf("BAT-2024-0117"), valeurs)
    }

    @Test
    fun `une formule trop large ramene aussi le bruit, ce que l apercu doit montrer`() {
        val valeurs = RegexRuleEngine.extract(etiquetteLue, "\\d{4}")

        // L'annee de fabrication passe au filtre, et le 2024 du numero de serie est
        // dedoublonne. C'est precisement ce que l'utilisateur doit voir dans l'apercu
        // avant d'armer la capture.
        assertEquals(listOf("2024", "0117"), valeurs)
    }

    @Test
    fun `le point ne traverse pas les retours a la ligne des blocs OCR`() {
        val valeurs = RegexRuleEngine.extract(etiquetteLue, "SYSTEMS.*Batterie")

        assertEquals(emptyList<String>(), valeurs)
    }

    @Test
    fun `plusieurs etiquettes dans la meme image donnent une valeur chacune`() {
        val deuxEtiquettes = "S/N: BAT-2024-0117\nS/N: BAT-2024-0118"

        val valeurs = RegexRuleEngine.extract(deuxEtiquettes, "BAT-\\d{4}-\\d{4}")

        assertEquals(listOf("BAT-2024-0117", "BAT-2024-0118"), valeurs)
    }

    // ---------------------------------------- Conformite (QR Code, code-barres)

    @Test
    fun `l absence de formule accepte n importe quelle valeur`() {
        assertTrue(RegexRuleEngine.conforms("BAT-0001", regex = null))
        assertTrue(RegexRuleEngine.conforms("", regex = null))
    }

    @Test
    fun `une valeur qui correspond en totalite est conforme`() {
        assertTrue(RegexRuleEngine.conforms("8412345678901", RegexRuleEngine.compileOrNull("\\d{13}")))
    }

    @Test
    fun `une valeur qui ne correspond qu en partie n est pas conforme`() {
        // A la difference de extract, conforms exige la correspondance totale : un
        // code-barres plus long que la formule n'est pas un fragment valide.
        assertFalse(
            RegexRuleEngine.conforms("8412345678901X", RegexRuleEngine.compileOrNull("\\d{13}")),
        )
    }

    @Test
    fun `la conformite est verifiee sur la valeur normalisee`() {
        assertTrue(
            RegexRuleEngine.conforms("  BAT-0001  ", RegexRuleEngine.compileOrNull("BAT-\\d{4}")),
        )
    }
}
