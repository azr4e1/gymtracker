package com.lorenzo.gymtracker

import android.app.Application
import com.lorenzo.gymtracker.data.backup.BackupRepository
import com.lorenzo.gymtracker.data.db.AppDatabase
import com.lorenzo.gymtracker.data.export.ExportRepository
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.data.repository.SampleDataRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.data.repository.TimerSounds
import com.lorenzo.gymtracker.data.repository.WorkoutTimers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

class GymTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        appScope.launch { container.exerciseRepository.addDefaultsIfEmpty() }
    }
}
