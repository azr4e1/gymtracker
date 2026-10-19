package com.lorenzo.gymtracker.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.domain.Metric
import com.lorenzo.gymtracker.domain.muscleSets
import com.lorenzo.gymtracker.domain.newRecords
import com.lorenzo.gymtracker.domain.valueOf
import com.lorenzo.gymtracker.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A personal best set in this workout. */
data class NewRecord(val exercise: Exercise, val metric: Metric, val value: Double)

data class SessionUiState(
    val details: SessionDetails,
    val volumeKg: Double,
    val muscles: Map<Muscle, Double>,
    val records: List<NewRecord>,
)

class SessionViewModel(
    private val sessionId: Long,
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
) : ViewModel() {
    val uiState: StateFlow<SessionUiState?> = combine(sessions.observe(sessionId), sessions.observeDoneSets()) { details, allSets ->
        details?.let { d ->
            val mine = allSets.filter { it.sessionId == sessionId }
            val exercises = d.exercises.associate { it.exercise.id to it.exercise }
            SessionUiState(
                details = d,
                volumeKg = mine.filter { !it.timed }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
                muscles = muscleSets(mine, exercises),
                records = exercises.values.flatMap { exercise ->
                    val history = allSets.filter { it.exerciseId == exercise.id }
                    newRecords(history, sessionId).map { metric ->
                        NewRecord(exercise, metric, metric.valueOf(mine.filter { it.exerciseId == exercise.id }) ?: 0.0)
                    }
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        val session = uiState.value?.details?.session ?: return
        viewModelScope.launch { sessions.rename(session, name) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            sessions.delete(sessionId)
            onDeleted()
        }
    }

    /** Makes a routine with this workout's exercises and sets. [onCreated] gets its id. */
    fun saveAsRoutine(onCreated: (Long) -> Unit) {
        val d = uiState.value?.details ?: return
        val items = d.exercises.map { e ->
            val sets = e.sets.filter { it.done }.ifEmpty { e.sets }
            RoutineExercise(
                routineId = 0,
                exerciseId = e.exercise.id,
                position = 0,
                timed = e.entry.timed,
                sets = sets.size.coerceAtLeast(1),
                reps = sets.firstNotNullOfOrNull { it.reps } ?: e.exercise.reps,
                restSec = e.entry.restSec,
                durationSec = sets.firstNotNullOfOrNull { it.durationSec } ?: e.exercise.durationSec,
            )
        }
        viewModelScope.launch { onCreated(routines.save(Routine(name = d.session.name), items)) }
    }

    companion object {
        fun factory(sessionId: Long) = appViewModelFactory { SessionViewModel(sessionId, it.sessionRepository, it.routineRepository) }
    }
}

