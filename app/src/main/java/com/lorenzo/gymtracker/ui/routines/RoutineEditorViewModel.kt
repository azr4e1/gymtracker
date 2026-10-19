package com.lorenzo.gymtracker.ui.routines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.ui.appViewModelFactory
import com.lorenzo.gymtracker.ui.formatWeight
import com.lorenzo.gymtracker.ui.toDecimalOrNull
import kotlinx.coroutines.launch

/** One exercise in the editor, by reps or [timed]. Numbers stay text while typing; [key] tells rows apart. */
data class RoutineItem(
    val key: Long,
    val exercise: Exercise,
    val timed: Boolean,
    val sets: String,
    val reps: String,
    val weight: String,
    val rest: String,
    val duration: String,
) {
    val setsValue get() = sets.toIntOrNull()?.takeIf { it in 1..50 }
    val repsValue get() = reps.toIntOrNull()?.takeIf { it in 1..1000 }
    val restValue get() = rest.toIntOrNull()?.takeIf { it in 0..3600 }
    val durationValue get() = duration.toIntOrNull()?.takeIf { it in 1..36_000 }
    val weightValue get() = weight.toDecimalOrNull()
    val weightInvalid get() = exercise.weighted && weight.isNotBlank() && weightValue == null

    val isValid get() = restValue != null && setsValue != null && !weightInvalid && if (timed) durationValue != null else repsValue != null
}

/** Edits a routine in memory; nothing is saved until [save]. */
class RoutineEditorViewModel(
    private val routineId: Long,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    val isEditing = routineId != 0L
    private var original: Routine? = null
    private var nextKey = 0L

    var name by mutableStateOf("")
    val items = mutableStateListOf<RoutineItem>()

    /** Changed since opening, so leaving asks first. */
    var dirty by mutableStateOf(false)
        private set

    init {
        if (isEditing) {
            viewModelScope.launch {
                val r = routines.get(routineId) ?: return@launch
                original = r.routine
                name = r.routine.name
                items.addAll(
                    r.exercises.map { (e, exercise) ->
                        RoutineItem(
                            nextKey++, exercise, e.timed, e.sets.toString(), e.reps.toString(), e.weightKg?.formatWeight().orEmpty(),
                            e.restSec.toString(), e.durationSec.toString(),
                        )
                    },
                )
            }
        }
    }

    fun changeName(value: String) {
        name = value
        dirty = true
    }

    /** Adds exercises with their defaults. */
    fun addExercises(ids: List<Long>) {
        viewModelScope.launch {
            ids.mapNotNull { exercises.get(it) }.forEach { e ->
                items.add(RoutineItem(nextKey++, e, e.timed, e.sets.toString(), e.reps.toString(), "", e.restSec.toString(), e.durationSec.toString()))
            }
            dirty = true
        }
    }

    fun update(item: RoutineItem) {
        val i = items.indexOfFirst { it.key == item.key }
        if (i >= 0) items[i] = item
        dirty = true
    }

    fun remove(item: RoutineItem) {
        items.removeAll { it.key == item.key }
        dirty = true
    }

    fun move(item: RoutineItem, by: Int) {
        val from = items.indexOfFirst { it.key == item.key }
        val to = from + by
        if (from < 0 || to !in items.indices) return
        items.add(to, items.removeAt(from))
        dirty = true
    }

    val isValid: Boolean get() = name.isNotBlank() && items.all { it.isValid }

    fun save(onSaved: () -> Unit) {
        if (!isValid) return
        val routine = (original ?: Routine(name = "")).copy(name = name.trim())
        val planned = items.map {
            val exercise = it.exercise
            RoutineExercise(
                routineId = routine.id,
                exerciseId = exercise.id,
                position = 0,
                timed = it.timed,
                // In the mode not chosen, keep the exercise's defaults, so switching later still works.
                sets = it.setsValue ?: exercise.sets,
                reps = it.repsValue ?: exercise.reps,
                weightKg = it.weightValue.takeIf { exercise.weighted },
                restSec = it.restValue ?: exercise.restSec,
                durationSec = it.durationValue ?: exercise.durationSec,
            )
        }
        viewModelScope.launch {
            routines.save(routine, planned)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            routines.delete(routineId)
            onDeleted()
        }
    }

    companion object {
        fun factory(routineId: Long) = appViewModelFactory { RoutineEditorViewModel(routineId, it.routineRepository, it.exerciseRepository) }
    }
}
