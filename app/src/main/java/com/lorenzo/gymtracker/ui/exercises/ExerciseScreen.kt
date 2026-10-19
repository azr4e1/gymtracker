package com.lorenzo.gymtracker.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import java.time.LocalDate
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.gymtracker.domain.Metric
import com.lorenzo.gymtracker.domain.SessionPoint
import com.lorenzo.gymtracker.ui.components.EmptyText
import com.lorenzo.gymtracker.ui.components.LineChart
import com.lorenzo.gymtracker.ui.components.SectionTitle
import com.lorenzo.gymtracker.ui.components.SegmentedChoice
import com.lorenzo.gymtracker.ui.dateLabel
import com.lorenzo.gymtracker.ui.description
import com.lorenzo.gymtracker.ui.format
import com.lorenzo.gymtracker.ui.formatAxis
import com.lorenzo.gymtracker.ui.label
import com.lorenzo.gymtracker.ui.musclesLabel
import com.lorenzo.gymtracker.ui.plural
import com.lorenzo.gymtracker.ui.summarizeSets
import kotlin.math.abs

/** An exercise's progress chart, personal records and history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(viewModel: ExerciseViewModel, onBack: () -> Unit, onEdit: () -> Unit, onOpenSession: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exercise = state.exercise
    var selected by rememberSaveable(state.metric, state.range) { mutableStateOf<Int?>(null) }
    // Dates on screen in the chart; until it reports them, the last range's worth.
    var window by remember(state.metric, state.range) { mutableStateOf<ClosedRange<LocalDate>?>(null) }
    val today = LocalDate.now()
    val shown = window ?: (state.range.days?.let { today.minusDays(it.toLong()) } ?: LocalDate.MIN)..today
    val visible = state.points.filter { it.date in shown }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (exercise != null && !exercise.hidden) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit exercise") }
                },
            )
        },
    ) { padding ->
        if (exercise == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(exercise.musclesLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (exercise.hidden) {
                        Text("Deleted from your library. Its past workouts are kept.", style = MaterialTheme.typography.bodySmall)
                    }
                    val metric = state.metric
                    if (metric == null) {
                        EmptyText("No sets logged yet.\nYour progress shows here after your first workout with this exercise.")
                        return@Column
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.metrics.forEach { m ->
                            FilterChip(selected = m == metric, onClick = { viewModel.setMetric(m) }, label = { Text(m.label) })
                        }
                    }
                    SegmentedChoice(ProgressRange.entries, state.range, { it.label }, viewModel::setRange)
                    if (state.points.isEmpty()) {
                        EmptyText("Nothing logged in this period.", Modifier.fillMaxWidth().padding(vertical = 48.dp))
                    } else {
                        LineChart(
                            points = state.points.map { it.date to it.value },
                            windowDays = state.range.days,
                            formatValue = metric::formatAxis,
                            selectedIndex = selected,
                            onSelect = { selected = it },
                            onWindowChange = { dates ->
                                window = dates
                                // A selected point scrolled off screen is deselected.
                                val selectedDate = selected?.let { state.points.getOrNull(it)?.date }
                                if (selectedDate != null && selectedDate !in dates) selected = null
                            },
                            description = "${metric.label} per workout. Drag sideways for earlier workouts.",
                        )
                    }
                    Text(metric.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val point = selected?.let { state.points.getOrNull(it) }
                    if (point != null) PointCard(point, metric, onOpen = { onOpenSession(point.sessionId) }) else PeriodCard(visible, metric, canScroll = state.range.days != null && state.points.any { it.date < shown.start })

                    if (state.records.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Personal records", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                state.records.forEach { r ->
                                    Row(Modifier.fillMaxWidth().clickable { onOpenSession(r.sessionId) }, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(r.metric.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${r.metric.format(r.value)} · ${dateLabel(r.date)}")
                                    }
                                }
                            }
                        }
                    }
                    SectionTitle("History", Modifier.padding(top = 8.dp))
                }
            }
            items(state.history, key = { it.sessionId }) { h ->
                ListItem(
                    headlineContent = { Text(dateLabel(h.date)) },
                    supportingContent = { Text(summarizeSets(h.sets, h.timed)) },
                    modifier = Modifier.clickable { onOpenSession(h.sessionId) },
                )
            }
        }
    }
}

@Composable
private fun PointCard(point: SessionPoint, metric: Metric, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dateLabel(point.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${metric.label}: ${metric.format(point.value)}", style = MaterialTheme.typography.titleMedium)
            Text("Tap to open this workout", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Best value and change over the points in the chart. */
@Composable
private fun PeriodCard(points: List<SessionPoint>, metric: Metric, canScroll: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (points.isEmpty()) {
                Text("No workouts in these dates", style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Best on screen: ${metric.format(points.maxOf { it.value })}", style = MaterialTheme.typography.titleMedium)
            }
            if (points.size >= 2) {
                val change = points.last().value - points.first().value
                val sign = if (change > 0) "+" else if (change < 0) "−" else "±"
                Text("$sign${metric.format(abs(change))} since ${dateLabel(points.first().date)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${plural(points.size, "workout", "workouts")} · tap a point for details" + if (canScroll) " · drag for earlier ones" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
