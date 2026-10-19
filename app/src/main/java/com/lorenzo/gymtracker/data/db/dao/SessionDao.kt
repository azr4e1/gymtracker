package com.lorenzo.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import kotlinx.coroutines.flow.Flow

/** A ticked set with what's needed for progress and muscle stats. */
data class SetRecord(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseId: Long,
    val timed: Boolean,
    val reps: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
)

@Dao
abstract class SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract fun observeDetails(id: Long): Flow<SessionDetails?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract suspend fun getDetails(id: Long): SessionDetails?

    @Transaction
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActive(): Flow<SessionDetails?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    abstract suspend fun getActive(): Session?

    /** Finished workouts, newest first. */
    @Transaction
    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    abstract fun observeFinished(): Flow<List<SessionDetails>>

    /** Every ticked set, oldest workout first. */
    @Query(
        """
        SELECT s.id AS sessionId, s.startedAt, se.exerciseId, se.timed, st.reps, st.weightKg, st.durationSec
        FROM session_sets st
        JOIN session_exercises se ON st.sessionExerciseId = se.id
        JOIN sessions s ON se.sessionId = s.id
        WHERE st.done = 1
        ORDER BY s.startedAt, se.position, st.position
        """,
    )
    abstract fun observeDoneSets(): Flow<List<SetRecord>>

    /** The ticked sets of [exerciseId] in the latest workout other than [excludeSessionId] that has some. */
    @Query(
        """
        SELECT st.* FROM session_sets st
        JOIN session_exercises se ON st.sessionExerciseId = se.id
        WHERE st.done = 1 AND se.exerciseId = :exerciseId AND se.sessionId = (
            SELECT s.id FROM sessions s
            JOIN session_exercises se2 ON se2.sessionId = s.id
            JOIN session_sets st2 ON st2.sessionExerciseId = se2.id
            WHERE se2.exerciseId = :exerciseId AND st2.done = 1 AND s.id != :excludeSessionId
            ORDER BY s.startedAt DESC LIMIT 1
        )
        ORDER BY se.position, st.position
        """,
    )
    abstract suspend fun lastDoneSets(exerciseId: Long, excludeSessionId: Long): List<SessionSet>

    @Insert abstract suspend fun insertSession(session: Session): Long
    @Update abstract suspend fun updateSession(session: Session)
    @Query("DELETE FROM sessions WHERE id = :id") abstract suspend fun deleteSession(id: Long)

    @Insert abstract suspend fun insertExercise(exercise: SessionExercise): Long
    @Update abstract suspend fun updateExercises(exercises: List<SessionExercise>)
    @Delete abstract suspend fun deleteExercise(exercise: SessionExercise)

    @Insert abstract suspend fun insertSets(sets: List<SessionSet>)
    @Update abstract suspend fun updateSets(sets: List<SessionSet>)
    @Query("DELETE FROM session_sets WHERE id = :id") abstract suspend fun deleteSet(id: Long)

    // Single-column updates, so quick edits to different fields of a set never overwrite each other.
    @Query("UPDATE session_sets SET reps = :reps WHERE id = :id") abstract suspend fun setReps(id: Long, reps: Int?)
    @Query("UPDATE session_sets SET weightKg = :kg WHERE id = :id") abstract suspend fun setWeight(id: Long, kg: Double?)
    @Query("UPDATE session_sets SET durationSec = :sec WHERE id = :id") abstract suspend fun setDuration(id: Long, sec: Int?)
    @Query("UPDATE session_sets SET done = :done WHERE id = :id") abstract suspend fun setDone(id: Long, done: Boolean)
    @Query("UPDATE session_exercises SET restSec = :sec WHERE id = :id") abstract suspend fun setRest(id: Long, sec: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM session_exercises WHERE sessionId = :sessionId")
    abstract suspend fun maxExercisePosition(sessionId: Long): Int

    @Query("SELECT * FROM session_sets WHERE sessionExerciseId = :sessionExerciseId ORDER BY position")
    abstract suspend fun setsOf(sessionExerciseId: Long): List<SessionSet>

    @Query("DELETE FROM session_sets WHERE done = 0 AND sessionExerciseId IN (SELECT id FROM session_exercises WHERE sessionId = :sessionId)")
    protected abstract suspend fun deleteUndoneSets(sessionId: Long)

    @Query("UPDATE session_sets SET done = 1 WHERE sessionExerciseId IN (SELECT id FROM session_exercises WHERE sessionId = :sessionId)")
    protected abstract suspend fun markAllDone(sessionId: Long)

    @Query("DELETE FROM session_exercises WHERE sessionId = :sessionId AND id NOT IN (SELECT sessionExerciseId FROM session_sets)")
    protected abstract suspend fun deleteEmptyExercises(sessionId: Long)

    @Query("SELECT COUNT(*) FROM session_exercises WHERE sessionId = :sessionId")
    protected abstract suspend fun exerciseCount(sessionId: Long): Int

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    protected abstract suspend fun setEnded(id: Long, endedAt: Long)

    /**
     * Ends the workout. Unticked sets are counted as done with [keepUnticked], deleted otherwise;
     * exercises left without sets are removed. A workout left empty is deleted: returns false then.
     */
    @Transaction
    open suspend fun finish(sessionId: Long, keepUnticked: Boolean, endedAt: Long): Boolean {
        if (keepUnticked) markAllDone(sessionId) else deleteUndoneSets(sessionId)
        deleteEmptyExercises(sessionId)
        if (exerciseCount(sessionId) == 0) {
            deleteSession(sessionId)
            return false
        }
        setEnded(sessionId, endedAt)
        return true
    }
}
