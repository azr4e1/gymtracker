package com.lorenzo.gymtracker.ui.navigation

import kotlinx.serialization.Serializable

// Screens and their arguments.

@Serializable
data object WorkoutRoute

@Serializable
data object RoutinesRoute

@Serializable
data object ProgressRoute

@Serializable
data object SettingsRoute

/** Tracks a workout: the one in progress, or a finished one being corrected. */
@Serializable
data class TrackRoute(val sessionId: Long)

/** Summary of a finished workout. */
@Serializable
data class SessionRoute(val sessionId: Long)

/** Creates a routine ([routineId] 0) or edits one. */
@Serializable
data class RoutineEditorRoute(val routineId: Long = 0)

/** Picks exercises; their ids go back to the previous screen. */
@Serializable
data object ExercisePickerRoute

/**
 * Creates an exercise ([exerciseId] 0) or edits one. With [forPicker], a new exercise is
 * returned to the exercise picker, picked.
 */
@Serializable
data class ExerciseEditorRoute(val exerciseId: Long = 0, val forPicker: Boolean = false)

/** An exercise's progress and history. */
@Serializable
data class ExerciseRoute(val exerciseId: Long)
