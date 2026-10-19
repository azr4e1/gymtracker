package com.lorenzo.gymtracker.data.repository

import androidx.room.withTransaction
import com.lorenzo.gymtracker.data.SAMPLE_ROUTINE_PLANS
import com.lorenzo.gymtracker.data.db.AppDatabase
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.data.sampleWorkouts
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** Fills the app with made-up training history, for trying it out. Only offered in dev builds. */
class SampleDataRepository(private val database: AppDatabase) {
    /** Adds the sample routines and about three months of workouts. Returns the number of workouts added. */
    suspend fun addSampleData(): Int = database.withTransaction {
        val exercises = database.exerciseDao().observeAll().first().filter { !it.hidden }.associateBy { it.name }
        val existingRoutines = database.routineDao().observeAll().first().associate { it.routine.name to it.routine.id }
        val routineIds = SAMPLE_ROUTINE_PLANS.mapValues { (name, plan) ->
            existingRoutines[name] ?: database.routineDao().save(
                Routine(name = name),
                plan.mapNotNull { e ->
                    val exercise = exercises[e.name] ?: return@mapNotNull null
                    RoutineExercise(
                        routineId = 0, exerciseId = exercise.id, position = 0, timed = e.timed, sets = e.sets.size,
                        reps = e.sets.first().reps ?: exercise.reps, restSec = exercise.restSec,
                        durationSec = e.sets.first().durationSec ?: exercise.durationSec,
                    )
                },
            )
        }

        val dao = database.sessionDao()
        val zone = ZoneId.systemDefault()
        val workouts = sampleWorkouts(LocalDate.now())
        workouts.forEach { w ->
            val startedAt = w.date.atTime(w.start).atZone(zone).toInstant().toEpochMilli()
            val planned = w.exercises.mapNotNull { e -> exercises[e.name]?.let { e to it } }
            // Each set takes about 40 s (or its time), then the exercise's rest.
            val seconds = planned.sumOf { (e, exercise) -> e.sets.sumOf { (it.durationSec ?: 40) + exercise.restSec } }
            val sessionId = dao.insertSession(
                Session(routineId = routineIds[w.routine], name = w.routine, startedAt = startedAt, endedAt = startedAt + seconds * 1000L),
            )
            planned.forEachIndexed { index, (e, exercise) ->
                val entryId = dao.insertExercise(
                    SessionExercise(sessionId = sessionId, exerciseId = exercise.id, position = index, timed = e.timed, restSec = exercise.restSec),
                )
                dao.insertSets(
                    e.sets.mapIndexed { i, s ->
                        SessionSet(sessionExerciseId = entryId, position = i, reps = s.reps, weightKg = s.weightKg, durationSec = s.durationSec, done = true)
                    },
                )
            }
        }
        workouts.size
    }
}
