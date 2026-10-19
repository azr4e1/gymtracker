package com.lorenzo.gymtracker.data.backup

import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import kotlinx.serialization.Serializable

/** Everything in the database, as written to a backup JSON file. */
@Serializable
data class Backup(
    /** Database schema version the backup was taken from. */
    val schemaVersion: Int,
    val exportedAt: String,
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val routineExercises: List<RoutineExercise> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val sessionExercises: List<SessionExercise> = emptyList(),
    val sessionSets: List<SessionSet> = emptyList(),
)
