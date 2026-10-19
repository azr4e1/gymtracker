package com.lorenzo.gymtracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** The current time in epoch milliseconds, updated every [intervalMillis] for clocks and countdowns. */
@Composable
fun rememberNow(intervalMillis: Long = 1000): Long {
    val now by produceState(System.currentTimeMillis(), intervalMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMillis)
        }
    }
    return now
}
