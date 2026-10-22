package com.lorenzo.gymtracker

import android.app.Application
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import com.lorenzo.gymtracker.data.backup.BackupRepository
import com.lorenzo.gymtracker.data.db.AppDatabase
import com.lorenzo.gymtracker.data.export.ExportRepository
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.data.repository.SampleDataRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.data.repository.TimerSounds
import com.lorenzo.gymtracker.data.repository.WorkoutTimers
import com.lorenzo.gymtracker.timer.TimerService
import com.lorenzo.gymtracker.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Holds the app-wide singletons. Screens get their dependencies from here. */
class AppContainer(app: Application, scope: CoroutineScope) {
    val database: AppDatabase by lazy { AppDatabase.create(app) }
    val exerciseRepository: ExerciseRepository by lazy { ExerciseRepository(database.exerciseDao()) }
    val routineRepository: RoutineRepository by lazy { RoutineRepository(database.routineDao()) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }
    val backupRepository: BackupRepository by lazy { BackupRepository(app, database, exerciseRepository) }
    val exportRepository: ExportRepository by lazy { ExportRepository(app, sessionRepository, exerciseRepository, routineRepository) }
    val sampleDataRepository: SampleDataRepository by lazy { SampleDataRepository(database) }
    val timers: WorkoutTimers by lazy { WorkoutTimers(app, scope, sessionRepository, TimerSounds(app)) }
}

@OptIn(FlowPreview::class)
class GymTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        // Lists build upcoming items in pausable chunks while scrolling; in release builds that
        // crashed the workout screen ("Expected to be at the end of a group"). Build them in one go.
        ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
        container = AppContainer(this, appScope)
        appScope.launch { container.exerciseRepository.addDefaultsIfEmpty() }
        // Widgets follow workouts and routines.
        appScope.launch {
            container.database.invalidationTracker
                .createFlow("sessions", "session_sets", "session_exercises", "routines", "routine_exercises", emitInitialState = false)
                .debounce(1_000)
                .collect { updateAllWidgets(this@GymTrackerApp) }
        }
        // A countdown starting shows the timer notification; the service stops itself after.
        appScope.launch {
            container.timers.state.map { it.current != null }.distinctUntilChanged().filter { it }
                .collect { TimerService.start(this@GymTrackerApp) }
        }
    }
}
