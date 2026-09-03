package com.scanlist.app

import com.scanlist.app.scanner.ConsensusCandidate
import com.scanlist.app.scanner.ScanConsensus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConsensusTest {

    private fun candidate(value: String, symbology: String? = null) =
        listOf(ConsensusCandidate(value, symbology))

    @Test
    fun `en dessous du seuil rien n est valide`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        val secondAppel = consensus.submit(candidate("BAT-001"), now = 10)

        assertEquals(emptyList<ConsensusCandidate>(), secondAppel)
    }

    @Test
    fun `le seuil atteint valide la valeur`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)
        val troisieme = consensus.submit(candidate("BAT-001"), now = 20)

        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), troisieme)
    }

    @Test
    fun `la validation conserve la symbologie et le texte de la premiere lecture`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("bat-001", "EAN_13"), now = 0)
        consensus.submit(candidate("BAT-001", "EAN_13"), now = 10)
        val valide = consensus.submit(candidate("Bat-001", "EAN_13"), now = 20)

        // Meme cle (casse ignoree), mais c'est le texte de la toute premiere lecture
        // qui est conserve comme rawValue.
        assertEquals(listOf(ConsensusCandidate("bat-001", "EAN_13")), valide)
    }

    @Test
    fun `une valeur validee est aussitot purgee et peut redemarrer un nouveau consensus`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)
        consensus.submit(candidate("BAT-001"), now = 20)

        consensus.submit(candidate("BAT-001"), now = 30)
        val second = consensus.submit(candidate("BAT-001"), now = 40)

        assertEquals(emptyList<ConsensusCandidate>(), second)
    }

    @Test
    fun `une frame sans decodage ne casse pas le compteur en cours`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(emptyList(), now = 50)
        consensus.submit(emptyList(), now = 90)
        val troisieme = consensus.submit(candidate("BAT-001"), now = 120)

        // Deux lectures de BAT-001 seulement : le seuil de 3 n'est pas encore atteint,
        // mais les frames vides intercalees n'ont pas fait repartir le compteur a zero.
        assertEquals(emptyList<ConsensusCandidate>(), troisieme)
        val quatrieme = consensus.submit(candidate("BAT-001"), now = 150)
        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), quatrieme)
    }

    @Test
    fun `un compteur expire au bout du TTL sans nouvelle lecture`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false, ttlMs = 800)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 100)
        // Plus de 800 ms depuis la derniere lecture : le compteur repart de zero.
        consensus.submit(candidate("BAT-001"), now = 1_000)
        consensus.submit(candidate("BAT-001"), now = 1_100)
        val troisiemeDepuisRedemarrage = consensus.submit(candidate("BAT-001"), now = 1_200)

        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), troisiemeDepuisRedemarrage)
    }

    @Test
    fun `deux valeurs differentes progressent independamment`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        repeat(2) { i -> consensus.submit(candidate("BAT-001"), now = i * 10L) }
        val troisiemeA = consensus.submit(candidate("BAT-001"), now = 20)
        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), troisiemeA)

        // XYZ-999 n'a encore ete lu qu'une fois : il ne doit pas etre valide.
        val etatXyz = consensus.submit(candidate("XYZ-999"), now = 30)
        assertEquals(emptyList<ConsensusCandidate>(), etatXyz)
    }

    @Test
    fun `une valeur vide ou illisible n est jamais validee`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        repeat(5) { i -> consensus.submit(candidate("   "), now = i * 10L) }

        assertEquals(0f, consensus.peakProgress())
    }

    // -------------------------------------------------------- Garde d'ambiguite

    @Test
    fun `deux valeurs a une lettre pres qui n apparaissent jamais ensemble se reinitialisent`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = true)

        consensus.submit(candidate("AB123"), now = 0)
        // "AB1B3" arrive seul, jamais vu aux cotes de "AB123" : lecture ambigue d'une
        // meme etiquette, les deux compteurs repartent a zero.
        consensus.submit(candidate("AB1B3"), now = 10)

        // Il faut repartir de zero : deux lectures supplementaires de AB123 ne
        // suffisent pas a atteindre le seuil de 3.
        consensus.submit(candidate("AB123"), now = 20)
        val encoreInsuffisant = consensus.submit(candidate("AB123"), now = 30)
        assertEquals(emptyList<ConsensusCandidate>(), encoreInsuffisant)

        val troisiemeDepuisReset = consensus.submit(candidate("AB123"), now = 40)
        assertEquals(listOf(ConsensusCandidate("AB123", null)), troisiemeDepuisReset)
    }

    @Test
    fun `deux valeurs proches qui coexistent dans la meme image ne s annulent jamais`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = true)

        // Deux numeros de serie sequentiels, vus ensemble dans chaque image : ce sont
        // deux objets physiques distincts, pas une meme etiquette mal lue.
        val ensemble = listOf(ConsensusCandidate("47828"), ConsensusCandidate("47829"))
        consensus.submit(ensemble, now = 0)
        consensus.submit(ensemble, now = 10)
        val troisieme = consensus.submit(ensemble, now = 20)

        assertEquals(
            setOf(ConsensusCandidate("47828", null), ConsensusCandidate("47829", null)),
            troisieme.toSet(),
        )
    }

    @Test
    fun `la garde d ambiguite est desactivable, notamment pour le QR Code`() {
        val consensus = ScanConsensus(threshold = 2, ambiguityGuard = false)

        // Sans garde, deux valeurs a une lettre pres qui n'apparaissent jamais
        // ensemble ne s'influencent pas : chacune progresse pour son propre compte.
        consensus.submit(candidate("AAAA"), now = 0)
        consensus.submit(candidate("AAAB"), now = 10)
        val premiere = consensus.submit(candidate("AAAA"), now = 20)
        val seconde = consensus.submit(candidate("AAAB"), now = 30)

        assertEquals(listOf(ConsensusCandidate("AAAA", null)), premiere)
        assertEquals(listOf(ConsensusCandidate("AAAB", null)), seconde)
    }

    @Test
    fun `l ambiguite couvre aussi une insertion ou une suppression d un caractere`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = true)

        consensus.submit(candidate("BAT0001"), now = 0)
        // Un chiffre en moins : meme etiquette, lecture tronquee.
        consensus.submit(candidate("BAT001"), now = 10)

        consensus.submit(candidate("BAT0001"), now = 20)
        val insuffisant = consensus.submit(candidate("BAT0001"), now = 30)

        assertEquals(emptyList<ConsensusCandidate>(), insuffisant)
    }

    // --------------------------------------------------------- Avancement (reticule)

    @Test
    fun `peakProgress reflete le compteur le plus avance`() {
        val consensus = ScanConsensus(threshold = 4, ambiguityGuard = false)

        assertEquals(0f, consensus.peakProgress())

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)

        assertEquals(0.5f, consensus.peakProgress())
    }

    @Test
    fun `reset vide tous les compteurs et l avancement`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)
        consensus.reset()

        assertEquals(0f, consensus.peakProgress())
        val apresReset = consensus.submit(candidate("BAT-001"), now = 20)
        assertTrue(apresReset.isEmpty())
    }
}
