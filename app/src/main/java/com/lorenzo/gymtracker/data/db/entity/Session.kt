package com.lorenzo.gymtracker.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A workout, started from a routine or empty. It's in progress while [endedAt] is null; only one
 * workout is in progress at a time. Times are epoch milliseconds.
 */
@Serializable
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("routineId"), Index("startedAt")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long? = null,
    val name: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
) {
    val isActive: Boolean get() = endedAt == null
}

/** An exercise done in a workout. A [timed] one records a duration per set instead of reps. */
@Serializable
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(entity = Session::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val timed: Boolean,
    val restSec: Int,
)

/**
 * One set. Values start as the plan and are edited to what was actually done. Only [done]
 * (ticked) sets count in progress and muscle stats. [weightKg] null means bodyweight.
 */
@Serializable
@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SessionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val done: Boolean = false,
)
