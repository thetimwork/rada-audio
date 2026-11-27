package africa.myrada.audio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A line icon from SVG path data on a 24 × 24 grid, drawn with a 1.8 stroke like the design. */
@Composable
fun StrokeIcon(pathData: String, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        scale(s, s, Offset.Zero) {
            drawPath(path, color, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

object TabIcons {
    const val HOME = "M3 10.5L12 3l9 7.5V21h-6v-6H9v6H3z"
    const val EQ = "M5 3v18M12 3v18M19 3v18M2 8h6M9 15h6M16 6h6"
    const val SPACE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M3 12h18M12 3c-4.5 5-4.5 13 0 18M12 3c4.5 5 4.5 13 0 18"
    const val DEVICES = "M3 17v-4a9 9 0 0 1 18 0v4M3 14h4v7H3zM17 14h4v7h-4z"
    const val STUDIO = "M2 12h3l3-8 4 16 4-13 3 5h3"
    const val ABOUT = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M12 11v6M12 7.5v.5"
}
