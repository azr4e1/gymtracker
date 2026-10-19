package com.lorenzo.gymtracker.data.repository

import com.lorenzo.gymtracker.data.DEFAULT_EXERCISES
import com.lorenzo.gymtracker.data.db.dao.ExerciseDao
import com.lorenzo.gymtracker.data.db.entity.Exercise
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {
    fun observeLibrary(): Flow<List<Exercise>> = dao.observeVisible()

    /** Including deleted exercises, which past workouts still refer to. */
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()

    fun observe(id: Long): Flow<Exercise?> = dao.observe(id)

    suspend fun get(id: Long): Exercise? = dao.get(id)

    suspend fun add(exercise: Exercise): Long = dao.insert(exercise.copy(id = 0))

    suspend fun update(exercise: Exercise) = dao.update(exercise)

    suspend fun delete(id: Long) = dao.delete(id)

    /** On a fresh install (or after deleting all data) the library starts with the default exercises. */
    suspend fun addDefaultsIfEmpty() {
        if (dao.count() == 0) dao.insertAll(DEFAULT_EXERCISES)
    }
}
