package com.lorenzo.gymtracker.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lorenzo.gymtracker.ui.components.AppIcons
import com.lorenzo.gymtracker.ui.exercises.ExerciseEditorScreen
import com.lorenzo.gymtracker.ui.exercises.ExerciseEditorViewModel
import com.lorenzo.gymtracker.ui.exercises.ExercisePickerScreen
import com.lorenzo.gymtracker.ui.exercises.ExerciseScreen
import com.lorenzo.gymtracker.ui.exercises.ExerciseViewModel
import com.lorenzo.gymtracker.ui.progress.ProgressScreen
import com.lorenzo.gymtracker.ui.routines.RoutineEditorScreen
import com.lorenzo.gymtracker.ui.routines.RoutineEditorViewModel
import com.lorenzo.gymtracker.ui.routines.RoutinesScreen
import com.lorenzo.gymtracker.ui.session.SessionScreen
import com.lorenzo.gymtracker.ui.session.SessionViewModel
import com.lorenzo.gymtracker.ui.settings.SettingsScreen
import com.lorenzo.gymtracker.ui.track.TrackScreen
import com.lorenzo.gymtracker.ui.track.TrackViewModel
import com.lorenzo.gymtracker.ui.workout.WorkoutScreen
import kotlin.reflect.KClass

private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: () -> ImageVector) {
    WORKOUT(WorkoutRoute, WorkoutRoute::class, "Workout", { AppIcons.Dumbbell }),
    ROUTINES(RoutinesRoute, RoutinesRoute::class, "Routines", { AppIcons.Routine }),
    PROGRESS(ProgressRoute, ProgressRoute::class, "Progress", { AppIcons.Chart }),
    SETTINGS(SettingsRoute, SettingsRoute::class, "Settings", { Icons.Filled.Settings }),
}

private const val PICKED_EXERCISES = "pickedExercises"
private const val CREATED_EXERCISE = "createdExercise"

/** [openWorkout], when set, is a workout to show right away (from the notification or a widget). */
@Composable
fun GymTrackerNavHost(openWorkout: Long? = null, onWorkoutOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    LaunchedEffect(openWorkout) {
        val id = openWorkout ?: return@LaunchedEffect
        // Already on that workout: nothing to do.
        if (navController.currentBackStackEntry?.destination?.hasRoute(TrackRoute::class) != true) {
            navController.navigate(TrackRoute(id))
        }
        onWorkoutOpened()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }
    val openWorkout: (Long) -> Unit = { id -> navController.navigate(TrackRoute(id)) }

    Scaffold(
        // Each screen handles the status bar itself; this outer scaffold only adds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = WorkoutRoute, modifier = Modifier.padding(padding)) {
            composable<WorkoutRoute> {
                WorkoutScreen(
                    onOpenWorkout = openWorkout,
                    onOpenSession = { navController.navigate(SessionRoute(it)) },
                    onNewRoutine = { navController.navigate(RoutineEditorRoute()) },
                )
            }
            composable<RoutinesRoute> {
                RoutinesScreen(
                    onOpenWorkout = openWorkout,
                    onEditRoutine = { navController.navigate(RoutineEditorRoute(it)) },
                    onOpenExercise = { navController.navigate(ExerciseRoute(it)) },
                    onNewExercise = { navController.navigate(ExerciseEditorRoute()) },
                )
            }
            composable<ProgressRoute> { ProgressScreen(onOpenExercise = { navController.navigate(ExerciseRoute(it)) }) }
            composable<SettingsRoute> { SettingsScreen() }

            composable<TrackRoute> { entry ->
                val route = entry.toRoute<TrackRoute>()
                val viewModel: TrackViewModel = viewModel(factory = TrackViewModel.factory(route.sessionId))
                val context = LocalContext.current
                OnPickedExercises(entry, viewModel::addExercises)
                TrackScreen(
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                    onAddExercises = { navController.navigate(ExercisePickerRoute) },
                    onOpenExercise = { navController.navigate(ExerciseRoute(it)) },
                    onFinished = { kept ->
                        if (kept) {
                            navController.navigate(SessionRoute(route.sessionId)) { popUpTo<TrackRoute> { inclusive = true } }
                        } else {
                            Toast.makeText(context, "Nothing was ticked, so the workout wasn't saved", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                    },
                    onDiscarded = navController::popBackStack,
                )
            }
            composable<SessionRoute> { entry ->
                val route = entry.toRoute<SessionRoute>()
                SessionScreen(
                    viewModel = viewModel(factory = SessionViewModel.factory(route.sessionId)),
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(TrackRoute(route.sessionId)) },
                    onOpenExercise = { navController.navigate(ExerciseRoute(it)) },
                    onRoutineCreated = { navController.navigate(RoutineEditorRoute(it)) },
                )
            }
            composable<RoutineEditorRoute> { entry ->
                val route = entry.toRoute<RoutineEditorRoute>()
                val viewModel: RoutineEditorViewModel = viewModel(factory = RoutineEditorViewModel.factory(route.routineId))
                OnPickedExercises(entry, viewModel::addExercises)
                RoutineEditorScreen(
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                    onAddExercises = { navController.navigate(ExercisePickerRoute) },
                )
            }
            composable<ExercisePickerRoute> { entry ->
                val created by entry.savedStateHandle.getStateFlow<Long?>(CREATED_EXERCISE, null).collectAsStateWithLifecycle()
                ExercisePickerScreen(
                    createdExerciseId = created,
                    onCreatedHandled = { entry.savedStateHandle[CREATED_EXERCISE] = null },
                    onBack = navController::popBackStack,
                    onNewExercise = { navController.navigate(ExerciseEditorRoute(forPicker = true)) },
                    onDone = { ids ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISES, ids.toLongArray())
                        navController.popBackStack()
                    },
                )
            }
            composable<ExerciseEditorRoute> { entry ->
                val route = entry.toRoute<ExerciseEditorRoute>()
                ExerciseEditorScreen(
                    viewModel = viewModel(factory = ExerciseEditorViewModel.factory(route.exerciseId)),
                    onBack = navController::popBackStack,
                    onSaved = { id ->
                        if (route.forPicker) navController.previousBackStackEntry?.savedStateHandle?.set(CREATED_EXERCISE, id)
                        navController.popBackStack()
                    },
                    onDeleted = {
                        // The exercise screen behind the editor would show a deleted exercise.
                        if (!navController.popBackStack<ExerciseRoute>(inclusive = true)) navController.popBackStack()
                    },
                )
            }
            composable<ExerciseRoute> { entry ->
                val route = entry.toRoute<ExerciseRoute>()
                ExerciseScreen(
                    viewModel = viewModel(factory = ExerciseViewModel.factory(route.exerciseId)),
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(ExerciseEditorRoute(route.exerciseId)) },
                    onOpenSession = { navController.navigate(SessionRoute(it)) },
                )
            }
        }
    }
}

/** Delivers exercises chosen in the picker to [onPicked], once. */
@Composable
private fun OnPickedExercises(entry: NavBackStackEntry, onPicked: (List<Long>) -> Unit) {
    val picked by entry.savedStateHandle.getStateFlow<LongArray?>(PICKED_EXERCISES, null).collectAsStateWithLifecycle()
    LaunchedEffect(picked) {
        picked?.let {
            onPicked(it.toList())
            entry.savedStateHandle[PICKED_EXERCISES] = null
        }
    }
}

/** Switches bottom-bar tab, keeping each tab's state. */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo<WorkoutRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
