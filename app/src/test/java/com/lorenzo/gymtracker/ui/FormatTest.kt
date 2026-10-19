package com.lorenzo.gymtracker.ui

import com.lorenzo.gymtracker.data.db.entity.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private fun set(kg: Double?, reps: Int?, sec: Int? = null) = SessionSet(sessionExerciseId = 0, position = 0, reps = reps, weightKg = kg, durationSec = sec)

    @Test
    fun clock() {
        assertEquals("0:45", formatClock(45))
        assertEquals("2:00", formatClock(120))
        assertEquals("1:02:03", formatClock(3723))
    }

    @Test
    fun durations() {
        assertEquals("45 min", formatDuration(45 * 60_000L))
        assertEquals("1 h 05 min", formatDuration(65 * 60_000L))
    }

    @Test
    fun weightsKeepTwoDecimals() {
        assertEquals("60", 60.0.formatWeight())
        assertEquals("62.5", 62.5.formatWeight())
        assertEquals("43.75", 43.75.formatWeight())
        // 135 lb in kg.
        assertEquals("61.23", 61.2349.formatWeight())
    }

    @Test
    fun compactNumbers() {
        assertEquals("950", 950.0.formatCompact())
        assertEquals("1.2K", 1240.0.formatCompact())
        assertEquals("3K", 3000.0.formatCompact())
        assertEquals("15K", 15_400.0.formatCompact())
    }

    @Test
    fun setSummaries() {
        assertEquals("60 kg × 8, 8, 6", summarizeSets(listOf(set(60.0, 8), set(60.0, 8), set(60.0, 6)), timed = false))
        assertEquals("60 × 8, 62.5 × 6", summarizeSets(listOf(set(60.0, 8), set(62.5, 6)), timed = false))
        assertEquals("12, 10 reps", summarizeSets(listOf(set(null, 12), set(null, 10)), timed = false))
        assertEquals("0:45, 0:40", summarizeSets(listOf(set(null, null, 45), set(null, null, 40)), timed = true))
        assertEquals("20 kg · 0:45, 0:40", summarizeSets(listOf(set(20.0, null, 45), set(20.0, null, 40)), timed = true))
    }
}
