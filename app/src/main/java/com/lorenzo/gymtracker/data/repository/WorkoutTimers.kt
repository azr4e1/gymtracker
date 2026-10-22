package com.lorenzo.gymtracker.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.edit
import com.lorenzo.gymtracker.domain.firstUnticked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A countdown: a timed set or a rest. Running until [endsAt], or paused with [pausedRemainingMs] left. */
@Serializable
data class Countdown(
    /** The set being timed; null for a rest. */
    val setId: Long? = null,
    /** Full length, for progress. */
    val totalMs: Long,
    val endsAt: Long = 0,
    val pausedRemainingMs: Long? = null,
    /** For a set: the rest that follows it. */
    val restSec: Int = 0,
) {
    val isRest: Boolean get() = setId == null
    val isPaused: Boolean get() = pausedRemainingMs != null

    fun remainingMs(now: Long): Long = (pausedRemainingMs ?: (endsAt - now)).coerceAtLeast(0)
}

/** Everything the timers do for the workout [sessionId]; saved, so it survives the app being closed. */
@Serializable
data class TimerState(
    val sessionId: Long? = null,
    /** The countdown on screen, running or paused. */
    val current: Countdown? = null,
    /** Timed sets paused in the background because another set was started: set id to time left. */
    val pausedSets: Map<Long, Long> = emptyMap(),
    /** Auto-run: after a set and its rest, the next unticked timed set starts on its own. */
    val auto: Boolean = false,
)

/**
 * The rest countdown and the countdown of a timed set. App-wide, so they keep running when the
 * workout screen is closed. The last seconds tick, and a bell rings (and the phone buzzes) when
 * a countdown runs out. Times are epoch milliseconds.
 *
 * Only one countdown runs at a time. Starting a timed set while another runs pauses the other,
 * which resumes later from where it stopped. Auto-run goes through a workout's timed sets on its
 * own: the first unticked set, its rest, the next one, and so on. It stops before a set done by
 * reps, and when every set is ticked.
 */
class WorkoutTimers(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sessions: SessionRepository,
    private val sounds: TimerSounds,
) {
    /** Why auto-run stopped on its own. */
    sealed interface AutoStop {
        /** The next set is done by reps: tick its sets, then resume. */
        data class NotTimed(val exerciseName: String) : AutoStop

        data object Finished : AutoStop
    }

    private val prefs = context.getSharedPreferences("timers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** Set when auto-run stops by itself, for the screen to explain; cleared with [clearAutoStop]. */
    private val _autoStop = MutableStateFlow<AutoStop?>(null)
    val autoStop: StateFlow<AutoStop?> = _autoStop.asStateFlow()

    /** Counts down [TimerState.current] while it runs. */
    private var job: Job? = null

    /** Completing a set reads and writes the database; one at a time. */
    private val mutex = Mutex()

    init {
        scope.launch {
            sounds.prepare()
            restore()
        }
    }

    // --- Sets ---

    /**
     * Starts (or resumes) the countdown of a timed set of workout [sessionId]. A set running or
     * paused on screen is paused in the background; a rest ends.
     */
    fun startSet(sessionId: Long, setId: Long, plannedSec: Int, restSec: Int) {
        if (plannedSec <= 0) return
        val now = System.currentTimeMillis()
        update { s ->
            val current = s.current
            if (current?.setId == setId) return@update s.copy(current = current.resumed(now))
            // The set on screen keeps its time left, to resume later.
            val parked = if (current != null && !current.isRest) s.pausedSets + (current.setId!! to current.remainingMs(now)) else s.pausedSets
            val remaining = parked[setId] ?: (plannedSec * 1000L)
            s.copy(
                sessionId = sessionId,
                current = Countdown(setId = setId, totalMs = plannedSec * 1000L, endsAt = now + remaining, restSec = restSec),
                pausedSets = parked - setId,
            )
        }
    }

    /** Ends the set on screen now: it's ticked with the time actually done. */
    fun finishSet() {
        val current = _state.value.current ?: return
        if (current.isRest) return
        val done = ((current.totalMs - current.remainingMs(System.currentTimeMillis())) / 1000).toInt().coerceAtLeast(1)
        update { it.copy(current = null) }
        scope.launch { complete(current, done) }
    }

    /** A set ticked or unticked by hand: it's no longer timed, here or in the background. */
    fun forgetSet(setId: Long) {
        update { s -> s.copy(current = s.current?.takeIf { it.setId != setId }, pausedSets = s.pausedSets - setId) }
    }

    // --- Rest ---

    /** Starts a rest, unless a timed set is on screen (its own rest follows it). */
    fun startRest(sessionId: Long, seconds: Int) {
        if (seconds <= 0) return
        val now = System.currentTimeMillis()
        update { s ->
            if (s.current != null && !s.current.isRest) s
            else s.copy(sessionId = sessionId, current = Countdown(totalMs = seconds * 1000L, endsAt = now + seconds * 1000L))
        }
    }

    /** Adds (or with a negative value removes) time from the rest, running or paused. */
    fun adjustRest(seconds: Int) {
        val now = System.currentTimeMillis()
        val rest = _state.value.current?.takeIf { it.isRest } ?: return
        val remaining = rest.remainingMs(now) + seconds * 1000L
        if (remaining <= 0) {
            skipRest()
            return
        }
        val total = (rest.totalMs + seconds * 1000L).coerceAtLeast(1000L)
        update {
            it.copy(current = if (rest.isPaused) rest.copy(totalMs = total, pausedRemainingMs = remaining) else rest.copy(totalMs = total, endsAt = now + remaining))
        }
    }

    /** Ends the rest now; during auto-run the next set starts. */
    fun skipRest() {
        if (_state.value.current?.isRest != true) return
        update { it.copy(current = null) }
        if (_state.value.auto) scope.launch { advance() }
    }

    // --- Pause and auto-run ---

    /** Pauses the countdown on screen, keeping its time left. Auto-run stays on and continues on resume. */
    fun pause() {
        val now = System.currentTimeMillis()
        update { s -> s.copy(current = s.current?.let { c -> if (c.isPaused) c else c.copy(pausedRemainingMs = c.remainingMs(now)) }) }
    }

    /** Resumes the paused countdown from where it stopped. */
    fun resume() {
        val now = System.currentTimeMillis()
        update { s -> s.copy(current = s.current?.resumed(now)) }
    }

    /**
     * Turns auto-run on for [sessionId]. A paused countdown continues where it stopped, a
     * running one carries on; otherwise the first unticked set starts (it must be timed).
     */
    fun startAuto(sessionId: Long) {
        _autoStop.value = null
        val now = System.currentTimeMillis()
        update { s -> s.copy(sessionId = sessionId, auto = true, current = s.current?.resumed(now)) }
        if (_state.value.current == null) scope.launch { advance() }
    }

    /**
     * Turns auto-run off. The countdown on screen carries on (or stays paused); after it and its
     * rest, nothing starts on its own.
     */
    fun stopAuto() {
        update { it.copy(auto = false) }
    }

    fun clearAutoStop() {
        _autoStop.value = null
    }

    /** Stops everything without ticking anything, e.g. when the workout ends. */
    fun cancelAll() {
        update { TimerState() }
        _autoStop.value = null
    }

    // --- Inside ---

    private fun Countdown.resumed(now: Long): Countdown =
        if (pausedRemainingMs == null) this else copy(endsAt = now + pausedRemainingMs, pausedRemainingMs = null)

    /** Changes the state, saves it, and (re)starts the countdown job if what's running changed. */
    private fun update(change: (TimerState) -> TimerState) {
        val before = _state.value
        _state.update(change)
        val after = _state.value
        prefs.edit { putString(KEY_STATE, json.encodeToString(TimerState.serializer(), after)) }
        val running = after.current?.takeIf { !it.isPaused }
        if (running != before.current?.takeIf { !it.isPaused } || job?.isActive != true) {
            job?.cancel()
            job = running?.let { c -> scope.launch { countDown(c.endsAt) { onCountdownEnd(c) } } }
        }
    }

    private fun onCountdownEnd(countdown: Countdown) {
        // Ignore a countdown replaced in the meantime.
        if (_state.value.current != countdown) return
        update { it.copy(current = null) }
        scope.launch {
            if (countdown.isRest) {
                if (_state.value.auto) advance()
            } else {
                complete(countdown, (countdown.totalMs / 1000).toInt())
            }
        }
    }

    /** Ticks a finished set, then starts its rest; in auto-run, or the next set if there's no rest. */
    private suspend fun complete(set: Countdown, seconds: Int) = mutex.withLock {
        sessions.setDuration(set.setId!!, seconds)
        sessions.setDone(set.setId, true)
        val state = _state.value
        val sessionId = state.sessionId ?: return@withLock
        // No rest after the last set of an auto-run: it's over.
        val nothingLeft = state.auto && sessions.get(sessionId)?.let(::firstUnticked) == null
        when {
            nothingLeft -> advance()
            set.restSec > 0 -> startRest(sessionId, set.restSec)
            state.auto -> advance()
        }
    }

    /** Auto-run: starts the first unticked set, or stops if it's done by reps or there's none. */
    private suspend fun advance() {
        val state = _state.value
        val sessionId = state.sessionId ?: return
        if (!state.auto || state.current != null) return
        val next = sessions.get(sessionId)?.let(::firstUnticked)
        when {
            next == null -> endAuto(AutoStop.Finished)
            !next.canRun -> endAuto(AutoStop.NotTimed(next.exercise.exercise.name))
            else -> startSet(sessionId, next.set.id, next.set.durationSec ?: next.exercise.exercise.durationSec, next.exercise.entry.restSec)
        }
    }

    private fun endAuto(reason: AutoStop) {
        update { it.copy(auto = false) }
        _autoStop.value = reason
    }

    /**
     * Picks up the saved state after the app was closed. A countdown still going continues; one
     * that ended meanwhile is completed silently, and auto-run waits for a resume.
     */
    private suspend fun restore() {
        val saved = prefs.getString(KEY_STATE, null)
            ?.let { runCatching { json.decodeFromString(TimerState.serializer(), it) }.getOrNull() }
            ?: return
        val session = saved.sessionId?.let { sessions.get(it) }
        if (session == null || !session.session.isActive) {
            update { TimerState() }
            return
        }
        val current = saved.current
        if (current == null || current.isPaused || current.endsAt > System.currentTimeMillis()) {
            update { saved }
            return
        }
        // Ended while the app was closed: auto-run pauses on the next set rather than surprising.
        update { saved.copy(current = null, auto = false) }
        if (!current.isRest) {
            sessions.setDuration(current.setId!!, (current.totalMs / 1000).toInt())
            sessions.setDone(current.setId, true)
        }
    }

    /**
     * Waits until [endsAt], ticking at each of the last [TICK_SECONDS] seconds, then rings the
     * bell and calls [onEnd]. Other audio is lowered from the first tick until the bell fades.
     * Cancelling it (skip, pause) ends it silently.
     */
    private suspend fun countDown(endsAt: Long, onEnd: () -> Unit) {
        var ducking = false
        fun duck() {
            if (!ducking) sounds.duck()
            ducking = true
        }
        try {
            for (second in TICK_SECONDS downTo 1) {
                val wait = endsAt - second * 1000L - System.currentTimeMillis()
                // A tick that's already well past (e.g. a 2-second timer) is skipped, not played late.
                if (wait < -TICK_LATE_MS) continue
                delay(wait)
                duck()
                sounds.tick()
            }
            delay(endsAt - System.currentTimeMillis())
            duck()
            sounds.bell()
            buzz()
            // The bell rings on after the countdown is over: other audio comes back once it fades,
            // even though what follows (e.g. the next countdown) ends this job straight away.
            ducking = false
            scope.launch {
                delay(BELL_MS)
                sounds.release()
            }
            onEnd()
        } finally {
            if (ducking) sounds.release()
        }
    }

    private companion object {
        const val KEY_STATE = "state"
        const val TICK_SECONDS = 3
        const val TICK_LATE_MS = 300L

        /** How long the bell rings, so other audio comes back after it. */
        const val BELL_MS = 2_400L
    }

    private fun buzz() {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)
        // Alarm usage, so it also buzzes with the app in the background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
