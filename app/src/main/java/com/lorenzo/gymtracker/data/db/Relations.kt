package com.lorenzo.gymtracker.data.db

import androidx.room.Embedded
import androidx.room.Relation
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet

// Rows loaded together with their children. Room doesn't order relations: use sorted().

data class RoutineExerciseDetails(
    @Embedded val entry: RoutineExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
)

data class RoutineDetails(
    @Embedded val routine: Routine,
    @Relation(entity = RoutineExercise::class, parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExerciseDetails>,
) {
    fun sorted() = copy(exercises = exercises.sortedBy { it.entry.position })
}

data class SessionExerciseDetails(
    @Embedded val entry: SessionExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId") val sets: List<SessionSet>,
)

data class SessionDetails(
    @Embedded val session: Session,
    @Relation(entity = SessionExercise::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseDetails>,
) {
    fun sorted() = copy(
        exercises = exercises.sortedBy { it.entry.position }.map { e -> e.copy(sets = e.sets.sortedBy { it.position }) },
    )

    val doneSets: Int get() = exercises.sumOf { e -> e.sets.count { it.done } }
}
