package com.lorenzo.gymtracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons not in the core Material set. Colours here are placeholders; Icon() tints them. */
object AppIcons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach { path ->
                addPath(
                    pathData = addPathNodes(path),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            fills.forEach { path -> addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)) }
        }.build()

    /** Dumbbell (Material "fitness_center"). */
    val Dumbbell: ImageVector by lazy {
        icon(
            "Dumbbell",
            fills = listOf(
                "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 " +
                    "2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22 " +
                    "l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29z",
            ),
        )
    }

    /** Rising line chart. */
    val Chart: ImageVector by lazy { icon("Chart", "M3,3v18h18", "M7,15l4,-4 3,3 6,-6") }

    /** Clipboard with lines, for routines. */
    val Routine: ImageVector by lazy {
        icon(
            "Routine",
            "M9,4H7a2,2 0 0 0 -2,2v13a2,2 0 0 0 2,2h10a2,2 0 0 0 2,-2V6a2,2 0 0 0 -2,-2h-2",
            "M9,3h6v3H9z",
            "M9,11h6",
            "M9,15h4",
        )
    }

    val Stop: ImageVector by lazy { icon("Stop", fills = listOf("M7,7h10v10H7z")) }

    val ArrowUp: ImageVector by lazy { icon("ArrowUp", "M12,19V5", "M5,12l7,-7 7,7") }

    val ArrowDown: ImageVector by lazy { icon("ArrowDown", "M12,5v14", "M19,12l-7,7 -7,-7") }
}
