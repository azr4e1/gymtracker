package com.lorenzo.gymtracker.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.domain.MusclePeriod
import com.lorenzo.gymtracker.domain.SECONDARY_SET_SHARE
import com.lorenzo.gymtracker.ui.components.EmptyText
import com.lorenzo.gymtracker.ui.components.MuscleBars
import com.lorenzo.gymtracker.ui.components.SegmentedChoice
import com.lorenzo.gymtracker.ui.dateLabel
import com.lorenzo.gymtracker.ui.formatAmount
import com.lorenzo.gymtracker.ui.label
import com.lorenzo.gymtracker.ui.plural
import com.lorenzo.gymtracker.ui.summarizeSets
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Guide line on the muscle chart: a commonly recommended minimum of weekly sets per muscle. */
private const val WEEKLY_SET_TARGET = 10.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onOpenExercise: (Long) -> Unit, viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMuscles by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Progress") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (showMuscles) 1 else 0) {
                Tab(selected = !showMuscles, onClick = { showMuscles = false }, text = { Text("Exercises") })
                Tab(selected = showMuscles, onClick = { showMuscles = true }, text = { Text("Muscles") })
            }
            if (!state.loaded) return@Column
            if (showMuscles) MuscleProgress(state, viewModel::setPeriod, viewModel::movePeriod) else ExerciseProgress(state, onOpenExercise)
        }
    }
}

@Composable
private fun ExerciseProgress(state: ProgressUiState, onOpenExercise: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.exercises.isEmpty()) {
            item { EmptyText("Finish a workout to see your progress here.\nEvery exercise you train gets its own chart.") }
        }
        items(state.exercises, key = { it.exercise.id }) { t ->
            ListItem(
                headlineContent = { Text(t.exercise.name) },
                supportingContent = { Text("${dateLabel(t.lastDate)} · ${summarizeSets(t.lastSets, t.timed)}") },
                trailingContent = { Text(plural(t.workouts, "workout", "workouts"), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.clickable { onOpenExercise(t.exercise.id) },
            )
        }
    }
}

@Composable
private fun MuscleProgress(state: ProgressUiState, onPeriodChange: (MusclePeriod) -> Unit, onMove: (Int) -> Unit) {
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SegmentedChoice(MusclePeriod.entries, state.period, { it.label }, onPeriodChange)
        // Swiping the card sideways steps through periods: right for earlier, left for later.
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        Card(
            Modifier.fillMaxWidth().pointerInput(state.canGoBack, state.periodsBack) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > swipeThreshold && state.canGoBack) onMove(1)
                        if (dragged < -swipeThreshold && state.periodsBack > 0) onMove(-1)
                    },
                ) { _, delta -> dragged += delta }
            },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onMove(1) }, enabled = state.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (state.period == MusclePeriod.THIS_WEEK) "Sets in the week" else "Sets per week, on average",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(periodLabel(state), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onMove(-1) }, enabled = state.periodsBack > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later")
                    }
                }
                MuscleBars(state.muscleSets, target = WEEKLY_SET_TARGET)
                Text(
                    "Dashed line: ${WEEKLY_SET_TARGET.formatAmount()} sets a week, a common target for each muscle. " +
                        "Muscles that only help in an exercise count ${SECONDARY_SET_SHARE.formatAmount()} per set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val notTrained = Muscle.entries.filter { (state.muscleSets[it] ?: 0.0) == 0.0 }
        if (notTrained.isNotEmpty() && state.lastTrained.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (state.periodsBack == 0) "Not trained in this period" else "Not trained in these weeks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    notTrained.forEach { m ->
                        val last = state.lastTrained[m]
                        Text(
                            "${m.label}: " + (last?.let { "last ${daysAgo(it, today)}" } ?: "never"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** "This week", or the dates shown, e.g. "12 – 18 Oct". */
private fun periodLabel(state: ProgressUiState): String {
    if (state.periodsBack == 0) return if (state.period == MusclePeriod.THIS_WEEK) "This week" else "Last ${state.period.weeks} weeks"
    val sameMonth = state.from.month == state.to.month
    val start = state.from.format(DateTimeFormatter.ofPattern(if (sameMonth) "d" else "d MMM"))
    val end = state.to.format(DateTimeFormatter.ofPattern(if (state.to.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"))
    return "$start – $end"
}

private fun daysAgo(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days < 2 -> dateLabel(date).lowercase()
        days < 60 -> "$days days ago"
        else -> dateLabel(date)
    }
}
