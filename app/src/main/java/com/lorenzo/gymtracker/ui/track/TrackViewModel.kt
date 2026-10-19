package com.lorenzo.gymtracker.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.data.repository.WorkoutTimers
import com.lorenzo.gymtracker.ui.appViewModelFactory
import com.lorenzo.gymtracker.ui.lbToKg
import com.lorenzo.gymtracker.ui.toDecimalOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What was done last time, to beat it: sets of the latest other workout with the exercise. */
data class LastTime(val timed: Boolean, val sets: List<SessionSet>)

/**
 * Tracks the sets of a workout: the one in progress, or a finished one being corrected.
 * Every edit is saved right away.
 */
class TrackViewModel(
    private val sessionId: Long,
    private val sessions: SessionRepository,
    private val timers: WorkoutTimers,
) : ViewModel() {
    val details: StateFlow<SessionDetails?> = sessions.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** By exercise id. */
    val lastTime: StateFlow<Map<Long, LastTime>> = sessions.observeDoneSets().map { records ->
        records.filter { it.sessionId != sessionId }.groupBy { it.exerciseId }.mapValues { (_, sets) ->
            val latest = sets.maxBy { it.startedAt }.sessionId
            val inLatest = sets.filter { it.sessionId == latest }
            LastTime(
                timed = inLatest.first().timed,
                sets = inLatest.map { SessionSet(sessionExerciseId = 0, position = 0, reps = it.reps, weightKg = it.weightKg, durationSec = it.durationSec, done = true) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val rest: StateFlow<WorkoutTimers.Rest?> = timers.rest
    val running: StateFlow<WorkoutTimers.RunningSet?> = timers.running

    /** Auto-run is going through this workout's timed sets. */
    val autoRunning: StateFlow<Boolean> = timers.autoSession.map { it == sessionId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoStop: StateFlow<WorkoutTimers.AutoStop?> = timers.autoStop

    fun startAuto() = timers.startAuto(sessionId)

    fun stopAuto() = timers.stopAuto()

    fun clearAutoStop() = timers.clearAutoStop()

    private val isActive get() = details.value?.session?.isActive == true

    /** [text] is in pounds when [inLb]; the weight is stored in kg either way. */
    fun setWeight(set: SessionSet, text: String, inLb: Boolean) {
        val value = text.toDecimalOrNull()
        launch { sessions.setWeight(set.id, if (inLb) value?.lbToKg() else value) }
    }

    fun setReps(set: SessionSet, text: String) = launch { sessions.setReps(set.id, text.toIntOrNull()) }

    fun setDuration(set: SessionSet, text: String) = launch { sessions.setDuration(set.id, text.toIntOrNull()) }

    /** Ticking a set during a workout starts the exercise's rest. */
    fun toggleDone(exercise: SessionExerciseDetails, set: SessionSet) {
        val done = !set.done
        launch { sessions.setDone(set.id, done) }
        // Not while a timed set is counting down: its own rest follows it.
        if (done && isActive && timers.running.value == null) timers.startRest(exercise.entry.restSec)
    }

    fun startSet(exercise: SessionExerciseDetails, set: SessionSet) =
        timers.startSet(set.id, set.durationSec ?: exercise.exercise.durationSec, exercise.entry.restSec)

    fun stopSet() = timers.stopSet()

    fun adjustRest(seconds: Int) = timers.adjustRest(seconds)

    fun skipRest() = timers.skipRest()

    fun addSet(exercise: SessionExerciseDetails) = launch { sessions.addSet(exercise.entry, exercise.exercise) }

    fun removeSet(set: SessionSet) = launch { sessions.deleteSet(set.id) }

    fun setRest(exercise: SessionExerciseDetails, seconds: Int) = launch { sessions.setRest(exercise.entry.id, seconds) }

    fun setTimed(exercise: SessionExerciseDetails, timed: Boolean) = launch { sessions.setTimed(exercise.entry, exercise.exercise, timed) }

    fun move(exercise: SessionExerciseDetails, by: Int) = launch { sessions.move(sessionId, exercise.entry.id, by) }

    fun removeExercise(exercise: SessionExerciseDetails) = launch { sessions.removeExercise(exercise.entry) }

    fun addExercises(ids: List<Long>) = launch { sessions.addExercises(sessionId, ids) }

    fun rename(name: String) {
        val session = details.value?.session ?: return
        launch { sessions.rename(session, name) }
    }

    /** [onDone] gets false if nothing was kept and the workout was deleted. */
    fun finish(keepUnticked: Boolean, onDone: (Boolean) -> Unit) {
        timers.cancelAll()
        viewModelScope.launch { onDone(sessions.finish(sessionId, keepUnticked)) }
    }

    fun discard(onDone: () -> Unit) {
        timers.cancelAll()
        viewModelScope.launch {
            sessions.delete(sessionId)
            onDone()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(sessionId: Long) = appViewModelFactory { TrackViewModel(sessionId, it.sessionRepository, it.timers) }
    }
}
