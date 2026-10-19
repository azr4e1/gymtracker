package com.lorenzo.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<RoutineDetails>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    abstract suspend fun get(id: Long): RoutineDetails?

    @Insert
    protected abstract suspend fun insert(routine: Routine): Long

    @Update
    protected abstract suspend fun update(routine: Routine)

    @Insert
    protected abstract suspend fun insertExercises(items: List<RoutineExercise>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    protected abstract suspend fun clearExercises(routineId: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Creates ([Routine.id] 0) or replaces a routine and its exercises, in this order. Returns its id. */
    @Transaction
    open suspend fun save(routine: Routine, exercises: List<RoutineExercise>): Long {
        val id = if (routine.id == 0L) insert(routine) else routine.id.also { update(routine) }
        clearExercises(id)
        insertExercises(exercises.mapIndexed { i, e -> e.copy(id = 0, routineId = id, position = i) })
        return id
    }
}
