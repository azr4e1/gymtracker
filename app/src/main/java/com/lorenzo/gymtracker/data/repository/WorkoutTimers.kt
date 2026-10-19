package com.lorenzo.gymtracker.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
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

    fun skipRest() {
        restJob?.cancel()
        _rest.value = null
    }

    private fun scheduleRest(rest: Rest) {
        restJob?.cancel()
        _rest.value = rest
        restJob = scope.launch { countDown(rest.endsAt) { _rest.value = null } }
    }

    /** Starts the countdown of a timed set. Only one runs at a time. */
    fun startSet(setId: Long, plannedSec: Int, restSec: Int) {
        if (_running.value != null || plannedSec <= 0) return
        skipRest()
        val set = RunningSet(setId, System.currentTimeMillis(), plannedSec, restSec)
        _running.value = set
        runningJob = scope.launch { countDown(set.endsAt) { complete(set, plannedSec) } }
    }

    /** Stops the running set early: it's ticked with the time actually done. */
    fun stopSet() {
        val set = _running.value ?: return
        runningJob?.cancel()
        val elapsed = ((System.currentTimeMillis() - set.startedAt) / 1000).toInt().coerceIn(1, set.plannedSec)
        complete(set, elapsed)
    }

    private fun complete(set: RunningSet, seconds: Int) {
        _running.value = null
        scope.launch {
            sessions.setDuration(set.setId, seconds)
            sessions.setDone(set.setId, true)
        }
        startRest(set.restSec)
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

    /** Stops both countdowns without ticking anything, e.g. when the workout ends. */
    fun cancelAll() {
        runningJob?.cancel()
        _running.value = null
        skipRest()
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
