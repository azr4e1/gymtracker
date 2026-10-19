package com.lorenzo.gymtracker.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.lorenzo.gymtracker.domain.CountPeriod
import com.lorenzo.gymtracker.domain.periodsBetween
import com.lorenzo.gymtracker.domain.workoutCounts
import com.lorenzo.gymtracker.ui.components.BarChart
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import com.lorenzo.gymtracker.ui.components.SegmentedChoice
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.ui.components.SectionTitle
import com.lorenzo.gymtracker.ui.components.rememberNow
import com.lorenzo.gymtracker.ui.dateLabel
import com.lorenzo.gymtracker.ui.formatDuration
import com.lorenzo.gymtracker.ui.formatTime
import com.lorenzo.gymtracker.ui.plural
import com.lorenzo.gymtracker.ui.toLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onOpenWorkout: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory),
) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val routines by viewModel.topRoutines.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val finished = history ?: return
    var discarding by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Workout") }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val current = active
                    if (current != null) {
                        ActiveCard(current, onResume = { onOpenWorkout(current.session.id) }, onDiscard = { discarding = current.session.id })
                    } else {
                        StartCard(
                            routines = routines,
                            onEmpty = { viewModel.startEmpty(onOpenWorkout) },
                            onRoutine = { viewModel.startRoutine(it, onOpenWorkout) },
                            onNewRoutine = onNewRoutine,
                        )
                    }
                    WeekCard(finished)
                    if (finished.isNotEmpty()) HistoryCard(finished)
                }
            }
            if (finished.isEmpty()) {
                item {
                    Text(
                        "Your finished workouts will be listed here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            finished.groupBy { weekStart(it.session.startedAt.toLocalDate()) }.forEach { (week, sessions) ->
                item(key = "week-$week") { SectionTitle(weekLabel(week), Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) }
                items(sessions, key = { it.session.id }) { details ->
                    SessionItem(details, onClick = { onOpenSession(details.session.id) })
                }
            }
        }
    }

    discarding?.let { id ->
        ConfirmDialog(
            title = "Discard workout?",
            text = "The workout in progress and its sets will be deleted.",
            confirm = "Discard",
            dismiss = "Keep it",
            destructive = true,
            onDismiss = { discarding = null },
            onConfirm = {
                discarding = null
                viewModel.discard(id)
            },
        )
    }
}

@Composable
private fun ActiveCard(details: SessionDetails, onResume: () -> Unit, onDiscard: () -> Unit) {
    val now = rememberNow()
    Card(
        onClick = onResume,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Workout in progress", style = MaterialTheme.typography.labelLarge)
            Text(details.session.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "Started ${details.session.startedAt.formatTime()} · ${formatDuration(now - details.session.startedAt)} · " +
                    "${details.doneSets} of ${plural(details.exercises.sumOf { it.sets.size }, "set", "sets")} done",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The default outline is too faint on the tinted card.
                OutlinedButton(
                    onClick = onDiscard,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f),
                ) { Text("Discard", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Resume") }
            }
        }
    }
}

@Composable
private fun StartCard(routines: List<RoutineDetails>, onEmpty: () -> Unit, onRoutine: (RoutineDetails) -> Unit, onNewRoutine: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "Start a workout",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Button(onClick = onEmpty, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Empty workout")
            }
            if (routines.isEmpty()) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Save the workouts you repeat as routines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNewRoutine) { Text("New routine") }
                }
            } else {
                Text(
                    "Or start a routine",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                routines.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onRoutine(r) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.routine.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                r.exercises.joinToString(", ") { it.exercise.name }.ifEmpty { "No exercises" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${r.routine.name}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Totals for this week's finished workouts. */
@Composable
private fun WeekCard(finished: List<SessionDetails>) {
    val monday = weekStart(LocalDate.now())
    val week = finished.filter { !it.session.startedAt.toLocalDate().isBefore(monday) }
    val time = week.sumOf { (it.session.endedAt ?: it.session.startedAt) - it.session.startedAt }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("${week.size}", if (week.size == 1) "workout" else "workouts")
                Stat("${week.sumOf { it.doneSets }}", "sets")
                Stat(if (week.isEmpty()) "–" else formatDuration(time), "trained")
            }
        }
    }
}

/** Workouts per week or month, to see how regularly you train. Drags back to the first workout. */
@Composable
private fun HistoryCard(finished: List<SessionDetails>) {
    var period by rememberSaveable { mutableStateOf(CountPeriod.WEEK) }
    val today = LocalDate.now()
    val dates = finished.map { it.session.startedAt.toLocalDate() }
    val periods = maxOf(CHART_PERIODS, dates.minOrNull()?.let { period.periodsBetween(it, today) } ?: 0)
    val counts = workoutCounts(dates, period, today, periods)
    var visible by remember(period, periods) { mutableStateOf<IntRange?>(null) }
    val onScreen = visible?.let { counts.slice(it) } ?: counts.takeLast(CHART_PERIODS)
    val labelFormat = DateTimeFormatter.ofPattern(if (period == CountPeriod.WEEK) "d MMM" else "MMM yy")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Workouts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SegmentedChoice(CountPeriod.entries, period, { if (it == CountPeriod.WEEK) "Per week" else "Per month" }, { period = it })
            BarChart(
                values = counts.map { it.count },
                labels = counts.map { it.start.format(labelFormat) },
                visibleCount = CHART_PERIODS,
                description = "Workouts per ${period.name.lowercase()}. Drag sideways for earlier ones.",
                onVisibleRangeChange = { visible = it },
            )
            val unit = if (period == CountPeriod.WEEK) "weeks" else "months"
            val range = if (onScreen.isEmpty()) "" else " (${onScreen.first().start.format(labelFormat)} – ${onScreen.last().start.format(labelFormat)})"
            Text(
                "${plural(onScreen.sumOf { it.count }, "workout", "workouts")} in these ${onScreen.size} $unit$range · " +
                    "${finished.size} in total" + if (counts.size > CHART_PERIODS) " · drag for earlier ones" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Weeks or months on screen in the workouts chart. */
private const val CHART_PERIODS = 12

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionItem(details: SessionDetails, onClick: () -> Unit) {
    val s = details.session
    ListItem(
        headlineContent = { Text(s.name) },
        supportingContent = {
            Text(
                "${dateLabel(s.startedAt.toLocalDate())} · ${formatDuration((s.endedAt ?: s.startedAt) - s.startedAt)} · " +
                    details.exercises.joinToString(", ") { it.exercise.name },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Text(plural(details.doneSets, "set", "sets"), style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun weekLabel(monday: LocalDate): String {
    val thisWeek = weekStart(LocalDate.now())
    return when (monday) {
        thisWeek -> "This week"
        thisWeek.minusWeeks(1) -> "Last week"
        else -> {
            val sunday = monday.plusDays(6)
            val end = sunday.format(DateTimeFormatter.ofPattern(if (sunday.year == thisWeek.year) "d MMM" else "d MMM yyyy"))
            val start = monday.format(DateTimeFormatter.ofPattern(if (monday.month == sunday.month) "d" else "d MMM"))
            "$start – $end"
        }
    }
}
