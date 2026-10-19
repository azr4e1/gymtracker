package com.lorenzo.gymtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val Y_AXIS_WIDTH = 44.dp
private val X_AXIS_HEIGHT = 22.dp
private val INSET = 8.dp

/**
 * Values over time: a line through [points] (oldest first), placed by date. [windowDays] days
 * ending today fit on screen; drag sideways for older ones. With [windowDays] null everything
 * from the first point fits and there's nothing to scroll. The y-axis fits the points on screen.
 * Tapping near a point selects it, tapping it again deselects.
 *
 * @param onWindowChange the dates on screen, each time the scroll settles.
 */
@Composable
fun LineChart(
    points: List<Pair<LocalDate, Double>>,
    windowDays: Int?,
    formatValue: (Double) -> String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onWindowChange: (ClosedRange<LocalDate>) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    val today = LocalDate.now()
    val totalDays = points.firstOrNull()?.let { ChronoUnit.DAYS.between(it.first, today) }?.coerceAtLeast(1) ?: 1
    val span = windowDays?.toLong() ?: totalDays
    val back = rememberScrollBack(span)
    var widthPx by remember { mutableIntStateOf(0) }
    val dayPx = with(density) { (widthPx - Y_AXIS_WIDTH.toPx() - 2 * INSET.toPx()) / span }
    OnScrollSettled(back) { daysBack ->
        val end = today.minusDays(daysBack.toLong())
        onWindowChange(end.minusDays(span)..end)
    }

    // Gestures are set up once, so they must read the latest values through these.
    val currentPoints by rememberUpdatedState(points)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentDayPx by rememberUpdatedState(dayPx)
    val currentSpan by rememberUpdatedState(span)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .onSizeChanged { widthPx = it.width }
            .semantics { contentDescription = description }
            .scrollBack(back, maxBack = (totalDays - span).coerceAtLeast(0).toFloat(), unitPx = dayPx)
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    fun x(date: LocalDate) = Y_AXIS_WIDTH.toPx() + INSET.toPx() +
                        (ChronoUnit.DAYS.between(today, date) + back.value + currentSpan) * currentDayPx
                    val nearest = currentPoints.indices.minByOrNull { abs(x(currentPoints[it].first) - tap.x) }
                    val hit = nearest != null && abs(x(currentPoints[nearest].first) - tap.x) < 32.dp.toPx()
                    currentOnSelect(if (hit && nearest != currentSelected) nearest else null)
                }
            },
    ) {
        val left = Y_AXIS_WIDTH.toPx()
        val plotBottom = size.height - X_AXIS_HEIGHT.toPx()
        val plotTop = 8.dp.toPx()
        // Days from today, shifted by the (fractional) scroll.
        fun x(date: LocalDate) = left + INSET.toPx() + (ChronoUnit.DAYS.between(today, date) + back.value + span) * dayPx

        // The y-axis fits the points on screen once the scroll settles, so it doesn't jump while dragging.
        val settledEnd = today.minusDays(back.targetValue.roundToInt().toLong())
        val settledStart = settledEnd.minusDays(span)
        val inView = points.filter { it.first in settledStart..settledEnd }.ifEmpty { points }.map { it.second }
        val (yMin, yMax, step) = niceAxis(inView.minOrNull() ?: 0.0, inView.maxOrNull() ?: 1.0)
        fun y(v: Double) = (plotBottom - (v - yMin) / (yMax - yMin) * (plotBottom - plotTop)).toFloat()

        var tick = yMin
        while (tick <= yMax + step / 1000) {
            val ty = y(tick)
            drawLine(gridColor, Offset(left, ty), Offset(size.width, ty), strokeWidth = 1.dp.toPx())
            val label = textMeasurer.measure(formatValue(tick), labelStyle)
            drawText(label, topLeft = Offset(left - 6.dp.toPx() - label.size.width, ty - label.size.height / 2))
            tick += step
        }

        clipRect(left = left, top = 0f, right = size.width, bottom = size.height) {
            // Date labels sit on fixed dates (counted back from today), so they scroll with the line.
            val labelEvery = (span / 3).coerceAtLeast(1)
            val formatter = DateTimeFormatter.ofPattern(if (span > 300) "MMM yy" else "d MMM")
            var nextLabelLeft = Float.MAX_VALUE
            var k = 0L
            while (true) {
                val date = today.minusDays(k * labelEvery)
                val center = x(date)
                if (center < left - 50.dp.toPx()) break
                k++
                // Scrolled off the right edge (only newer dates are there).
                if (center > size.width) continue
                val label = textMeasurer.measure(date.format(formatter), labelStyle)
                // Kept whole at the right edge, where today's label would be cut in half.
                val lx = (center - label.size.width / 2).coerceAtMost(size.width - label.size.width)
                if (lx + label.size.width + 8.dp.toPx() <= nextLabelLeft && lx >= left) {
                    drawText(label, topLeft = Offset(lx, plotBottom + 4.dp.toPx()))
                    nextLabelLeft = lx
                }
            }

            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, (date, v) -> if (i == 0) path.moveTo(x(date), y(v)) else path.lineTo(x(date), y(v)) }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Dots with a surface-coloured ring, so they stay distinct where the line passes through.
            points.forEachIndexed { i, (date, v) ->
                val center = Offset(x(date), y(v))
                if (center.x < left - 10.dp.toPx() || center.x > size.width + 10.dp.toPx()) return@forEachIndexed
                val selected = i == selectedIndex
                if (selected) drawLine(gridColor, Offset(center.x, plotTop), Offset(center.x, plotBottom), strokeWidth = 1.dp.toPx())
                drawCircle(surface, radius = if (selected) 8.dp.toPx() else 6.dp.toPx(), center = center)
                drawCircle(lineColor, radius = if (selected) 6.dp.toPx() else 4.dp.toPx(), center = center)
            }
        }
    }
}

/** A y-axis range around [min]..[max] with a round step, giving about 3–5 grid lines. */
internal fun niceAxis(min: Double, max: Double): Triple<Double, Double, Double> {
    val span = (max - min).takeIf { it > 0 } ?: (abs(max) * 0.2).coerceAtLeast(1.0)
    val raw = span / 3
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val low = (floor((min - span * 0.1) / step) * step).coerceAtLeast(0.0)
    val high = ceil((max + span * 0.1) / step) * step
    return Triple(low, if (high > low) high else low + step, step)
}
