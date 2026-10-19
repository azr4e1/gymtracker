package com.lorenzo.gymtracker.data.repository

import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.dao.RoutineDao
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoutineRepository(private val dao: RoutineDao) {
    fun observeAll(): Flow<List<RoutineDetails>> = dao.observeAll().map { list -> list.map { it.sorted() } }

    suspend fun get(id: Long): RoutineDetails? = dao.get(id)?.sorted()

    /** Saves the routine with [exercises] in this order. Returns its id. */
    suspend fun save(routine: Routine, exercises: List<RoutineExercise>): Long = dao.save(routine, exercises)

    suspend fun delete(id: Long) = dao.delete(id)
}
