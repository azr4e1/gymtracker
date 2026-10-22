package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.data.repository.Countdown

/** A set with the exercise it belongs to. */
data class NextSet(val exercise: SessionExerciseDetails, val set: SessionSet) {
    /** Only timed sets can run on their own. */
    val canRun: Boolean get() = exercise.entry.timed
}

/** The first set not ticked yet, in workout order ([details] sorted), or null when all are done. */
fun firstUnticked(details: SessionDetails): NextSet? =
    details.exercises.firstNotNullOfOrNull { e -> e.sets.firstOrNull { !it.done }?.let { NextSet(e, it) } }

/** "Plank · set 2 of 3" for a timed set of [details], "Rest · next: Side plank" for a rest. */
fun timerTitle(countdown: Countdown, details: SessionDetails): String {
    if (countdown.isRest) return firstUnticked(details)?.let { "Rest · next: ${it.exercise.exercise.name}" } ?: "Rest"
    val exercise = details.exercises.firstOrNull { e -> e.sets.any { it.id == countdown.setId } } ?: return "Set"
    val number = exercise.sets.indexOfFirst { it.id == countdown.setId } + 1
    return "${exercise.exercise.name} · set $number of ${exercise.sets.size}"
}
