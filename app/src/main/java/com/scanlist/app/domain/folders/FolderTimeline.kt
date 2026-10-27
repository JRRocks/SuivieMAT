package com.scanlist.app.domain.folders

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Periode d'activite d'un dossier : la liste des dossiers est rangee par periode
 * (etape 5 de la refonte, choix de l'utilisateur). L'ordre des constantes est celui
 * de l'affichage.
 */
enum class FolderPeriod { TODAY, THIS_WEEK, OLDER }

/**
 * Moment d'une activite, dit de la facon la plus courte qui reste sans ambiguite :
 * l'heure pour aujourd'hui, "hier", le jour de la semaine, puis la date.
 */
sealed interface ActivityMoment {
    data class Time(val hour: Int, val minute: Int) : ActivityMoment
    data object Yesterday : ActivityMoment
    data class Weekday(val day: DayOfWeek) : ActivityMoment
    data class Day(val date: LocalDate) : ActivityMoment
    data class DayOfOtherYear(val date: LocalDate) : ActivityMoment
}

/**
 * Classement des dossiers dans le temps, pur et testable en JVM.
 *
 * La semaine commence le lundi, comme au calendrier francais : "Cette semaine" ne
 * contient jamais le jeudi de la semaine precedente, ce qu'une fenetre glissante de
 * sept jours ferait le lundi matin.
 */
object FolderTimeline {

    fun periodOf(timestamp: Long, now: Long, zone: ZoneId): FolderPeriod {
        val day = dateOf(timestamp, zone)
        val today = dateOf(now, zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when {
            // Une date future (horloge du telephone recalee) reste en tete plutot que
            // de disparaitre au fond de la liste.
            !day.isBefore(today) -> FolderPeriod.TODAY
            !day.isBefore(monday) -> FolderPeriod.THIS_WEEK
            else -> FolderPeriod.OLDER
        }
    }

    /**
     * Regroupe par periode, dans l'ordre d'affichage, sans periode vide. L'ordre des
     * elements a l'interieur d'une periode est conserve : celui de la base, le plus
     * recent en tete.
     */
    fun <T> group(
        items: List<T>,
        timestampOf: (T) -> Long,
        now: Long,
        zone: ZoneId,
    ): List<Pair<FolderPeriod, List<T>>> {
        val byPeriod = items.groupBy { periodOf(timestampOf(it), now, zone) }
        return FolderPeriod.entries.mapNotNull { period -> byPeriod[period]?.let { period to it } }
    }

    fun momentOf(timestamp: Long, now: Long, zone: ZoneId): ActivityMoment {
        val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
        val day = dateTime.toLocalDate()
        val today = dateOf(now, zone)
        return when {
            day == today -> ActivityMoment.Time(dateTime.hour, dateTime.minute)
            day == today.minusDays(1) -> ActivityMoment.Yesterday
            day.isBefore(today) && periodOf(timestamp, now, zone) == FolderPeriod.THIS_WEEK ->
                ActivityMoment.Weekday(day.dayOfWeek)
            day.year == today.year -> ActivityMoment.Day(day)
            else -> ActivityMoment.DayOfOtherYear(day)
        }
    }

    private fun dateOf(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
