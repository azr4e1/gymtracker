package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.SessionSet

/** A set with the exercise it belongs to. */
data class NextSet(val exercise: SessionExerciseDetails, val set: SessionSet) {
    /** Only timed sets can run on their own. */
    val canRun: Boolean get() = exercise.entry.timed
}

/** The first set not ticked yet, in workout order ([details] sorted), or null when all are done. */
fun firstUnticked(details: SessionDetails): NextSet? =
    details.exercises.firstNotNullOfOrNull { e -> e.sets.firstOrNull { !it.done }?.let { NextSet(e, it) } }
