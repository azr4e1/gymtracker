package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.dao.SetRecord
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private val zone = ZoneOffset.UTC

    private fun day(n: Int) = LocalDate.of(2026, 10, 1).plusDays(n.toLong())

    private fun set(session: Long, day: Int, kg: Double?, reps: Int?, exercise: Long = 1, duration: Int? = null) = SetRecord(
        sessionId = session,
        startedAt = day(day).atStartOfDay(zone).toInstant().toEpochMilli(),
        exerciseId = exercise,
        timed = duration != null,
        reps = reps,
        weightKg = kg,
        durationSec = duration,
    )

    @Test
    fun epleyEstimate() {
        assertEquals(100.0, estimatedOneRepMax(100.0, 1)!!, 1e-9)
        assertEquals(120.0, estimatedOneRepMax(100.0, 6)!!, 1e-9)
        assertNull(estimatedOneRepMax(0.0, 8))
        assertNull(estimatedOneRepMax(60.0, 0))
    }

    @Test
    fun metricsDependOnWhatWasLogged() {
        assertEquals(listOf(Metric.ONE_REP_MAX, Metric.MAX_WEIGHT, Metric.VOLUME), availableMetrics(listOf(set(1, 0, 60.0, 8))))
        assertEquals(listOf(Metric.MAX_REPS, Metric.TOTAL_REPS), availableMetrics(listOf(set(1, 0, null, 12))))
        assertEquals(listOf(Metric.LONGEST, Metric.TOTAL_TIME), availableMetrics(listOf(set(1, 0, null, null, duration = 45))))
        assertTrue(availableMetrics(emptyList()).isEmpty())
    }

    @Test
    fun onePointPerWorkout() {
        val sets = listOf(set(1, 0, 60.0, 8), set(1, 0, 60.0, 6), set(2, 3, 62.5, 8))
        val volume = sessionPoints(sets, Metric.VOLUME, zone)
        assertEquals(listOf(840.0, 500.0), volume.map { it.value })
        assertEquals(listOf(day(0), day(3)), volume.map { it.date })
        assertEquals(listOf(60.0, 62.5), sessionPoints(sets, Metric.MAX_WEIGHT, zone).map { it.value })
    }

    @Test
    fun recordsKeepTheBestWorkout() {
        val sets = listOf(set(1, 0, 80.0, 3), set(2, 3, 70.0, 10))
        val records = records(sets, zone).associateBy { it.metric }
        assertEquals(80.0, records.getValue(Metric.MAX_WEIGHT).value, 1e-9)
        assertEquals(1L, records.getValue(Metric.MAX_WEIGHT).sessionId)
        // 70 × (1 + 10/30) = 93.3 beats 80 × 1.1 = 88.
        assertEquals(2L, records.getValue(Metric.ONE_REP_MAX).sessionId)
    }

    @Test
    fun newRecordOnlyWhenBeatingEarlierWorkouts() {
        val sets = listOf(set(1, 0, 60.0, 8), set(2, 3, 62.5, 8), set(3, 5, 62.5, 6))
        assertTrue(newRecords(sets, 1).isEmpty())
        // Heavier weight: shown instead of the (also improved) estimated max.
        assertEquals(listOf(Metric.MAX_WEIGHT), newRecords(sets, 2))
        assertTrue(newRecords(sets, 3).isEmpty())
    }

    @Test
    fun timedRecords() {
        val sets = listOf(set(1, 0, null, null, duration = 45), set(2, 2, null, null, duration = 60))
        assertEquals(listOf(Metric.LONGEST), newRecords(sets, 2))
    }
}
