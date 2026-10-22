package com.lorenzo.gymtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.lorenzo.gymtracker.ui.navigation.GymTrackerNavHost
import com.lorenzo.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {
    /** A workout to open, from the timer notification or a widget; cleared once opened. */
    private var openWorkout by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            GymTrackerTheme { GymTrackerNavHost(openWorkout = openWorkout, onWorkoutOpened = { openWorkout = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * Opens a workout, or (from a widget) starts one first. Starting while another workout is
     * in progress opens that one instead.
     */
    private fun handle(intent: Intent) {
        val sessions = (application as GymTrackerApp).container.sessionRepository
        val routineId = intent.getLongExtra(EXTRA_START_ROUTINE, 0)
        when {
            routineId != 0L -> lifecycleScope.launch {
                val routine = (application as GymTrackerApp).container.routineRepository.get(routineId) ?: return@launch
                openWorkout = sessions.startRoutine(routine)
            }
            intent.getBooleanExtra(EXTRA_START_EMPTY, false) -> lifecycleScope.launch { openWorkout = sessions.startEmpty() }
            else -> intent.getLongExtra(EXTRA_OPEN_WORKOUT, 0).takeIf { it != 0L }?.let { openWorkout = it }
        }
        // Handled once: not again when the activity is recreated.
        intent.removeExtra(EXTRA_START_ROUTINE)
        intent.removeExtra(EXTRA_START_EMPTY)
        intent.removeExtra(EXTRA_OPEN_WORKOUT)
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "openWorkout"
        const val EXTRA_START_ROUTINE = "startRoutine"
        const val EXTRA_START_EMPTY = "startEmpty"
    }
}
