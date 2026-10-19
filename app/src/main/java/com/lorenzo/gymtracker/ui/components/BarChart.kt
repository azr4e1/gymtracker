package com.lorenzo.gymtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private val MAX_BAR_WIDTH = 28.dp

/**
 * Columns of whole-number [values] with their number on top, oldest first. [visibleCount] fit
 * on screen, ending with the newest; drag sideways for older ones. The newest column (the period
 * still going on) is drawn lighter. [labels] go under the columns; ones that would touch their
 * neighbour are skipped.
 *
 * @param onVisibleRangeChange indices of [values] on screen, each time the scroll settles.
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    visibleCount: Int,
    description: String,
    onVisibleRangeChange: (IntRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val barColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.outlineVariant

    val back = rememberScrollBack(values.size to visibleCount)
    var widthPx by remember { mutableIntStateOf(0) }
    val slot = widthPx.toFloat() / visibleCount
    fun visible(barsBack: Int): IntRange {
        val last = values.lastIndex - barsBack
        return (last - visibleCount + 1).coerceAtLeast(0)..last
    }
    OnScrollSettled(back) { onVisibleRangeChange(visible(it)) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .onSizeChanged { widthPx = it.width }
            .semantics { contentDescription = description }
            .scrollBack(back, maxBack = (values.size - visibleCount).coerceAtLeast(0).toFloat(), unitPx = slot),
    ) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = 20.dp.toPx()
        val valueHeight = 18.dp.toPx()
        val bottom = size.height - labelHeight
        val plotHeight = bottom - valueHeight
        val barWidth = min(slot * 0.6f, MAX_BAR_WIDTH.toPx())
        // Index of the leftmost slot on screen (fractional while dragging).
        val firstSlot = values.size - visibleCount - back.value
        fun centerX(i: Int) = slot * (i - firstSlot) + slot / 2
        // The scale fits the columns on screen once the scroll settles, so it doesn't jump while dragging.
        val max = values.slice(visible(back.targetValue.roundToInt())).maxOrNull()?.coerceAtLeast(1) ?: 1
        val drawn = floor(firstSlot).toInt().coerceAtLeast(0)..min(ceil(firstSlot + visibleCount).toInt(), values.lastIndex)

        drawLine(baseColor, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
            for (i in drawn) {
                val value = values[i]
                if (value <= 0) continue
                val cx = centerX(i)
                // Columns of an earlier, busier stretch may not fit once the scale has settled.
                val top = (bottom - plotHeight * value / max).coerceAtLeast(valueHeight)
                val color = if (i == values.lastIndex) barColor.copy(alpha = 0.5f) else barColor
                val radius = CornerRadius(4.dp.toPx())
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(cx - barWidth / 2, top, cx + barWidth / 2, bottom),
                            topLeft = radius, topRight = radius, bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero,
                        ),
                    )
                }
                drawPath(path, color)
                val text = textMeasurer.measure(value.toString(), valueStyle)
                drawText(text, topLeft = Offset(cx - text.size.width / 2, top - text.size.height - 2.dp.toPx()))
            }

            // Labels are anchored to every n-th column counted from the newest, so they scroll with them.
            val widest = labels.maxOfOrNull { textMeasurer.measure(it, labelStyle).size.width } ?: 0
            val every = ceil((widest + 8.dp.toPx()) / slot).toInt().coerceAtLeast(1)
            for (i in drawn) {
                if ((values.lastIndex - i) % every != 0) continue
                val text = textMeasurer.measure(labels[i], labelStyle)
                // Kept whole at the edges, where the outermost columns' labels would be cut off.
                val x = (centerX(i) - text.size.width / 2).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(x, bottom + 4.dp.toPx()))
            }
        }
    }
}
