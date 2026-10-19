package com.lorenzo.gymtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.ui.formatAmount
import com.lorenzo.gymtracker.ui.label

/**
 * Horizontal bars of sets per muscle, in body order. [target] draws a dashed guide at that many
 * sets on every bar. [showAll] lists muscles with no sets too.
 */
@Composable
fun MuscleBars(values: Map<Muscle, Double>, modifier: Modifier = Modifier, target: Double? = null, showAll: Boolean = true) {
    val shown = Muscle.entries.filter { showAll || (values[it] ?: 0.0) > 0 }
    val scaleMax = maxOf(values.values.maxOrNull() ?: 0.0, target ?: 0.0, 1.0)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { muscle ->
            val value = values[muscle] ?: 0.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    muscle.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val radius = CornerRadius(4.dp.toPx())
                    drawRoundRect(trackColor, cornerRadius = radius)
                    val w = (value / scaleMax * size.width).toFloat()
                    if (w > 0) drawRoundRect(barColor, size = Size(w.coerceAtLeast(4.dp.toPx()), size.height), cornerRadius = radius)
                    target?.let {
                        val gx = (it / scaleMax * size.width).toFloat()
                        drawLine(
                            guideColor, Offset(gx, -2.dp.toPx()), Offset(gx, size.height + 2.dp.toPx()),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                        )
                    }
                }
                Text(
                    if (value > 0) value.formatAmount() else "–",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}
