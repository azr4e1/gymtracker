package com.lorenzo.gymtracker.ui

import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.domain.Metric
import com.lorenzo.gymtracker.domain.MusclePeriod

val Muscle.label: String
    get() = when (this) {
        Muscle.CHEST -> "Chest"
        Muscle.SHOULDERS -> "Shoulders"
        Muscle.TRICEPS -> "Triceps"
        Muscle.BICEPS -> "Biceps"
        Muscle.FOREARMS -> "Forearms"
        Muscle.LATS -> "Lats"
        Muscle.UPPER_BACK -> "Upper back"
        Muscle.LOWER_BACK -> "Lower back"
        Muscle.ABS -> "Abs"
        Muscle.OBLIQUES -> "Obliques"
        Muscle.GLUTES -> "Glutes"
        Muscle.QUADS -> "Quads"
        Muscle.HAMSTRINGS -> "Hamstrings"
        Muscle.CALVES -> "Calves"
    }

/** "Chest, Triceps" or, with helping muscles, "Chest · also Triceps, Shoulders". */
val Exercise.musclesLabel: String
    get() {
        val main = primaryMuscles.joinToString(", ") { it.label }
        return if (secondaryMuscles.isEmpty()) main else "$main · also ${secondaryMuscles.joinToString(", ") { it.label }}"
    }

/** How the exercise is tracked, when it isn't the usual reps with weight. */
val Exercise.trackingLabel: String?
    get() = when {
        timed && weighted -> "Timed · weight"
        timed -> "Timed"
        !weighted -> "Bodyweight"
        else -> null
    }

val Metric.label: String
    get() = when (this) {
        Metric.ONE_REP_MAX -> "Est. 1RM"
        Metric.MAX_WEIGHT -> "Heaviest"
        Metric.VOLUME -> "Volume"
        Metric.MAX_REPS -> "Best set"
        Metric.TOTAL_REPS -> "Total reps"
        Metric.LONGEST -> "Longest"
        Metric.TOTAL_TIME -> "Total time"
    }

/** Longer explanation shown under the chart. */
val Metric.description: String
    get() = when (this) {
        Metric.ONE_REP_MAX -> "Estimated one-rep max of your best set each workout (weight × (1 + reps ÷ 30))."
        Metric.MAX_WEIGHT -> "Heaviest weight lifted each workout."
        Metric.VOLUME -> "Weight × reps, added up over each workout's sets."
        Metric.MAX_REPS -> "Most reps in a single set each workout."
        Metric.TOTAL_REPS -> "All reps added up each workout."
        Metric.LONGEST -> "Longest set each workout."
        Metric.TOTAL_TIME -> "All sets' time added up each workout."
    }

fun Metric.format(value: Double): String = when (this) {
    Metric.ONE_REP_MAX -> "${value.formatAmount()} kg"
    Metric.MAX_WEIGHT -> "${value.formatWeight()} kg"
    Metric.VOLUME -> "${value.formatCompact()} kg"
    Metric.MAX_REPS, Metric.TOTAL_REPS -> plural(value.toInt(), "rep", "reps")
    Metric.LONGEST, Metric.TOTAL_TIME -> formatClock(value.toInt())
}

/** Short value for chart axes. */
fun Metric.formatAxis(value: Double): String = when (this) {
    Metric.VOLUME -> value.formatCompact()
    Metric.LONGEST, Metric.TOTAL_TIME -> formatClock(value.toInt())
    else -> value.formatAmount()
}

val MusclePeriod.label: String
    get() = when (this) {
        MusclePeriod.THIS_WEEK -> "This week"
        MusclePeriod.FOUR_WEEKS -> "4 weeks"
        MusclePeriod.TWELVE_WEEKS -> "12 weeks"
    }
