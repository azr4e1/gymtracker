package com.lorenzo.gymtracker.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val workouts = sampleWorkouts(today)

    @Test
    fun coversAboutThreeMonthsUpToYesterday() {
        assertTrue(workouts.first().date <= today.minusDays(90))
        assertTrue(workouts.all { it.date.isBefore(today) })
        // About three workouts a week, some missed.
        assertTrue("${workouts.size} workouts", workouts.size in 35..55)
    }

    @Test
    fun weightsGoUp() {
        val bench = workouts.flatMap { w -> w.exercises.filter { it.name == "Bench press" }.map { it.sets.first().weightKg!! } }
        assertTrue(bench.takeLast(3).average() > bench.take(3).average() + 5)
    }

    @Test
    fun sameSeedSameData() {
        assertEquals(workouts, sampleWorkouts(today))
    }

    @Test
    fun everyExerciseIsInTheDefaultLibrary() {
        val names = DEFAULT_EXERCISES.map { it.name }.toSet()
        val missing = SAMPLE_ROUTINE_PLANS.values.flatten().map { it.name }.filter { it !in names }
        assertTrue("Missing: $missing", missing.isEmpty())
    }
}
