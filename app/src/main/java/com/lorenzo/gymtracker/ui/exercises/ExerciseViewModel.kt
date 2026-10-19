package com.lorenzo.gymtracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.dao.SetRecord
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import com.lorenzo.gymtracker.domain.Metric
import com.lorenzo.gymtracker.domain.Record
import com.lorenzo.gymtracker.domain.SessionPoint
import com.lorenzo.gymtracker.domain.availableMetrics
import com.lorenzo.gymtracker.domain.date
import com.lorenzo.gymtracker.domain.records
import com.lorenzo.gymtracker.domain.sessionPoints
import com.lorenzo.gymtracker.ui.appViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How many days the chart shows at once (older ones are a drag away); null for everything. */
enum class ProgressRange(val days: Int?, val label: String) {
    MONTH(30, "1 month"),
    QUARTER(91, "3 months"),
    YEAR(365, "1 year"),
    ALL(null, "All"),
}

/** One workout with this exercise, for the history list. */
data class HistoryEntry(val sessionId: Long, val date: LocalDate, val timed: Boolean, val sets: List<SessionSet>)

data class ExerciseUiState(
    val exercise: Exercise? = null,
    val metrics: List<Metric> = emptyList(),
    val metric: Metric? = null,
    val range: ProgressRange = ProgressRange.QUARTER,
    /** Every workout's value, oldest first. */
    val points: List<SessionPoint> = emptyList(),
    val records: List<Record> = emptyList(),
    /** Newest first. */
    val history: List<HistoryEntry> = emptyList(),
)

class ExerciseViewModel(exerciseId: Long, exercises: ExerciseRepository, sessions: SessionRepository) : ViewModel() {
    private val chosenMetric = MutableStateFlow<Metric?>(null)
    private val range = MutableStateFlow(ProgressRange.QUARTER)
    private val sets = sessions.observeDoneSets().map { all -> all.filter { it.exerciseId == exerciseId } }

    val uiState: StateFlow<ExerciseUiState> =
        combine(exercises.observe(exerciseId), sets, chosenMetric, range) { exercise, sets, chosen, range ->
            val metrics = availableMetrics(sets)
            val metric = chosen?.takeIf { it in metrics } ?: metrics.firstOrNull()
            ExerciseUiState(
                exercise = exercise,
                metrics = metrics,
                metric = metric,
                range = range,
                points = metric?.let { sessionPoints(sets, it) }.orEmpty(),
                records = records(sets),
                history = history(sets),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseUiState())

    fun setMetric(metric: Metric) {
        chosenMetric.value = metric
    }

    fun setRange(value: ProgressRange) {
        range.value = value
    }

    private fun history(sets: List<SetRecord>): List<HistoryEntry> =
        sets.groupBy { it.sessionId }.values.map { s ->
            HistoryEntry(
                s.first().sessionId, s.first().date(), s.first().timed,
                s.map { SessionSet(sessionExerciseId = 0, position = 0, reps = it.reps, weightKg = it.weightKg, durationSec = it.durationSec, done = true) },
            )
        }.sortedByDescending { it.date }

    companion object {
        fun factory(exerciseId: Long) = appViewModelFactory { ExerciseViewModel(exerciseId, it.exerciseRepository, it.sessionRepository) }
    }
}
