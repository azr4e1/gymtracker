package com.lorenzo.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lorenzo.gymtracker.data.backup.Backup
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet

/** Bulk read and replace of every table, for backup and restore. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM exercises") abstract suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM routines") abstract suspend fun routines(): List<Routine>
    @Query("SELECT * FROM routine_exercises") abstract suspend fun routineExercises(): List<RoutineExercise>
    @Query("SELECT * FROM sessions") abstract suspend fun sessions(): List<Session>
    @Query("SELECT * FROM session_exercises") abstract suspend fun sessionExercises(): List<SessionExercise>
    @Query("SELECT * FROM session_sets") abstract suspend fun sessionSets(): List<SessionSet>

    @Query("DELETE FROM session_sets") protected abstract suspend fun clearSessionSets()
    @Query("DELETE FROM session_exercises") protected abstract suspend fun clearSessionExercises()
    @Query("DELETE FROM sessions") protected abstract suspend fun clearSessions()
    @Query("DELETE FROM routine_exercises") protected abstract suspend fun clearRoutineExercises()
    @Query("DELETE FROM routines") protected abstract suspend fun clearRoutines()
    @Query("DELETE FROM exercises") protected abstract suspend fun clearExercises()

    @Insert protected abstract suspend fun insertExercises(items: List<Exercise>)
    @Insert protected abstract suspend fun insertRoutines(items: List<Routine>)
    @Insert protected abstract suspend fun insertRoutineExercises(items: List<RoutineExercise>)
    @Insert protected abstract suspend fun insertSessions(items: List<Session>)
    @Insert protected abstract suspend fun insertSessionExercises(items: List<SessionExercise>)
    @Insert protected abstract suspend fun insertSessionSets(items: List<SessionSet>)

    /** Deletes every row in every table, children first. All or nothing. */
    @Transaction
    open suspend fun deleteAll() {
        clearSessionSets()
        clearSessionExercises()
        clearSessions()
        clearRoutineExercises()
        clearRoutines()
        clearExercises()
    }

    /** Deletes everything and inserts the backup's rows, keeping their ids. All or nothing. */
    @Transaction
    open suspend fun replaceAll(backup: Backup) {
        deleteAll()
        insertExercises(backup.exercises)
        insertRoutines(backup.routines)
        insertRoutineExercises(backup.routineExercises)
        insertSessions(backup.sessions)
        insertSessionExercises(backup.sessionExercises)
        insertSessionSets(backup.sessionSets)
    }
}
