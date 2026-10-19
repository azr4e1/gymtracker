package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRunTest {
    private fun exercise(id: Long, timed: Boolean, vararg done: Boolean) = SessionExerciseDetails(
        SessionExercise(id = id, sessionId = 1, exerciseId = id, position = id.toInt(), timed = timed, restSec = 30),
        Exercise(id = id, name = "E$id", primaryMuscles = listOf(Muscle.ABS), timed = timed),
        done.mapIndexed { i, d -> SessionSet(id = id * 10 + i, sessionExerciseId = id, position = i, durationSec = 30, done = d) },
    )

    private fun workout(vararg exercises: SessionExerciseDetails) = SessionDetails(Session(id = 1, name = "W"), exercises.toList())

    @Test
    fun startsFromTheFirstUntickedSet() {
        val next = firstUnticked(workout(exercise(1, true, true, true), exercise(2, true, true, false, false)))!!
        assertEquals(21L, next.set.id)
        assertTrue(next.canRun)
    }

    @Test
    fun aRepsExerciseCantRun() {
        val next = firstUnticked(workout(exercise(1, true, true), exercise(2, false, false), exercise(3, true, false)))!!
        assertEquals("E2", next.exercise.exercise.name)
        assertFalse(next.canRun)
    }

    @Test
    fun onceTheRepsSetsAreTickedItContinues() {
        val next = firstUnticked(workout(exercise(1, true, true), exercise(2, false, true, true), exercise(3, true, false)))!!
        assertEquals("E3", next.exercise.exercise.name)
        assertTrue(next.canRun)
    }

    @Test
    fun nothingLeftWhenEverythingIsTicked() {
        assertNull(firstUnticked(workout(exercise(1, true, true), exercise(2, false, true))))
    }
}
