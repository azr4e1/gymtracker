package com.lorenzo.gymtracker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A workout plan: exercises in order, each with its targets. */
@Serializable
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One exercise of a routine with its targets. A [timed] one has an execution time per set
 * ([durationSec]) instead of reps. [weightKg] null means "same as last time".
 */
@Serializable
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    @ColumnInfo(defaultValue = "0") val timed: Boolean = false,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val restSec: Int,
    val durationSec: Int,
)
