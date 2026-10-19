package com.lorenzo.gymtracker.data.repository

import androidx.room.withTransaction
import com.lorenzo.gymtracker.data.db.AppDatabase
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.dao.SetRecord
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Workouts: starting, tracking sets, finishing, and the history behind progress. */
class SessionRepository(private val database: AppDatabase) {
    private val dao = database.sessionDao()
    private val exerciseDao = database.exerciseDao()

    fun observe(id: Long): Flow<SessionDetails?> = dao.observeDetails(id).map { it?.sorted() }

    fun observeActive(): Flow<SessionDetails?> = dao.observeActive().map { it?.sorted() }

    fun observeFinished(): Flow<List<SessionDetails>> = dao.observeFinished().map { list -> list.map { it.sorted() } }

    fun observeDoneSets(): Flow<List<SetRecord>> = dao.observeDoneSets()

    suspend fun getActive(): Session? = dao.getActive()

    suspend fun get(id: Long): SessionDetails? = dao.getDetails(id)?.sorted()

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmpty(name: String = "Workout"): Long =
        dao.getActive()?.id ?: dao.insertSession(Session(name = name))

    suspend fun startRoutine(routine: RoutineDetails): Long =
        startRoutine(routine.routine, routine.exercises.map { it.entry to it.exercise })

    /**
     * Starts a workout from [routine], with each exercise's sets pre-filled from the plan. A
     * planned weight left empty is taken from the same set last time; exercises that don't track
     * weight get none.
     */
    suspend fun startRoutine(routine: Routine, exercises: List<Pair<RoutineExercise, Exercise>>): Long =
        database.withTransaction {
            dao.getActive()?.let { return@withTransaction it.id }
            val sessionId = dao.insertSession(Session(routineId = routine.id, name = routine.name))
            exercises.forEachIndexed { index, (planned, exercise) ->
                val timed = planned.timed
                val entryId = dao.insertExercise(
                    SessionExercise(sessionId = sessionId, exerciseId = planned.exerciseId, position = index, timed = timed, restSec = planned.restSec),
                )
                val last = dao.lastDoneSets(planned.exerciseId, excludeSessionId = sessionId)
                dao.insertSets(
                    List(planned.sets.coerceAtLeast(1)) { i ->
                        SessionSet(
                            sessionExerciseId = entryId,
                            position = i,
                            reps = planned.reps.takeUnless { timed },
                            weightKg = if (exercise.weighted) planned.weightKg ?: last.getOrNull(i)?.weightKg ?: last.lastOrNull()?.weightKg else null,
                            durationSec = planned.durationSec.takeIf { timed },
                        )
                    },
                )
            }
            sessionId
        }

    /**
     * Adds exercises to a workout, by reps or by time as set on the exercise. Each gets the sets
     * done last time, or the exercise's defaults if it's new.
     */
    suspend fun addExercises(sessionId: Long, exerciseIds: List<Long>) = database.withTransaction {
        val exercises = exerciseDao.getByIds(exerciseIds).associateBy { it.id }
        var position = dao.maxExercisePosition(sessionId) + 1
        for (id in exerciseIds) {
            val exercise = exercises[id] ?: continue
            val timed = exercise.timed
            val entryId = dao.insertExercise(
                SessionExercise(sessionId = sessionId, exerciseId = id, position = position++, timed = timed, restSec = exercise.restSec),
            )
            val last = dao.lastDoneSets(id, excludeSessionId = sessionId)
            val sets = last.ifEmpty {
                List(exercise.sets.coerceAtLeast(1)) { SessionSet(sessionExerciseId = 0, position = 0) }
            }
            dao.insertSets(
                sets.mapIndexed { i, s ->
                    SessionSet(
                        sessionExerciseId = entryId,
                        position = i,
                        reps = if (timed) null else s.reps ?: exercise.reps,
                        weightKg = s.weightKg.takeIf { exercise.weighted },
                        durationSec = if (timed) s.durationSec ?: exercise.durationSec else null,
                    )
                },
            )
        }
    }

    /** Adds a set like the exercise's last one. */
    suspend fun addSet(entry: SessionExercise, exercise: Exercise) {
        val sets = dao.setsOf(entry.id)
        val last = sets.lastOrNull()
        dao.insertSets(
            listOf(
                SessionSet(
                    sessionExerciseId = entry.id,
                    position = (last?.position ?: -1) + 1,
                    reps = if (entry.timed) null else last?.reps ?: exercise.reps,
                    weightKg = last?.weightKg,
                    durationSec = if (entry.timed) last?.durationSec ?: exercise.durationSec else null,
                ),
            ),
        )
    }

    suspend fun deleteSet(setId: Long) = dao.deleteSet(setId)

    suspend fun setReps(setId: Long, reps: Int?) = dao.setReps(setId, reps)

    suspend fun setWeight(setId: Long, kg: Double?) = dao.setWeight(setId, kg)

    suspend fun setDuration(setId: Long, sec: Int?) = dao.setDuration(setId, sec)

    suspend fun setDone(setId: Long, done: Boolean) = dao.setDone(setId, done)

    suspend fun setRest(entryId: Long, sec: Int) = dao.setRest(entryId, sec)

    /** Switches an exercise between reps and time, filling the new value from its defaults. */
    suspend fun setTimed(entry: SessionExercise, exercise: Exercise, timed: Boolean) = database.withTransaction {
        dao.updateExercises(listOf(entry.copy(timed = timed)))
        dao.updateSets(
            dao.setsOf(entry.id).map { s ->
                if (timed) s.copy(reps = null, durationSec = s.durationSec ?: exercise.durationSec)
                else s.copy(durationSec = null, reps = s.reps ?: exercise.reps)
            },
        )
    }

    /** Moves an exercise one place up ([by] -1) or down (+1). */
    suspend fun move(sessionId: Long, entryId: Long, by: Int) = database.withTransaction {
        val entries = dao.getDetails(sessionId)?.sorted()?.exercises?.map { it.entry } ?: return@withTransaction
        val from = entries.indexOfFirst { it.id == entryId }
        val to = from + by
        if (from < 0 || to !in entries.indices) return@withTransaction
        val reordered = entries.toMutableList().apply { add(to, removeAt(from)) }
        dao.updateExercises(reordered.mapIndexed { i, e -> e.copy(position = i) })
    }

    suspend fun removeExercise(entry: SessionExercise) = dao.deleteExercise(entry)

    suspend fun rename(session: Session, name: String) = dao.updateSession(session.copy(name = name))

    /** See [com.lorenzo.gymtracker.data.db.dao.SessionDao.finish]. Returns false if nothing was kept. */
    suspend fun finish(sessionId: Long, keepUnticked: Boolean): Boolean =
        dao.finish(sessionId, keepUnticked, System.currentTimeMillis())

    suspend fun delete(sessionId: Long) = dao.deleteSession(sessionId)
}
