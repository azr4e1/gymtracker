package com.lorenzo.gymtracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.domain.MusclePeriod
import com.lorenzo.gymtracker.domain.date
import com.lorenzo.gymtracker.domain.end
import com.lorenzo.gymtracker.domain.lastTrained
import com.lorenzo.gymtracker.domain.start
import com.lorenzo.gymtracker.domain.weeklyMuscleSets
import com.lorenzo.gymtracker.ui.appViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** An exercise that has been trained, with its latest workout. */
data class TrainedExercise(val exercise: Exercise, val workouts: Int, val lastDate: LocalDate, val timed: Boolean, val lastSets: List<SessionSet>)

data class ProgressUiState(
    val loaded: Boolean = false,
    /** Most recently trained first. */
    val exercises: List<TrainedExercise> = emptyList(),
    val period: MusclePeriod = MusclePeriod.THIS_WEEK,
    /** How many periods back from the current one the muscle stats are. */
    val periodsBack: Int = 0,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    /** There are sets before [from]. */
    val canGoBack: Boolean = false,
    /** Sets per week. */
    val muscleSets: Map<Muscle, Double> = emptyMap(),
    val lastTrained: Map<Muscle, LocalDate> = emptyMap(),
)

class ProgressViewModel(exercises: ExerciseRepository, sessions: SessionRepository) : ViewModel() {
    private val period = MutableStateFlow(MusclePeriod.THIS_WEEK)
    private val periodsBack = MutableStateFlow(0)

    val uiState: StateFlow<ProgressUiState> =
        combine(sessions.observeDoneSets(), exercises.observeAll(), period, periodsBack) { sets, all, period, back ->
            val byId = all.associateBy { it.id }
            val to = period.end(back, LocalDate.now())
            val from = period.start(to)
            val trained = sets.groupBy { it.exerciseId }.mapNotNull { (id, s) ->
                val exercise = byId[id] ?: return@mapNotNull null
                val last = s.maxBy { it.startedAt }
                val lastSets = s.filter { it.sessionId == last.sessionId }
                TrainedExercise(
                    exercise, s.map { it.sessionId }.distinct().size, last.date(), last.timed,
                    lastSets.map { SessionSet(sessionExerciseId = 0, position = 0, reps = it.reps, weightKg = it.weightKg, durationSec = it.durationSec, done = true) },
                )
            }.sortedWith(compareByDescending<TrainedExercise> { it.lastDate }.thenBy { it.exercise.name })
            ProgressUiState(
                loaded = true,
                exercises = trained,
                period = period,
                periodsBack = back,
                from = from,
                to = to,
                canGoBack = sets.any { it.date() < from },
                muscleSets = weeklyMuscleSets(sets, byId, period, to),
                // As of the end of the period shown: later workouts don't count.
                lastTrained = lastTrained(sets.filter { it.date() <= to }, byId),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun setPeriod(value: MusclePeriod) {
        period.value = value
        periodsBack.value = 0
    }

    /** Shows the muscle stats [by] periods earlier (positive) or later (negative). */
    fun movePeriod(by: Int) {
        periodsBack.value = (periodsBack.value + by).coerceAtLeast(0)
    }

    companion object {
        val Factory = appViewModelFactory { ProgressViewModel(it.exerciseRepository, it.sessionRepository) }
    }
}
