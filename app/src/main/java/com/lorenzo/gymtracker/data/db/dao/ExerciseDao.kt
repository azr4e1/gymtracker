package com.lorenzo.gymtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lorenzo.gymtracker.data.db.entity.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExerciseDao {
    /** The library: every exercise not deleted, by name. */
    @Query("SELECT * FROM exercises WHERE hidden = 0 ORDER BY name COLLATE NOCASE")
    abstract fun observeVisible(): Flow<List<Exercise>>

    /** Including deleted ones, for labelling past workouts. */
    @Query("SELECT * FROM exercises")
    abstract fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    abstract fun observe(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    abstract suspend fun get(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    abstract suspend fun getByIds(ids: List<Long>): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(exercise: Exercise): Long

    @Insert
    abstract suspend fun insertAll(exercises: List<Exercise>)

    @Update
    abstract suspend fun update(exercise: Exercise)

    @Query("UPDATE exercises SET hidden = 1 WHERE id = :id")
    protected abstract suspend fun setHidden(id: Long)

    @Query("DELETE FROM routine_exercises WHERE exerciseId = :id")
    protected abstract suspend fun removeFromRoutines(id: Long)

    /** Hides the exercise from the library and removes it from routines; past workouts keep it. */
    @Transaction
    open suspend fun delete(id: Long) {
        removeFromRoutines(id)
        setHidden(id)
    }
}
