package com.lorenzo.gymtracker.ui.exercises

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExercisePickerViewModel(exercises: ExerciseRepository) : ViewModel() {
    val exercises: StateFlow<List<Exercise>> = exercises.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = appViewModelFactory { ExercisePickerViewModel(it.exerciseRepository) }
    }
}

/**
 * Picks one or more exercises, in the order tapped. A [createdExerciseId] (made with "New
 * exercise") is picked automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    createdExerciseId: Long?,
    onCreatedHandled: () -> Unit,
    onBack: () -> Unit,
    onNewExercise: () -> Unit,
    onDone: (List<Long>) -> Unit,
    viewModel: ExercisePickerViewModel = viewModel(factory = ExercisePickerViewModel.Factory),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var picked by rememberSaveable { mutableStateOf(listOf<Long>()) }
    LaunchedEffect(createdExerciseId) {
        createdExerciseId?.let {
            picked = picked + it
            onCreatedHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add exercises") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Button(onClick = { onDone(picked) }, enabled = picked.isNotEmpty(), modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (picked.isEmpty()) "Add" else "Add (${picked.size})")
                    }
                },
            )
        },
    ) { padding ->
        ExerciseList(
            exercises = exercises,
            selectedIds = picked,
            onClick = { e -> picked = if (e.id in picked) picked - e.id else picked + e.id },
            modifier = Modifier.padding(padding),
            header = {
                FilledTonalButton(onClick = onNewExercise, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("New exercise")
                }
            },
        )
    }
}
