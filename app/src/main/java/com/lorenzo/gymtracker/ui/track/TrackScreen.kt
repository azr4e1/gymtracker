package com.lorenzo.gymtracker.ui.track

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import com.lorenzo.gymtracker.ui.kgToLb
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.lorenzo.gymtracker.domain.firstUnticked
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import com.lorenzo.gymtracker.data.repository.WorkoutTimers
import com.lorenzo.gymtracker.ui.components.AppIcons
import com.lorenzo.gymtracker.ui.components.ConfirmDialog
import com.lorenzo.gymtracker.ui.components.EmptyText
import com.lorenzo.gymtracker.ui.components.NumberField
import com.lorenzo.gymtracker.ui.components.OverflowMenu
import com.lorenzo.gymtracker.ui.components.TextInputDialog
import com.lorenzo.gymtracker.ui.components.rememberNow
import com.lorenzo.gymtracker.ui.dateLabel
import com.lorenzo.gymtracker.ui.formatWeight
import com.lorenzo.gymtracker.ui.formatClock
import com.lorenzo.gymtracker.ui.formatDuration
import com.lorenzo.gymtracker.ui.musclesLabel
import com.lorenzo.gymtracker.ui.plural
import com.lorenzo.gymtracker.ui.summarizeSets
import com.lorenzo.gymtracker.ui.toLocalDate
import kotlin.math.ceil

/**
 * Tracks a workout. In progress, ticking a set starts the rest timer and timed sets have a
 * countdown. A finished workout opens here to correct it: same editing, no timers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    viewModel: TrackViewModel,
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onFinished: (kept: Boolean) -> Unit,
    onDiscarded: () -> Unit,
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val lastTime by viewModel.lastTime.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val autoRunning by viewModel.autoRunning.collectAsStateWithLifecycle()
    val autoStop by viewModel.autoStop.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val d = details ?: return
    val session = d.session
    val active = session.isActive

    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingRest by remember { mutableStateOf<SessionExerciseDetails?>(null) }
    var removing by remember { mutableStateOf<SessionExerciseDetails?>(null) }

    // The phone stays awake during a workout, so the timers stay in view.
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    // Auto-run starts at the first unticked set, and only if that one is timed.
    val next = firstUnticked(d)
    val scope = rememberCoroutineScope()
    LaunchedEffect(autoStop) {
        val stop = autoStop ?: return@LaunchedEffect
        // Shown from its own coroutine: clearing the reason restarts this effect, which would cancel it.
        viewModel.clearAutoStop()
        scope.launch {
            snackbarHostState.showSnackbar(
                when (stop) {
                    is WorkoutTimers.AutoStop.NotTimed -> "Auto-run paused: ${stop.exerciseName} is done by reps. Tick its sets, then Resume."
                    WorkoutTimers.AutoStop.Finished -> "Auto-run done: every set is ticked."
                },
                duration = SnackbarDuration.Long,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (active && autoRunning) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::stopAuto,
                    icon = { Icon(AppIcons.Stop, contentDescription = null) },
                    text = { Text("Stop") },
                )
            } else if (active && next != null && next.canRun) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::startAuto,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    // "Go" at the start; "Resume" once part of the workout is done.
                    text = { Text(if (d.doneSets == 0) "Go" else "Resume") },
                )
            }
        },
        topBar = {
            val totalSets = d.exercises.sumOf { it.sets.size }
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(session.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (active) {
                            ElapsedTime(session.startedAt, d.doneSets, totalSets)
                        } else {
                            Text(
                                "Editing · ${dateLabel(session.startedAt.toLocalDate())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (active) {
                        Button(onClick = { finishing = true }) { Text("Finish") }
                        OverflowMenu("Rename" to { renaming = true }, "Cancel workout" to { discarding = true })
                    } else {
                        TextButton(onClick = onBack) { Text("Done") }
                        OverflowMenu("Rename" to { renaming = true })
                    }
                },
            )
            if (active) SetsProgress(d.doneSets, totalSets)
            }
        },
        bottomBar = {
            val r = rest
            if (active && r != null) {
                RestBar(r, next = next?.exercise?.exercise?.name, onAdjust = viewModel::adjustRest, onSkip = viewModel::skipRest)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            // Room at the bottom for the Go button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d.exercises.isEmpty()) {
                item {
                    EmptyText(
                        if (active) "Add the exercises you're doing.\nTick each set when you finish it: the rest timer starts on its own."
                        else "No exercises.",
                    )
                }
            }
            itemsIndexed(d.exercises, key = { _, e -> e.entry.id }) { index, e ->
                ExerciseCard(
                    exercise = e,
                    lastTime = lastTime[e.exercise.id],
                    active = active,
                    running = running,
                    viewModel = viewModel,
                    menu = listOfNotNull(
                        "Rest time…" to { editingRest = e },
                        (if (e.entry.timed) "Track by reps" else "Track by time") to { viewModel.setTimed(e, !e.entry.timed) },
                        ("Move up" to { viewModel.move(e, -1) }).takeIf { index > 0 },
                        ("Move down" to { viewModel.move(e, 1) }).takeIf { index < d.exercises.lastIndex },
                        "Exercise progress" to { onOpenExercise(e.exercise.id) },
                        "Remove exercise" to { if (e.sets.any { it.done }) removing = e else viewModel.removeExercise(e) },
                    ),
                )
            }
            item {
                OutlinedButton(onClick = onAddExercises, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
            }
        }
    }

    if (finishing) {
        FinishDialog(
            details = d,
            onDismiss = { finishing = false },
            onFinish = { keepUnticked ->
                finishing = false
                viewModel.finish(keepUnticked, onFinished)
            },
        )
    }
    if (discarding) {
        ConfirmDialog(
            title = "Cancel workout?",
            text = "This workout and its sets will be deleted.",
            confirm = "Delete workout",
            dismiss = "Keep training",
            destructive = true,
            onDismiss = { discarding = false },
            onConfirm = {
                discarding = false
                viewModel.discard(onDiscarded)
            },
        )
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
    editingRest?.let { e ->
        RestDialog(
            name = e.exercise.name,
            seconds = e.entry.restSec,
            onDismiss = { editingRest = null },
            onSave = {
                viewModel.setRest(e, it)
                editingRest = null
            },
        )
    }
    removing?.let { e ->
        ConfirmDialog(
            title = "Remove ${e.exercise.name}?",
            text = "Its ${plural(e.sets.count { it.done }, "ticked set", "ticked sets")} will be removed from this workout.",
            confirm = "Remove",
            destructive = true,
            onDismiss = { removing = null },
            onConfirm = {
                viewModel.removeExercise(e)
                removing = null
            },
        )
    }
}

@Composable
private fun ElapsedTime(startedAt: Long, doneSets: Int, totalSets: Int) {
    val now = rememberNow()
    Text(
        "${formatClock(((now - startedAt) / 1000).toInt())} · $doneSets of ${plural(totalSets, "set", "sets")} done",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ExerciseCard(
    exercise: SessionExerciseDetails,
    lastTime: LastTime?,
    active: Boolean,
    running: WorkoutTimers.RunningSet?,
    viewModel: TrackViewModel,
    menu: List<Pair<String, () -> Unit>>,
) {
    val timed = exercise.entry.timed
    // Weights can be typed in pounds: they're converted to kg, and back when switching to lb.
    var inLb by rememberSaveable(exercise.entry.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(exercise.exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${exercise.exercise.musclesLabel} · rest ${formatClock(exercise.entry.restSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (lastTime != null && lastTime.timed == timed) {
                        Text(
                            "Last time: ${summarizeSets(lastTime.sets, timed)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                OverflowMenu(*menu.toTypedArray())
            }
            Column(Modifier.padding(end = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val weighted = exercise.exercise.weighted
                HeaderRow(timed, weighted, inLb, onToggleUnit = { inLb = !inLb })
                exercise.sets.forEachIndexed { i, set ->
                    if (timed) {
                        TimedSetRow(i + 1, set, exercise, weighted, inLb, active, running, viewModel)
                    } else {
                        RepsSetRow(i + 1, set, exercise, weighted, inLb, viewModel)
                    }
                }
            }
            Row {
                TextButton(onClick = { viewModel.addSet(exercise) }) { Text("Add set") }
                exercise.sets.lastOrNull()?.let { last ->
                    TextButton(onClick = { viewModel.removeSet(last) }) { Text("Remove set") }
                }
            }
        }
    }
}

private val SET_NUMBER_WIDTH = 32.dp
private val CHECK_WIDTH = 48.dp

@Composable
private fun HeaderRow(timed: Boolean, weighted: Boolean, inLb: Boolean, onToggleUnit: () -> Unit) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Set", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(SET_NUMBER_WIDTH))
        if (weighted) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { UnitToggle(inLb, onToggleUnit) }
        Text(
            if (timed) "Time (seconds)" else "Reps",
            style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        Text("Done", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(CHECK_WIDTH))
    }
}

/** "kg | lb" with the unit in use highlighted; tapping switches. */
@Composable
private fun UnitToggle(inLb: Boolean, onToggle: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClickLabel = if (inLb) "Show kilograms" else "Show pounds", onClick = onToggle),
    ) {
        listOf(false to "kg", true to "lb").forEach { (lb, label) ->
            val selected = lb == inLb
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** A set's row. Only its tick shows whether it's done. */
@Composable
private fun SetRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SetNumber(number: Int) {
    Text("$number", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(SET_NUMBER_WIDTH))
}

@Composable
private fun DoneToggle(done: Boolean, onToggle: () -> Unit) {
    FilledIconToggleButton(checked = done, onCheckedChange = { onToggle() }, modifier = Modifier.width(CHECK_WIDTH)) {
        Icon(Icons.Filled.Check, contentDescription = if (done) "Untick set" else "Tick set")
    }
}

/** The set's weight, shown and typed in kg or (with [inLb]) pounds. */
@Composable
private fun WeightField(set: SessionSet, inLb: Boolean, viewModel: TrackViewModel, modifier: Modifier) {
    // Keyed on the unit, so switching shows the converted value instead of keeping the typed text.
    key(inLb) {
        NumberField(
            value = set.weightKg?.let { if (inLb) it.kgToLb() else it }?.formatWeight().orEmpty(),
            onValueChange = { viewModel.setWeight(set, it, inLb) },
            label = null,
            placeholder = "–",
            decimals = true,
            modifier = modifier,
        )
    }
}

@Composable
private fun RepsSetRow(
    number: Int,
    set: SessionSet,
    exercise: SessionExerciseDetails,
    weighted: Boolean,
    inLb: Boolean,
    viewModel: TrackViewModel,
) {
    SetRow {
        SetNumber(number)
        if (weighted) WeightField(set, inLb, viewModel, Modifier.weight(1f))
        NumberField(
            value = set.reps?.toString().orEmpty(),
            onValueChange = { viewModel.setReps(set, it) },
            label = null,
            placeholder = "–",
            modifier = Modifier.weight(1f),
        )
        DoneToggle(set.done) { viewModel.toggleDone(exercise, set) }
    }
}

@Composable
private fun TimedSetRow(
    number: Int,
    set: SessionSet,
    exercise: SessionExerciseDetails,
    weighted: Boolean,
    inLb: Boolean,
    active: Boolean,
    running: WorkoutTimers.RunningSet?,
    viewModel: TrackViewModel,
) {
    SetRow {
        SetNumber(number)
        if (weighted) WeightField(set, inLb, viewModel, Modifier.weight(1f))
        if (running?.setId == set.id) {
            Countdown(running.endsAt, running.plannedSec, Modifier.weight(1f))
            FilledTonalIconButton(onClick = viewModel::stopSet) { Icon(AppIcons.Stop, contentDescription = "Stop set") }
        } else {
            NumberField(
                value = set.durationSec?.toString().orEmpty(),
                onValueChange = { viewModel.setDuration(set, it) },
                label = null,
                placeholder = "–",
                suffix = "s",
                modifier = Modifier.weight(1f),
            )
            if (active && !set.done) {
                FilledTonalIconButton(onClick = { viewModel.startSet(exercise, set) }, enabled = running == null) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start set")
                }
            }
        }
        DoneToggle(set.done) { viewModel.toggleDone(exercise, set) }
    }
}

/** Edge-to-edge bar under the header: the share of the workout's sets ticked so far. */
@Composable
private fun SetsProgress(done: Int, total: Int) {
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, label = "sets progress")
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Time left in a running timed set, with a progress bar. */
@Composable
private fun Countdown(endsAt: Long, totalSec: Int, modifier: Modifier) {
    val now = rememberNow(200)
    val left = ((endsAt - now) / 1000.0).coerceAtLeast(0.0)
    Column(modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(formatClock(ceil(left).toInt()), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        LinearProgressIndicator(progress = { (1 - left / totalSec).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

/** Rest countdown above the navigation bar. */
@Composable
private fun RestBar(rest: WorkoutTimers.Rest, next: String?, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    val now = rememberNow(200)
    val left = ((rest.endsAt - now) / 1000.0).coerceAtLeast(0.0)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 4.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (next != null) "Rest · next: $next" else "Rest",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatClock(ceil(left).toInt()), style = MaterialTheme.typography.headlineMedium)
                }
                TextButton(onClick = { onAdjust(-15) }) { Text("−15 s") }
                TextButton(onClick = { onAdjust(15) }) { Text("+15 s") }
                TextButton(onClick = onSkip) { Text("Skip") }
            }
            LinearProgressIndicator(
                progress = { (left / rest.totalSec).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

/** Asks what to do with unticked sets before finishing. */
@Composable
private fun FinishDialog(details: SessionDetails, onDismiss: () -> Unit, onFinish: (keepUnticked: Boolean) -> Unit) {
    val total = details.exercises.sumOf { it.sets.size }
    val unticked = total - details.doneSets
    val now = remember { System.currentTimeMillis() }
    val summary = "${plural(details.doneSets, "set", "sets")} done in ${formatDuration(now - details.session.startedAt)}."
    if (unticked == 0) {
        ConfirmDialog(
            title = "Finish workout?",
            text = summary,
            confirm = "Finish",
            dismiss = "Keep training",
            onDismiss = onDismiss,
            onConfirm = { onFinish(false) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish workout?") },
        text = {
            Text(
                "$summary\n\n${plural(unticked, "set isn't", "sets aren't")} ticked. Count ${if (unticked == 1) "it" else "them"} " +
                    "as done, or leave ${if (unticked == 1) "it" else "them"} out of your log?",
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onFinish(true) }) { Text("Count as done") }
                TextButton(onClick = { onFinish(false) }) { Text("Leave out") }
                TextButton(onClick = onDismiss) { Text("Keep training") }
            }
        },
    )
}

/** Rest time after each set of one exercise in this workout. */
@Composable
private fun RestDialog(name: String, seconds: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(seconds.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in 0..3600 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("After each set of $name in this workout. 0 turns the timer off.", style = MaterialTheme.typography.bodyMedium)
                NumberField(
                    text, { text = it }, "Rest", suffix = "seconds", isError = text.isNotEmpty() && value == null,
                    supportingText = value?.takeIf { it >= 60 }?.let { formatClock(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(30, 60, 90, 120, 180).forEach { preset ->
                        Text(
                            formatClock(preset),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { text = preset.toString() }.padding(8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value!!) }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
