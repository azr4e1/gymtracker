package com.lorenzo.gymtracker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * An exercise in the library. [primaryMuscles] are the muscles it mainly works; [secondaryMuscles]
 * only help. The numbers are defaults used when the exercise is added to a routine or a workout.
 *
 * Exercises are never deleted from the database: deleting one sets [hidden], so past workouts
 * and progress charts still show it.
 */
@Serializable
@Entity(tableName = "exercises", indices = [Index("name")])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle> = emptyList(),
    /** Tracked by time (e.g. plank) rather than reps when added to a workout on its own. */
    val timed: Boolean = false,
    /** Sets record a weight. Off for bodyweight exercises; on for weighted timed ones (e.g. farmer's carry). */
    @ColumnInfo(defaultValue = "1") val weighted: Boolean = true,
    val sets: Int = 3,
    val reps: Int = 10,
    val restSec: Int = 90,
    val durationSec: Int = 45,
    /** One of the exercises the app comes with. */
    val builtIn: Boolean = false,
    val hidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
