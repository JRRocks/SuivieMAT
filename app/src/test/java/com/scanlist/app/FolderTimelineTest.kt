package com.scanlist.app

import com.scanlist.app.domain.folders.ActivityMoment
import com.scanlist.app.domain.folders.FolderPeriod
import com.scanlist.app.domain.folders.FolderTimeline
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTimelineTest {

    private val paris = ZoneId.of("Europe/Paris")

    // Jeudi 29 octobre 2026, 15 h.
    private val jeudi = at(2026, 10, 29, 15, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(paris).toInstant().toEpochMilli()

    @Test
    fun `un dossier touche aujourd hui est range dans Aujourd hui avec son heure`() {
        val ceMatin = at(2026, 10, 29, 9, 5)
        assertEquals(FolderPeriod.TODAY, FolderTimeline.periodOf(ceMatin, jeudi, paris))
        assertEquals(ActivityMoment.Time(9, 5), FolderTimeline.momentOf(ceMatin, jeudi, paris))
    }

    @Test
    fun `hier reste dans la semaine et se dit hier`() {
        val hier = at(2026, 10, 28, 16, 20)
        assertEquals(FolderPeriod.THIS_WEEK, FolderTimeline.periodOf(hier, jeudi, paris))
        assertEquals(ActivityMoment.Yesterday, FolderTimeline.momentOf(hier, jeudi, paris))
    }

    @Test
    fun `un jour plus ancien de la meme semaine se dit par son nom`() {
        val lundi = at(2026, 10, 26, 8, 0)
        assertEquals(FolderPeriod.THIS_WEEK, FolderTimeline.periodOf(lundi, jeudi, paris))
        assertEquals(
            ActivityMoment.Weekday(DayOfWeek.MONDAY),
            FolderTimeline.momentOf(lundi, jeudi, paris),
        )
    }

    @Test
    fun `le dimanche precedent appartient a la semaine d avant`() {
        val dimanche = at(2026, 10, 25, 18, 0)
        assertEquals(FolderPeriod.OLDER, FolderTimeline.periodOf(dimanche, jeudi, paris))
        assertEquals(
            ActivityMoment.Day(LocalDate.of(2026, 10, 25)),
            FolderTimeline.momentOf(dimanche, jeudi, paris),
        )
    }

    @Test
    fun `le lundi matin le jeudi precedent n est plus dans cette semaine`() {
        // Une fenetre glissante de sept jours le rangerait dans "Cette semaine".
        val lundiMatin = at(2026, 11, 2, 8, 0)
        val jeudiPrecedent = at(2026, 10, 29, 10, 0)
        assertEquals(FolderPeriod.OLDER, FolderTimeline.periodOf(jeudiPrecedent, lundiMatin, paris))
    }

    @Test
    fun `le lundi la veille se dit hier meme rangee dans Plus ancien`() {
        val lundiMatin = at(2026, 11, 2, 8, 0)
        val dimanche = at(2026, 11, 1, 19, 0)
        assertEquals(FolderPeriod.OLDER, FolderTimeline.periodOf(dimanche, lundiMatin, paris))
        assertEquals(ActivityMoment.Yesterday, FolderTimeline.momentOf(dimanche, lundiMatin, paris))
    }

    @Test
    fun `une date d une autre annee porte son annee`() {
        val anneePassee = at(2025, 12, 31, 12, 0)
        assertEquals(
            ActivityMoment.DayOfOtherYear(LocalDate.of(2025, 12, 31)),
            FolderTimeline.momentOf(anneePassee, jeudi, paris),
        )
    }

    @Test
    fun `une date future reste en tete et affiche sa date`() {
        val demain = at(2026, 10, 30, 9, 0)
        assertEquals(FolderPeriod.TODAY, FolderTimeline.periodOf(demain, jeudi, paris))
        assertEquals(
            ActivityMoment.Day(LocalDate.of(2026, 10, 30)),
            FolderTimeline.momentOf(demain, jeudi, paris),
        )
    }

    @Test
    fun `le jour se juge dans le fuseau du telephone et non en UTC`() {
        // 23 h 30 en UTC le 28, c'est deja 0 h 30 le 29 a Paris.
        val minuitPasse = LocalDateTime.of(2026, 10, 28, 23, 30)
            .atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(FolderPeriod.TODAY, FolderTimeline.periodOf(minuitPasse, jeudi, paris))
    }

    @Test
    fun `le regroupement suit l ordre d affichage et omet les periodes vides`() {
        val dossiers = listOf(
            "aujourd'hui" to at(2026, 10, 29, 14, 2),
            "ancien" to at(2026, 10, 18, 9, 0),
            "ce matin" to at(2026, 10, 29, 8, 0),
        )
        val groupes = FolderTimeline.group(dossiers, { it.second }, jeudi, paris)

        assertEquals(listOf(FolderPeriod.TODAY, FolderPeriod.OLDER), groupes.map { it.first })
        // L'ordre de la base, le plus recent en tete, est conserve dans chaque periode.
        assertEquals(listOf("aujourd'hui", "ce matin"), groupes[0].second.map { it.first })
        assertEquals(listOf("ancien"), groupes[1].second.map { it.first })
    }
}
