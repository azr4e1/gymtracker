package com.lorenzo.gymtracker.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutinesViewModel(routines: RoutineRepository, exercises: ExerciseRepository, private val sessions: SessionRepository) : ViewModel() {
    /** Null while loading. */
    val routines: StateFlow<List<RoutineDetails>?> = routines.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val exercises: StateFlow<List<Exercise>> = exercises.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active: StateFlow<SessionDetails?> = sessions.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start(routine: RoutineDetails, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(sessions.startRoutine(routine)) }
    }

    companion object {
        val Factory = appViewModelFactory { RoutinesViewModel(it.routineRepository, it.exerciseRepository, it.sessionRepository) }
    }
}
