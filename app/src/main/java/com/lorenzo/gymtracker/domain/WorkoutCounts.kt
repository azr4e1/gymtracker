package com.lorenzo.gymtracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How workouts are grouped in the history chart. */
enum class CountPeriod {
    WEEK,
    MONTH,
}

/** Number of workouts in the week or month starting [start]. */
data class PeriodCount(val start: LocalDate, val count: Int)

/** The first day of the week (Monday) or month containing [date]. */
fun CountPeriod.startOf(date: LocalDate): LocalDate = when (this) {
    CountPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    CountPeriod.MONTH -> date.withDayOfMonth(1)
}

/** How many periods from the one with [first] to the one with [today], both included. */
fun CountPeriod.periodsBetween(first: LocalDate, today: LocalDate): Int = when (this) {
    CountPeriod.WEEK -> ChronoUnit.WEEKS.between(startOf(first), startOf(today)).toInt() + 1
    CountPeriod.MONTH -> ChronoUnit.MONTHS.between(startOf(first), startOf(today)).toInt() + 1
}

/** Workouts per period for the last [periods] periods up to and including the one with [today], oldest first. */
fun workoutCounts(dates: List<LocalDate>, period: CountPeriod, today: LocalDate, periods: Int = 12): List<PeriodCount> {
    val counts = dates.groupingBy { period.startOf(it) }.eachCount()
    val current = period.startOf(today)
    return (periods - 1 downTo 0).map { back ->
        val start = when (period) {
            CountPeriod.WEEK -> current.minusWeeks(back.toLong())
            CountPeriod.MONTH -> current.minusMonths(back.toLong())
        }
        PeriodCount(start, counts[start] ?: 0)
    }
}
