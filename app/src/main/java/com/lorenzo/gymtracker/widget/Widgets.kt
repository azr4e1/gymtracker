package com.lorenzo.gymtracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lorenzo.gymtracker.GymTrackerApp
import com.lorenzo.gymtracker.MainActivity
import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.domain.CountPeriod
import com.lorenzo.gymtracker.domain.MusclePeriod
import com.lorenzo.gymtracker.domain.startOf
import com.lorenzo.gymtracker.domain.weeklyMuscleSets
import com.lorenzo.gymtracker.domain.workoutCounts
import com.lorenzo.gymtracker.ui.formatAmount
import com.lorenzo.gymtracker.ui.formatDuration
import com.lorenzo.gymtracker.ui.formatTime
import com.lorenzo.gymtracker.ui.label
import com.lorenzo.gymtracker.ui.plural
import com.lorenzo.gymtracker.ui.toLocalDate
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

// Home-screen widgets. Each observes the database, so it updates while it's alive; the app also
// refreshes them all when workouts or routines change (see GymTrackerApp).

/** Refreshes every widget, e.g. after a workout or routine changed. */
suspend fun updateAllWidgets(context: Context) {
    QuickStartWidget().updateAll(context)
    WeekWidget().updateAll(context)
    MusclesWidget().updateAll(context)
}

/** Opens the app; with [extras] it starts or opens a workout (see MainActivity). */
private fun openApp(context: Context, extras: Intent.() -> Unit = {}): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply(extras))

@Composable
private fun WidgetCard(onClick: Action, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(14.dp).clickable(onClick),
    ) { content() }
}

@Composable
private fun Title(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
}

// --- Quick start ---

/** Starts an empty workout or one of the most used routines; while a workout is on, shows it with Resume. */
class QuickStartWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as GymTrackerApp).container
        val sessions = container.sessionRepository
        val data = combine(sessions.observeActive(), container.routineRepository.observeAll(), sessions.observeFinished()) { active, routines, finished ->
            val uses = finished.groupingBy { it.session.routineId }.eachCount()
            active to routines.sortedWith(compareByDescending<RoutineDetails> { uses[it.routine.id] ?: 0 }.thenBy { it.routine.name.lowercase() }).take(3)
        }
        val initial = data.first()
        provideContent {
            val state by data.collectAsState(initial)
            GlanceTheme { QuickStart(context, state.first, state.second) }
        }
    }

    @Composable
    private fun QuickStart(context: Context, active: SessionDetails?, routines: List<RoutineDetails>) {
        if (active != null) {
            val total = active.exercises.sumOf { it.sets.size }
            WidgetCard(openApp(context) { putExtra(MainActivity.EXTRA_OPEN_WORKOUT, active.session.id) }) {
                Title("Workout in progress")
                Text(active.session.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Hint("Started ${active.session.startedAt.formatTime()} · ${active.doneSets} of ${plural(total, "set", "sets")} done")
                Spacer(GlanceModifier.height(8.dp))
                Pill("Resume", openApp(context) { putExtra(MainActivity.EXTRA_OPEN_WORKOUT, active.session.id) })
            }
            return
        }
        WidgetCard(openApp(context)) {
            Title("Start a workout")
            Spacer(GlanceModifier.height(6.dp))
            Pill("Empty workout", openApp(context) { putExtra(MainActivity.EXTRA_START_EMPTY, true) })
            routines.forEach { r ->
                Spacer(GlanceModifier.height(6.dp))
                Pill("▶  ${r.routine.name}", openApp(context) { putExtra(MainActivity.EXTRA_START_ROUTINE, r.routine.id) }, tonal = true)
            }
        }
    }

    @Composable
    private fun Pill(text: String, onClick: Action, tonal: Boolean = false) {
        Box(
            GlanceModifier.fillMaxWidth().height(36.dp).cornerRadius(18.dp)
                .background(if (tonal) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.primary)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = TextStyle(
                    color = if (tonal) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

class QuickStartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickStartWidget()
}

// --- This week ---

/** Workouts, sets and time this week, and workouts per week for the last weeks as bars. */
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val finished = (context.applicationContext as GymTrackerApp).container.sessionRepository.observeFinished()
        val initial = finished.first()
        provideContent {
            val sessions by finished.collectAsState(initial)
            GlanceTheme { Week(context, sessions) }
        }
    }

    @Composable
    private fun Week(context: Context, finished: List<SessionDetails>) {
        val today = LocalDate.now()
        val monday = CountPeriod.WEEK.startOf(today)
        val week = finished.filter { !it.session.startedAt.toLocalDate().isBefore(monday) }
        val time = week.sumOf { (it.session.endedAt ?: it.session.startedAt) - it.session.startedAt }
        val counts = workoutCounts(finished.map { it.session.startedAt.toLocalDate() }, CountPeriod.WEEK, today, periods = 8)
        val max = counts.maxOf { it.count }.coerceAtLeast(1)
        WidgetCard(openApp(context)) {
            Title("This week")
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                Stat("${week.size}", if (week.size == 1) "workout" else "workouts", GlanceModifier.defaultWeight())
                Stat("${week.sumOf { it.doneSets }}", "sets", GlanceModifier.defaultWeight())
                Stat(if (week.isEmpty()) "–" else formatDuration(time), "trained", GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
            // Workouts per week, oldest first; this week (still going) lighter.
            Row(GlanceModifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.Bottom) {
                counts.forEachIndexed { i, c ->
                    Box(GlanceModifier.defaultWeight().height(44.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            GlanceModifier.width(14.dp).height((4 + 40 * c.count / max).dp).cornerRadius(3.dp)
                                .background(if (i == counts.lastIndex) GlanceTheme.colors.secondary else GlanceTheme.colors.primary),
                        ) {}
                    }
                }
            }
            Hint("Workouts per week, last ${counts.size} weeks")
        }
    }

    @Composable
    private fun Stat(value: String, label: String, modifier: GlanceModifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

// --- Muscles this week ---

/** Sets per muscle this week, most trained first, and the muscles not trained yet. */
class MusclesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as GymTrackerApp).container
        val data = combine(container.sessionRepository.observeDoneSets(), container.exerciseRepository.observeAll()) { sets, exercises ->
            weeklyMuscleSets(sets, exercises.associateBy { it.id }, MusclePeriod.THIS_WEEK, LocalDate.now())
        }
        val initial = data.first()
        provideContent {
            val values by data.collectAsState(initial)
            GlanceTheme { Muscles(context, values) }
        }
    }

    @Composable
    private fun Muscles(context: Context, values: Map<Muscle, Double>) {
        val trained = values.entries.sortedByDescending { it.value }
        val max = trained.firstOrNull()?.value?.coerceAtLeast(1.0) ?: 1.0
        val notTrained = Muscle.entries.filter { it !in values }
        WidgetCard(openApp(context)) {
            Title("Sets this week")
            if (trained.isEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Hint("Nothing yet this week. Finished workouts show here.")
            }
            // The most trained first; as many as fit, the widget cuts off the rest.
            trained.take(MAX_ROWS).forEach { (muscle, sets) ->
                Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(muscle.label, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.width(84.dp))
                    LinearProgressIndicator(
                        progress = (sets / max).toFloat(),
                        modifier = GlanceModifier.defaultWeight().height(8.dp),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.surfaceVariant,
                    )
                    Text(
                        sets.formatAmount(),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.width(36.dp).padding(start = 6.dp),
                    )
                }
            }
            if (trained.isNotEmpty() && notTrained.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Hint("Not yet: " + notTrained.joinToString(", ") { it.label })
            }
        }
    }

    private companion object {
        const val MAX_ROWS = 8
    }
}

class MusclesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusclesWidget()
}
