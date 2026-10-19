package com.lorenzo.gymtracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.random.Random

/** One planned exercise of a sample routine and how it progresses week by week. */
private data class Progression(
    val exercise: String,
    /** Starting weight; null for bodyweight. */
    val startKg: Double? = null,
    /** Kg added every two weeks, rounded to [roundTo]. */
    val kgPerTwoWeeks: Double = 0.0,
    val roundTo: Double = 2.5,
    val reps: Int = 8,
    /** Reps added every four weeks (bodyweight exercises). */
    val repsPerFourWeeks: Int = 0,
    val seconds: Int? = null,
    val secondsPerTwoWeeks: Int = 0,
    val sets: Int = 3,
)

private data class SampleRoutine(val name: String, val days: Set<DayOfWeek>, val chance: Double, val exercises: List<Progression>)

private val SAMPLE_ROUTINES = listOf(
    SampleRoutine(
        "Push", setOf(DayOfWeek.MONDAY), 0.9,
        listOf(
            Progression("Bench press", 60.0, 2.5),
            Progression("Overhead press", 35.0, 1.25, roundTo = 1.25),
            Progression("Incline dumbbell press", 22.0, 1.0, roundTo = 1.0, reps = 10),
            Progression("Lateral raise", 8.0, 0.5, roundTo = 1.0, reps = 15),
            Progression("Triceps pushdown", 25.0, 1.25, roundTo = 1.25, reps = 12),
            Progression("Push-up", reps = 12, repsPerFourWeeks = 2),
        ),
    ),
    SampleRoutine(
        "Pull", setOf(DayOfWeek.WEDNESDAY), 0.88,
        listOf(
            Progression("Deadlift", 100.0, 5.0, reps = 5),
            Progression("Pull-up", reps = 5, repsPerFourWeeks = 1),
            Progression("Barbell row", 55.0, 2.5),
            Progression("Lat pulldown", 50.0, 2.5, reps = 10),
            Progression("Face pull", 20.0, 1.0, roundTo = 1.0, reps = 15),
            Progression("Barbell curl", 25.0, 1.25, roundTo = 1.25, reps = 10),
        ),
    ),
    SampleRoutine(
        "Legs", setOf(DayOfWeek.FRIDAY), 0.85,
        listOf(
            Progression("Squat", 80.0, 5.0, reps = 5),
            Progression("Romanian deadlift", 70.0, 2.5),
            Progression("Leg press", 140.0, 10.0, roundTo = 10.0, reps = 10),
            Progression("Leg curl", 35.0, 2.5, reps = 12),
            Progression("Standing calf raise", 60.0, 5.0, roundTo = 5.0, reps = 15),
            Progression("Hanging leg raise", reps = 8, repsPerFourWeeks = 2),
        ),
    ),
    SampleRoutine(
        "Core circuit", setOf(DayOfWeek.SATURDAY), 0.6,
        listOf(
            Progression("Plank", seconds = 45, secondsPerTwoWeeks = 5),
            Progression("Side plank", seconds = 30, secondsPerTwoWeeks = 3),
            Progression("Mountain climber", seconds = 30, secondsPerTwoWeeks = 2),
            Progression("Farmer's carry", 24.0, 2.0, roundTo = 2.0, seconds = 45),
        ),
    ),
)

/** A set as the sample generator plans it. */
data class SampleSet(val reps: Int?, val weightKg: Double?, val durationSec: Int?)

data class SampleExercise(val name: String, val timed: Boolean, val sets: List<SampleSet>)

data class SampleWorkout(val routine: String, val date: LocalDate, val start: LocalTime, val exercises: List<SampleExercise>)

/** The sample routines by name, each with its exercises in order and their planned sets. */
val SAMPLE_ROUTINE_PLANS: Map<String, List<SampleExercise>> = SAMPLE_ROUTINES.associate { r ->
    r.name to r.exercises.map { p ->
        SampleExercise(p.exercise, p.seconds != null, List(p.sets) { SampleSet(p.reps, null, p.seconds) })
    }
}

/**
 * A push/pull/legs week plus a Saturday core circuit, for [days] days up to yesterday, with
 * steadily rising weights, reps and times, missed sessions, tired last sets and the odd bad day.
 * The same [seed] always gives the same workouts.
 */
fun sampleWorkouts(today: LocalDate, days: Int = 105, seed: Int = 42): List<SampleWorkout> {
    val random = Random(seed)
    val first = today.minusDays(days.toLong())
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.mapNotNull { date ->
        val routine = SAMPLE_ROUTINES.firstOrNull { date.dayOfWeek in it.days } ?: return@mapNotNull null
        if (random.nextDouble() > routine.chance) return@mapNotNull null
        val weeks = (date.toEpochDay() - first.toEpochDay()) / 7.0
        // Now and then a bad day: everything a bit lighter.
        val form = if (random.nextDouble() < 0.1) 0.92 else 1.0
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val start = LocalTime.of(if (weekend) 10 else 18, 0).plusMinutes(random.nextLong(0, 75))
        SampleWorkout(
            routine.name, date, start,
            routine.exercises.map { p ->
                val kg = p.startKg?.let { round(it * form + p.kgPerTwoWeeks * (weeks / 2).toInt(), p.roundTo) }
                SampleExercise(
                    p.exercise, p.seconds != null,
                    List(p.sets) { i ->
                        val last = i == p.sets - 1
                        if (p.seconds != null) {
                            val seconds = p.seconds + p.secondsPerTwoWeeks * (weeks / 2).toInt()
                            // Timed sets sometimes stop a few seconds early on the last round.
                            SampleSet(null, kg, if (last && random.nextBoolean()) seconds - 5 else seconds)
                        } else {
                            val reps = p.reps + p.repsPerFourWeeks * (weeks / 4).toInt()
                            SampleSet(if (last) reps - random.nextInt(0, 3) else reps, kg, null)
                        }
                    },
                )
            },
        )
    }.toList()
}

private fun round(value: Double, step: Double): Double = (value / step).roundToInt() * step
