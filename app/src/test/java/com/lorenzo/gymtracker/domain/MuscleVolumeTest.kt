package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.dao.SetRecord
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleVolumeTest {
    private val zone = ZoneOffset.UTC
    private val bench = Exercise(id = 1, name = "Bench press", primaryMuscles = listOf(Muscle.CHEST), secondaryMuscles = listOf(Muscle.TRICEPS, Muscle.SHOULDERS))
    private val curl = Exercise(id = 2, name = "Curl", primaryMuscles = listOf(Muscle.BICEPS))
    private val exercises = listOf(bench, curl).associateBy { it.id }

    private fun set(date: LocalDate, exercise: Long) =
        SetRecord(1, date.atStartOfDay(zone).toInstant().toEpochMilli(), exercise, false, 8, 50.0, null)

    @Test
    fun helpingMusclesCountHalf() {
        val d = LocalDate.of(2026, 10, 14)
        val totals = muscleSets(listOf(set(d, 1), set(d, 1), set(d, 1), set(d, 2)), exercises)
        assertEquals(3.0, totals.getValue(Muscle.CHEST), 1e-9)
        assertEquals(1.5, totals.getValue(Muscle.TRICEPS), 1e-9)
        assertEquals(1.0, totals.getValue(Muscle.BICEPS), 1e-9)
        assertNull(totals[Muscle.QUADS])
    }

    @Test
    fun thisWeekStartsOnMonday() {
        // 2026-10-19 is a Monday.
        assertEquals(LocalDate.of(2026, 10, 19), MusclePeriod.THIS_WEEK.start(LocalDate.of(2026, 10, 21)))
        assertEquals(LocalDate.of(2026, 10, 19), MusclePeriod.THIS_WEEK.start(LocalDate.of(2026, 10, 19)))
        assertEquals(LocalDate.of(2026, 9, 24), MusclePeriod.FOUR_WEEKS.start(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun earlierPeriodsEndBeforeTheNextStarts() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals(today, MusclePeriod.THIS_WEEK.end(0, today))
        // The week before this one: Monday 12 – Sunday 18 Oct.
        assertEquals(LocalDate.of(2026, 10, 18), MusclePeriod.THIS_WEEK.end(1, today))
        assertEquals(LocalDate.of(2026, 10, 12), MusclePeriod.THIS_WEEK.start(MusclePeriod.THIS_WEEK.end(1, today)))
        // Four weeks back: the period right before the current 24 Sep – 21 Oct.
        assertEquals(LocalDate.of(2026, 9, 23), MusclePeriod.FOUR_WEEKS.end(1, today))
    }

    @Test
    fun longerPeriodsAveragePerWeek() {
        val today = LocalDate.of(2026, 10, 21)
        val sets = List(8) { set(today.minusDays(it * 3L), 2) } + set(today.minusDays(40), 2)
        val weekly = weeklyMuscleSets(sets, exercises, MusclePeriod.FOUR_WEEKS, today, zone)
        assertEquals(2.0, weekly.getValue(Muscle.BICEPS), 1e-9)
        val thisWeek = weeklyMuscleSets(sets, exercises, MusclePeriod.THIS_WEEK, today, zone)
        assertEquals(1.0, thisWeek.getValue(Muscle.BICEPS), 1e-9)
    }

    @Test
    fun lastTrainedIsTheLatestDay() {
        val sets = listOf(set(LocalDate.of(2026, 10, 1), 1), set(LocalDate.of(2026, 10, 9), 1), set(LocalDate.of(2026, 10, 5), 2))
        val last = lastTrained(sets, exercises, zone)
        assertEquals(LocalDate.of(2026, 10, 9), last[Muscle.TRICEPS])
        assertEquals(LocalDate.of(2026, 10, 5), last[Muscle.BICEPS])
    }
}
