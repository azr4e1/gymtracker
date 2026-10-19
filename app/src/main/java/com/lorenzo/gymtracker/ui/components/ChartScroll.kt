package com.lorenzo.gymtracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Shared by the charts: a window onto the newest data that drags back to older data.

/**
 * How far a chart is scrolled back from its newest data, in the chart's own units (days, bars).
 * 0 shows the newest; fractional while dragging. Starts over when [key] changes.
 */
@Composable
fun rememberScrollBack(key: Any?): Animatable<Float, AnimationVector1D> = remember(key) { Animatable(0f) }

/**
 * Horizontal drag that scrolls [back] between 0 and [maxBack] units, [unitPx] pixels each, then
 * settles on a whole unit (coasting a little after a fling). Dragging right goes back in time.
 */
@Composable
fun Modifier.scrollBack(back: Animatable<Float, AnimationVector1D>, maxBack: Float, unitPx: Float): Modifier {
    val scope = rememberCoroutineScope()
    val max by rememberUpdatedState(maxBack)
    val unit by rememberUpdatedState(unitPx.coerceAtLeast(1f))
    val state = rememberDraggableState { delta ->
        scope.launch { back.snapTo((back.value + delta / unit).coerceIn(0f, max)) }
    }
    return draggable(
        state = state,
        orientation = Orientation.Horizontal,
        enabled = maxBack > 0,
        onDragStopped = { velocity ->
            val target = (back.value + velocity / unit * 0.15f).roundToInt().toFloat().coerceIn(0f, max)
            back.animateTo(target)
        },
    )
}

/** Calls [onChange] with the whole number of units scrolled back, once the scroll settles on it. */
@Composable
fun OnScrollSettled(back: Animatable<Float, AnimationVector1D>, onChange: (Int) -> Unit) {
    val callback by rememberUpdatedState(onChange)
    LaunchedEffect(back) {
        snapshotFlow { back.targetValue.roundToInt() }.collect { callback(it) }
    }
}
