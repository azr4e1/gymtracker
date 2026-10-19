package com.lorenzo.gymtracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutCountsTest {
    // 2026-10-21 is a Wednesday.
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun countsPerWeekStartingMonday() {
        val dates = listOf("2026-10-19", "2026-10-21", "2026-10-18", "2026-10-12", "2026-08-01").map(LocalDate::parse)
        val weeks = workoutCounts(dates, CountPeriod.WEEK, today, periods = 3)
        assertEquals(listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)), weeks.map { it.start })
        assertEquals(listOf(0, 2, 2), weeks.map { it.count })
    }

    @Test
    fun periodsBetweenCountsBothEnds() {
        assertEquals(1, CountPeriod.WEEK.periodsBetween(LocalDate.of(2026, 10, 19), today))
        assertEquals(2, CountPeriod.WEEK.periodsBetween(LocalDate.of(2026, 10, 18), today))
        assertEquals(13, CountPeriod.MONTH.periodsBetween(LocalDate.of(2025, 10, 31), today))
    }

    @Test
    fun countsPerMonth() {
        val dates = listOf("2026-10-01", "2026-10-31", "2026-09-15", "2025-10-20").map(LocalDate::parse)
        val months = workoutCounts(dates, CountPeriod.MONTH, today)
        assertEquals(12, months.size)
        assertEquals(LocalDate.of(2025, 11, 1), months.first().start)
        assertEquals(listOf(1, 2), months.takeLast(2).map { it.count })
        // October 2025 is just before the window.
        assertEquals(3, months.sumOf { it.count })
    }
}
