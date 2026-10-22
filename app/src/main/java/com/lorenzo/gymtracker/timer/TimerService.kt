package com.lorenzo.gymtracker.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lorenzo.gymtracker.GymTrackerApp
import com.lorenzo.gymtracker.MainActivity
import com.lorenzo.gymtracker.R
import com.lorenzo.gymtracker.data.repository.Countdown
import com.lorenzo.gymtracker.data.repository.TimerState
import com.lorenzo.gymtracker.domain.timerTitle
import com.lorenzo.gymtracker.ui.formatClock
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows the timer on screen as a notification with a progress bar and buttons, and keeps the
 * app running while a countdown is on (so it keeps counting, ticking and ringing with the screen
 * off). Started when a countdown appears; stops itself shortly after the last one ends.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val timers get() = (application as GymTrackerApp).container.timers
    private val sessions get() = (application as GymTrackerApp).container.sessionRepository

    /** The title for the countdown it was worked out for (it needs the database). */
    private var title: Pair<Countdown, String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Workout timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The set or rest counting down, with buttons to pause or skip it"
                setShowBadge(false)
            },
        )
        scope.launch { updateLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must be followed by startForeground, even if it's already running.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(timers.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        when (intent?.action) {
            ACTION_PAUSE -> timers.pause()
            ACTION_RESUME -> timers.resume()
            ACTION_SKIP_REST -> timers.skipRest()
            ACTION_FINISH_SET -> timers.finishSet()
            ACTION_STOP_AUTO -> timers.stopAuto()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Refreshes the notification every second; stops once no countdown has been on for a moment. */
    private suspend fun updateLoop() {
        val manager = getSystemService(NotificationManager::class.java)
        var idleSince: Long? = null
        while (true) {
            val state = timers.state.value
            val now = System.currentTimeMillis()
            if (state.current == null) {
                // Between a set and its rest, or a rest and the next set, there's none for an instant.
                idleSince = idleSince ?: now
                if (now - idleSince > IDLE_STOP_MS) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }
            } else {
                idleSince = null
                refreshTitle(state)
                manager.notify(NOTIFICATION_ID, build(state))
            }
            delay(if (state.current?.isPaused == false) 1000 else 300)
        }
    }

    private suspend fun refreshTitle(state: TimerState) {
        val current = state.current ?: return
        // Worked out again only when the countdown changes, not every second.
        val key = current.copy(endsAt = 0, pausedRemainingMs = null)
        if (title?.first == key) return
        val details = state.sessionId?.let { sessions.get(it) } ?: return
        title = key to timerTitle(current, details)
    }

    private fun build(state: TimerState): android.app.Notification {
        val current = state.current
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openWorkout(this, state.sessionId))
        if (current == null) return builder.setContentTitle("Workout timer").build()

        val now = System.currentTimeMillis()
        val leftMs = current.remainingMs(now)
        val left = formatClock(ceil(leftMs / 1000.0).toInt())
        val key = current.copy(endsAt = 0, pausedRemainingMs = null)
        val name = title?.takeIf { it.first == key }?.second ?: if (current.isRest) "Rest" else "Timed set"
        val totalSec = (current.totalMs / 1000).toInt().coerceAtLeast(1)
        val doneSec = ((current.totalMs - leftMs) / 1000).toInt()
        builder
            .setContentTitle((if (state.auto) "Auto-run · " else "") + name)
            .setContentText(if (current.isPaused) "Paused · $left left" else "$left left")
            // Empties as the time runs out, like on screen.
            .setProgress(totalSec, totalSec - doneSec, false)
        if (current.isPaused) {
            builder.addAction(0, "Resume", action(ACTION_RESUME))
        } else {
            builder.addAction(0, "Pause", action(ACTION_PAUSE))
        }
        if (current.isRest) builder.addAction(0, "Skip", action(ACTION_SKIP_REST)) else builder.addAction(0, "Done", action(ACTION_FINISH_SET))
        if (state.auto) builder.addAction(0, "Stop auto-run", action(ACTION_STOP_AUTO))
        return builder.build()
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this, name.hashCode(), Intent(this, TimerService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 1
        private const val IDLE_STOP_MS = 2_000L
        private const val ACTION_PAUSE = "com.lorenzo.gymtracker.PAUSE"
        private const val ACTION_RESUME = "com.lorenzo.gymtracker.RESUME"
        private const val ACTION_SKIP_REST = "com.lorenzo.gymtracker.SKIP_REST"
        private const val ACTION_FINISH_SET = "com.lorenzo.gymtracker.FINISH_SET"
        private const val ACTION_STOP_AUTO = "com.lorenzo.gymtracker.STOP_AUTO"

        /** Starts the service; it does nothing if the app isn't allowed to start it now. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java)) }
        }

        /** Opens the app on the workout [sessionId] (or the start screen if null). */
        fun openWorkout(context: Context, sessionId: Long?): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { sessionId?.let { putExtra(MainActivity.EXTRA_OPEN_WORKOUT, it) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
