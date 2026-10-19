package com.lorenzo.gymtracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.domain.SECONDARY_SET_SHARE
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import com.lorenzo.gymtracker.ui.components.MuscleBars
import com.lorenzo.gymtracker.ui.components.OverflowMenu
import com.lorenzo.gymtracker.ui.components.TextInputDialog
import com.lorenzo.gymtracker.ui.dateLabel
import com.lorenzo.gymtracker.ui.format
import com.lorenzo.gymtracker.ui.formatAmount
import com.lorenzo.gymtracker.ui.formatWeight
import com.lorenzo.gymtracker.ui.formatClock
import com.lorenzo.gymtracker.ui.formatCompact
import com.lorenzo.gymtracker.ui.formatDuration
import com.lorenzo.gymtracker.ui.formatTime
import com.lorenzo.gymtracker.ui.label
import com.lorenzo.gymtracker.ui.toLocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onRoutineCreated: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val s = state ?: return
    val session = s.details.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit workout") }
                    OverflowMenu(
                        "Rename" to { renaming = true },
                        "Save as routine" to { viewModel.saveAsRoutine(onRoutineCreated) },
                        "Delete workout" to { deleting = true },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val end = session.endedAt
                        Text(
                            "${dateLabel(session.startedAt.toLocalDate())} · ${session.startedAt.formatTime()}" +
                                (end?.let { "–${it.formatTime()}" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat(formatDuration((end ?: session.startedAt) - session.startedAt), "duration")
                            Stat("${s.details.doneSets}", "sets")
                            if (s.volumeKg > 0) Stat("${s.volumeKg.formatCompact()} kg", "volume")
                        }
                    }
                }
            }
            if (s.records.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Star, contentDescription = null)
                                Text("New records", style = MaterialTheme.typography.titleMedium)
                            }
                            s.records.forEach { r ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${r.exercise.name} · ${r.metric.label.lowercase()}", modifier = Modifier.weight(1f))
                                    Text(r.metric.format(r.value), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
            if (s.muscles.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Muscles worked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            MuscleBars(s.muscles, showAll = false)
                            Text(
                                "Sets per muscle. Muscles that only help count ${SECONDARY_SET_SHARE.formatAmount()} per set.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(s.details.exercises, key = { it.entry.id }) { e -> ExerciseSummary(e, onClick = { onOpenExercise(e.exercise.id) }) }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Rename workout",
            initial = session.name,
            label = "Name",
            confirm = "Save",
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete workout?",
            text = "This workout and its sets will be removed from your history and progress.",
            confirm = "Delete",
            destructive = true,
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                viewModel.delete(onBack)
            },
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseSummary(e: SessionExerciseDetails, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            e.sets.forEachIndexed { i, set ->
                val color = if (set.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Row {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                    Text(setLabel(set, e.entry.timed) + if (set.done) "" else "  (not done)", color = color)
                }
            }
        }
    }
}

private fun setLabel(set: SessionSet, timed: Boolean): String = when {
    timed -> formatClock(set.durationSec ?: 0) + (set.weightKg?.let { " · ${it.formatWeight()} kg" } ?: "")
    set.weightKg != null && set.weightKg > 0 -> "${set.weightKg.formatWeight()} kg × ${set.reps ?: "–"}"
    else -> "${set.reps ?: "–"} reps"
}
