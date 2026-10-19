package com.lorenzo.gymtracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.data.repository.WorkoutTimers
import com.lorenzo.gymtracker.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(
    private val sessions: SessionRepository,
    routines: RoutineRepository,
    private val timers: WorkoutTimers,
) : ViewModel() {
    /** The workout in progress, if any. */
    val active: StateFlow<SessionDetails?> = sessions.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Finished workouts, newest first; null while loading. */
    val history: StateFlow<List<SessionDetails>?> = sessions.observeFinished()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The routines started most often, for quick start; all routines are in the Routines tab. */
    val topRoutines: StateFlow<List<RoutineDetails>> = combine(routines.observeAll(), sessions.observeFinished()) { all, finished ->
        val uses = finished.groupingBy { it.session.routineId }.eachCount()
        all.sortedWith(compareByDescending<RoutineDetails> { uses[it.routine.id] ?: 0 }.thenBy { it.routine.name.lowercase() })
            .take(QUICK_START_ROUTINES)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startEmpty(onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(sessions.startEmpty()) }
    }

    fun startRoutine(routine: RoutineDetails, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(sessions.startRoutine(routine)) }
    }

    /** Deletes the workout in progress. */
    fun discard(sessionId: Long) {
        timers.cancelAll()
        viewModelScope.launch { sessions.delete(sessionId) }
    }

    companion object {
        const val QUICK_START_ROUTINES = 3

        val Factory = appViewModelFactory { WorkoutViewModel(it.sessionRepository, it.routineRepository, it.timers) }
    }
}
