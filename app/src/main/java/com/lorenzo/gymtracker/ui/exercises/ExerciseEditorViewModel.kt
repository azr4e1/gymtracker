package com.lorenzo.gymtracker.ui.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.ui.appViewModelFactory
import kotlinx.coroutines.launch

/** Form for creating an exercise, or editing one when [exerciseId] isn't 0. */
class ExerciseEditorViewModel(private val exerciseId: Long, private val exercises: ExerciseRepository) : ViewModel() {
    val isEditing = exerciseId != 0L
    private var original: Exercise? = null

    var name by mutableStateOf("")
    var primary by mutableStateOf(listOf<Muscle>())
        private set
    var secondary by mutableStateOf(listOf<Muscle>())
        private set
    var timed by mutableStateOf(false)
        private set
    var weighted by mutableStateOf(true)
        private set

    // Until the weight switch is used, a new exercise tracks weight by reps and not by time.
    private var weightedChosen = false
    var sets by mutableStateOf("3")
    var reps by mutableStateOf("10")
    var rest by mutableStateOf("90")
    var duration by mutableStateOf("45")

    init {
        if (isEditing) {
            viewModelScope.launch {
                val e = exercises.get(exerciseId) ?: return@launch
                original = e
                name = e.name
                primary = e.primaryMuscles
                secondary = e.secondaryMuscles
                timed = e.timed
                weighted = e.weighted
                sets = e.sets.toString()
                reps = e.reps.toString()
                rest = e.restSec.toString()
                duration = e.durationSec.toString()
            }
        }
    }

    fun changeTimed(value: Boolean) {
        timed = value
        if (!isEditing && !weightedChosen) weighted = !value
    }

    fun changeWeighted(value: Boolean) {
        weighted = value
        weightedChosen = true
    }

    /** A muscle is either main or helping, never both. */
    fun togglePrimary(m: Muscle) {
        primary = if (m in primary) primary - m else primary + m
        secondary = secondary - m
    }

    fun toggleSecondary(m: Muscle) {
        secondary = if (m in secondary) secondary - m else secondary + m
        primary = primary - m
    }

    val setsValue get() = sets.toIntOrNull()?.takeIf { it in 1..50 }
    val repsValue get() = reps.toIntOrNull()?.takeIf { it in 1..1000 }
    val restValue get() = rest.toIntOrNull()?.takeIf { it in 0..3600 }
    val durationValue get() = duration.toIntOrNull()?.takeIf { it in 1..36_000 }

    val isValid: Boolean
        get() = name.isNotBlank() && primary.isNotEmpty() && restValue != null &&
            if (timed) durationValue != null && setsValue != null else setsValue != null && repsValue != null

    fun save(onSaved: (Long) -> Unit) {
        if (!isValid || (isEditing && original == null)) return
        // Muscles are stored in body order, whatever order they were tapped in.
        val base = original ?: Exercise(name = "", primaryMuscles = emptyList())
        val exercise = base.copy(
            name = name.trim(),
            primaryMuscles = primary.sorted(),
            secondaryMuscles = secondary.sorted(),
            timed = timed,
            weighted = weighted,
            sets = setsValue ?: base.sets,
            reps = repsValue ?: base.reps,
            restSec = restValue!!,
            durationSec = durationValue ?: base.durationSec,
        )
        viewModelScope.launch {
            if (isEditing) {
                exercises.update(exercise)
                onSaved(exercise.id)
            } else {
                onSaved(exercises.add(exercise))
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            exercises.delete(exerciseId)
            onDeleted()
        }
    }

    companion object {
        fun factory(exerciseId: Long) = appViewModelFactory { ExerciseEditorViewModel(exerciseId, it.exerciseRepository) }
    }
}
