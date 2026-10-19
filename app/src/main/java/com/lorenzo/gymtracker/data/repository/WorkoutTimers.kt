package com.lorenzo.gymtracker.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import com.lorenzo.gymtracker.domain.firstUnticked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The rest countdown and the countdown of a timed set. App-wide, so they keep running when the
 * workout screen is closed. The last seconds tick, and a bell rings (and the phone buzzes) when
 * a countdown runs out. Times are epoch milliseconds.
 *
 * Auto-run goes through a workout's timed sets on its own: the first unticked set, its rest, the
 * next one, and so on. It stops before a set done by reps, and when every set is ticked.
 */
class WorkoutTimers(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sessions: SessionRepository,
    private val sounds: TimerSounds,
) {
    data class Rest(val endsAt: Long, val totalSec: Int)

    /** A timed set being done: it's ticked with [plannedSec] when the countdown ends. */
    data class RunningSet(val setId: Long, val startedAt: Long, val plannedSec: Int, val restSec: Int) {
        val endsAt: Long get() = startedAt + plannedSec * 1000L
    }

    private val _rest = MutableStateFlow<Rest?>(null)
    val rest: StateFlow<Rest?> = _rest.asStateFlow()

    private val _running = MutableStateFlow<RunningSet?>(null)
    val running: StateFlow<RunningSet?> = _running.asStateFlow()

    /** Why auto-run stopped on its own. */
    sealed interface AutoStop {
        /** The next set is done by reps: tick its sets, then resume. */
        data class NotTimed(val exerciseName: String) : AutoStop

        data object Finished : AutoStop
    }

    /** Id of the workout being auto-run, or null. */
    private val _autoSession = MutableStateFlow<Long?>(null)
    val autoSession: StateFlow<Long?> = _autoSession.asStateFlow()

    /** Set when auto-run stops by itself, for the screen to explain; cleared with [clearAutoStop]. */
    private val _autoStop = MutableStateFlow<AutoStop?>(null)
    val autoStop: StateFlow<AutoStop?> = _autoStop.asStateFlow()

    private var restJob: Job? = null
    private var runningJob: Job? = null

    init {
        scope.launch { sounds.prepare() }
    }

    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        scheduleRest(Rest(System.currentTimeMillis() + seconds * 1000L, seconds))
    }

    /** Adds (or with a negative value removes) time from the rest. */
    fun adjustRest(seconds: Int) {
        val current = _rest.value ?: return
        val endsAt = current.endsAt + seconds * 1000L
        if (endsAt <= System.currentTimeMillis()) skipRest() else scheduleRest(Rest(endsAt, (current.totalSec + seconds).coerceAtLeast(1)))
    }

    /** Ends the rest now; during auto-run the next set starts. */
    fun skipRest() {
        clearRest()
        if (_autoSession.value != null) scope.launch { advance() }
    }

    private fun clearRest() {
        restJob?.cancel()
        _rest.value = null
    }

    private fun scheduleRest(rest: Rest) {
        restJob?.cancel()
        _rest.value = rest
        restJob = scope.launch {
            countDown(rest.endsAt) {
                _rest.value = null
                // Launched apart: starting the next set ends this rest's job.
                if (_autoSession.value != null) scope.launch { advance() }
            }
        }
    }

    /** Starts the countdown of a timed set. Only one runs at a time. */
    fun startSet(setId: Long, plannedSec: Int, restSec: Int) {
        if (_running.value != null || plannedSec <= 0) return
        clearRest()
        val set = RunningSet(setId, System.currentTimeMillis(), plannedSec, restSec)
        _running.value = set
        runningJob = scope.launch { countDown(set.endsAt) { scope.launch { complete(set, plannedSec) } } }
    }

    /** Stops the running set early: it's ticked with the time actually done (auto-run carries on). */
    fun stopSet() {
        val set = _running.value ?: return
        runningJob?.cancel()
        _running.value = null
        val elapsed = ((System.currentTimeMillis() - set.startedAt) / 1000).toInt().coerceIn(1, set.plannedSec)
        scope.launch { complete(set, elapsed) }
    }

    private suspend fun complete(set: RunningSet, seconds: Int) {
        _running.value = null
        sessions.setDuration(set.setId, seconds)
        sessions.setDone(set.setId, true)
        val auto = _autoSession.value
        // No rest after the last set of an auto-run: it's over.
        val nothingLeft = auto != null && sessions.get(auto)?.let(::firstUnticked) == null
        if (nothingLeft) advance() else if (set.restSec > 0) startRest(set.restSec) else if (auto != null) advance()
    }

    /** Starts auto-run of [sessionId] from its first unticked set, which must be timed. */
    fun startAuto(sessionId: Long) {
        _autoStop.value = null
        _autoSession.value = sessionId
        runningJob?.cancel()
        _running.value = null
        clearRest()
        scope.launch { advance() }
    }

    /** Stops auto-run. A set in progress is abandoned, not ticked, so resuming starts it again. */
    fun stopAuto() {
        _autoSession.value = null
        runningJob?.cancel()
        _running.value = null
        clearRest()
    }

    fun clearAutoStop() {
        _autoStop.value = null
    }

    /** Auto-run: starts the first unticked set, or stops if it's done by reps or there's none. */
    private suspend fun advance() {
        val sessionId = _autoSession.value ?: return
        val next = sessions.get(sessionId)?.let(::firstUnticked)
        when {
            next == null -> endAuto(AutoStop.Finished)
            !next.canRun -> endAuto(AutoStop.NotTimed(next.exercise.exercise.name))
            else -> startSet(next.set.id, next.set.durationSec ?: next.exercise.exercise.durationSec, next.exercise.entry.restSec)
        }
    }

    private fun endAuto(reason: AutoStop) {
        _autoSession.value = null
        _autoStop.value = reason
    }

    /**
     * Waits until [endsAt], ticking at each of the last [TICK_SECONDS] seconds, then rings the
     * bell and calls [onEnd]. Other audio is lowered from the first tick until the bell fades.
     * Cancelling it (skip, stop) ends it silently.
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
            onEnd()
            delay(BELL_MS)
        } finally {
            if (ducking) sounds.release()
        }
    }

    /** Stops both countdowns and auto-run without ticking anything, e.g. when the workout ends. */
    fun cancelAll() {
        stopAuto()
        _autoStop.value = null
    }

    private companion object {
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
