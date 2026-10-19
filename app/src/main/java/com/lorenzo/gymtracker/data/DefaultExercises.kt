package com.lorenzo.gymtracker.data

import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.data.db.entity.Muscle.ABS
import com.lorenzo.gymtracker.data.db.entity.Muscle.BICEPS
import com.lorenzo.gymtracker.data.db.entity.Muscle.CALVES
import com.lorenzo.gymtracker.data.db.entity.Muscle.CHEST
import com.lorenzo.gymtracker.data.db.entity.Muscle.FOREARMS
import com.lorenzo.gymtracker.data.db.entity.Muscle.GLUTES
import com.lorenzo.gymtracker.data.db.entity.Muscle.HAMSTRINGS
import com.lorenzo.gymtracker.data.db.entity.Muscle.LATS
import com.lorenzo.gymtracker.data.db.entity.Muscle.LOWER_BACK
import com.lorenzo.gymtracker.data.db.entity.Muscle.OBLIQUES
import com.lorenzo.gymtracker.data.db.entity.Muscle.QUADS
import com.lorenzo.gymtracker.data.db.entity.Muscle.SHOULDERS
import com.lorenzo.gymtracker.data.db.entity.Muscle.TRICEPS
import com.lorenzo.gymtracker.data.db.entity.Muscle.UPPER_BACK

/** Compound lifts: heavier, fewer reps, longer rest. */
private fun compound(name: String, primary: List<Muscle>, secondary: List<Muscle> = emptyList()) =
    Exercise(name = name, primaryMuscles = primary, secondaryMuscles = secondary, sets = 3, reps = 8, restSec = 120, builtIn = true)

private fun isolation(name: String, primary: List<Muscle>, secondary: List<Muscle> = emptyList()) =
    Exercise(name = name, primaryMuscles = primary, secondaryMuscles = secondary, sets = 3, reps = 12, restSec = 60, builtIn = true)

private fun timed(name: String, primary: List<Muscle>, secondary: List<Muscle> = emptyList(), seconds: Int = 30) =
    Exercise(
        name = name, primaryMuscles = primary, secondaryMuscles = secondary, timed = true, weighted = false,
        sets = 3, reps = 10, durationSec = seconds, restSec = 30, builtIn = true,
    )

/** The exercises a new install starts with. Bodyweight ones don't track weight. */
val DEFAULT_EXERCISES: List<Exercise> = listOf(
    // Chest
    compound("Bench press", listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
    compound("Incline bench press", listOf(CHEST, SHOULDERS), listOf(TRICEPS)),
    compound("Dumbbell bench press", listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
    compound("Incline dumbbell press", listOf(CHEST, SHOULDERS), listOf(TRICEPS)),
    compound("Machine chest press", listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
    compound("Dip", listOf(CHEST, TRICEPS), listOf(SHOULDERS)).copy(weighted = false),
    compound("Push-up", listOf(CHEST), listOf(TRICEPS, SHOULDERS, ABS)).copy(reps = 15, restSec = 60, weighted = false),
    isolation("Dumbbell fly", listOf(CHEST)),
    isolation("Cable crossover", listOf(CHEST)),
    // Back
    compound("Deadlift", listOf(HAMSTRINGS, GLUTES, LOWER_BACK), listOf(UPPER_BACK, QUADS, FOREARMS)).copy(reps = 5, restSec = 180),
    compound("Pull-up", listOf(LATS), listOf(BICEPS, UPPER_BACK)).copy(weighted = false),
    compound("Chin-up", listOf(LATS, BICEPS), listOf(UPPER_BACK)).copy(weighted = false),
    compound("Lat pulldown", listOf(LATS), listOf(BICEPS, UPPER_BACK)).copy(reps = 10, restSec = 90),
    compound("Barbell row", listOf(UPPER_BACK, LATS), listOf(BICEPS, LOWER_BACK)),
    compound("Dumbbell row", listOf(LATS, UPPER_BACK), listOf(BICEPS)).copy(reps = 10, restSec = 90),
    compound("Seated cable row", listOf(UPPER_BACK, LATS), listOf(BICEPS)).copy(reps = 10, restSec = 90),
    isolation("Face pull", listOf(UPPER_BACK, SHOULDERS)),
    isolation("Shrug", listOf(UPPER_BACK), listOf(FOREARMS)),
    isolation("Back extension", listOf(LOWER_BACK), listOf(GLUTES, HAMSTRINGS)).copy(weighted = false),
    // Shoulders
    compound("Overhead press", listOf(SHOULDERS), listOf(TRICEPS, UPPER_BACK)),
    compound("Dumbbell shoulder press", listOf(SHOULDERS), listOf(TRICEPS)).copy(reps = 10, restSec = 90),
    isolation("Lateral raise", listOf(SHOULDERS)).copy(reps = 15),
    isolation("Front raise", listOf(SHOULDERS)),
    isolation("Reverse fly", listOf(SHOULDERS, UPPER_BACK)).copy(reps = 15),
    // Arms
    isolation("Barbell curl", listOf(BICEPS), listOf(FOREARMS)).copy(reps = 10),
    isolation("Dumbbell curl", listOf(BICEPS), listOf(FOREARMS)),
    isolation("Hammer curl", listOf(BICEPS, FOREARMS)),
    isolation("Triceps pushdown", listOf(TRICEPS)),
    isolation("Overhead triceps extension", listOf(TRICEPS)),
    isolation("Skull crusher", listOf(TRICEPS)).copy(reps = 10),
    compound("Close-grip bench press", listOf(TRICEPS, CHEST), listOf(SHOULDERS)),
    isolation("Wrist curl", listOf(FOREARMS)).copy(reps = 15),
    // Legs
    compound("Squat", listOf(QUADS, GLUTES), listOf(HAMSTRINGS, LOWER_BACK)).copy(reps = 5, restSec = 180),
    compound("Front squat", listOf(QUADS), listOf(GLUTES, ABS)),
    compound("Goblet squat", listOf(QUADS, GLUTES), listOf(ABS)).copy(reps = 12, restSec = 90),
    compound("Leg press", listOf(QUADS, GLUTES), listOf(HAMSTRINGS)).copy(reps = 10),
    compound("Romanian deadlift", listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK)),
    compound("Lunge", listOf(QUADS, GLUTES), listOf(HAMSTRINGS)).copy(reps = 10, restSec = 90),
    compound("Bulgarian split squat", listOf(QUADS, GLUTES), listOf(HAMSTRINGS)).copy(reps = 10, restSec = 90),
    compound("Hip thrust", listOf(GLUTES), listOf(HAMSTRINGS)).copy(reps = 10),
    isolation("Leg extension", listOf(QUADS)),
    isolation("Leg curl", listOf(HAMSTRINGS)),
    isolation("Standing calf raise", listOf(CALVES)).copy(reps = 15),
    isolation("Seated calf raise", listOf(CALVES)).copy(reps = 15),
    // Core
    isolation("Crunch", listOf(ABS)).copy(reps = 20, weighted = false),
    isolation("Cable crunch", listOf(ABS)).copy(reps = 15),
    isolation("Hanging leg raise", listOf(ABS), listOf(OBLIQUES, FOREARMS)).copy(weighted = false),
    isolation("Russian twist", listOf(OBLIQUES), listOf(ABS)).copy(reps = 20, weighted = false),
    isolation("Ab wheel rollout", listOf(ABS), listOf(LATS, SHOULDERS)).copy(reps = 10, weighted = false),
    timed("Plank", listOf(ABS), listOf(OBLIQUES, SHOULDERS), seconds = 45),
    timed("Side plank", listOf(OBLIQUES), listOf(ABS)),
    // Conditioning
    timed("Jumping jacks", listOf(CALVES), listOf(SHOULDERS, QUADS)),
    timed("Burpee", listOf(QUADS, CHEST), listOf(SHOULDERS, ABS)),
    timed("Mountain climber", listOf(ABS), listOf(SHOULDERS, QUADS)),
    timed("Jump rope", listOf(CALVES), listOf(SHOULDERS, FOREARMS), seconds = 60),
    timed("Jump squat", listOf(QUADS, GLUTES), listOf(CALVES)),
    timed("Wall sit", listOf(QUADS), listOf(GLUTES), seconds = 45),
    timed("Dead hang", listOf(FOREARMS), listOf(LATS)),
    // Timed, with a weight
    timed("Farmer's carry", listOf(FOREARMS, UPPER_BACK), listOf(ABS, GLUTES), seconds = 45).copy(weighted = true, restSec = 60),
)
