package com.lorenzo.gymtracker.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import com.lorenzo.gymtracker.ui.components.EmptyText
import com.lorenzo.gymtracker.ui.exercises.ExerciseList
import com.lorenzo.gymtracker.ui.formatDuration
import com.lorenzo.gymtracker.ui.plural

/** Routines and the exercise library, as two tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onOpenWorkout: (Long) -> Unit,
    onEditRoutine: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onNewExercise: () -> Unit,
    viewModel: RoutinesViewModel = viewModel(factory = RoutinesViewModel.Factory),
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    var showExercises by rememberSaveable { mutableStateOf(false) }
    var blockedBy by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Routines") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (showExercises) onNewExercise() else onEditRoutine(0) }) {
                Icon(Icons.Filled.Add, contentDescription = if (showExercises) "New exercise" else "New routine")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (showExercises) 1 else 0) {
                Tab(selected = !showExercises, onClick = { showExercises = false }, text = { Text("Routines") })
                Tab(selected = showExercises, onClick = { showExercises = true }, text = { Text("Exercises") })
            }
            if (showExercises) {
                ExerciseList(exercises, onClick = { onOpenExercise(it.id) })
            } else {
                val list = routines ?: return@Column
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (list.isEmpty()) {
                        item { EmptyText("No routines yet. Tap + to plan one, e.g. \"Push day\" or a 20-minute circuit.") }
                    }
                    items(list, key = { it.routine.id }) { r ->
                        RoutineCard(
                            r,
                            onClick = { onEditRoutine(r.routine.id) },
                            onStart = {
                                val current = active
                                if (current != null) blockedBy = current.session.id else viewModel.start(r, onOpenWorkout)
                            },
                        )
                    }
                }
            }
        }
    }

    blockedBy?.let { id ->
        ConfirmDialog(
            title = "A workout is in progress",
            text = "Finish or cancel it before starting another one.",
            confirm = "Go to workout",
            onDismiss = { blockedBy = null },
            onConfirm = {
                blockedBy = null
                onOpenWorkout(id)
            },
        )
    }
}

@Composable
private fun RoutineCard(r: RoutineDetails, onClick: () -> Unit, onStart: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.routine.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(routineSummary(r), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.exercises.isNotEmpty()) {
                Text(r.exercises.joinToString(" · ") { it.exercise.name }, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onStart, enabled = r.exercises.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
            }
        }
    }
}

/** "5 exercises · 15 sets", plus "about 9 min" when every exercise is timed. */
internal fun routineSummary(r: RoutineDetails): String {
    val parts = mutableListOf(
        plural(r.exercises.size, "exercise", "exercises"),
        plural(r.exercises.sumOf { it.entry.sets }, "set", "sets"),
    )
    // Reps take an unknown time, so only all-timed routines get an estimate.
    if (r.exercises.isNotEmpty() && r.exercises.all { it.entry.timed }) {
        // Rest after the very last set doesn't count.
        val seconds = r.exercises.sumOf { it.entry.sets * (it.entry.durationSec + it.entry.restSec) } - r.exercises.last().entry.restSec
        // Rounded up, so a short circuit reads "about 1 min" rather than "0 min".
        if (seconds > 0) parts += "about ${formatDuration((seconds + 59) / 60 * 60_000L)}"
    }
    return parts.joinToString(" · ")
}
