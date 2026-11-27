package africa.myrada.audio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Edge = Color(0xFF8D7560)
private val Copper = Color(0xFFEDA86E)
private val Dark = Color(0xFF141312)

private fun cup(o: Offset, w: Float, h: Float) = Brush.radialGradient(
    listOf(Color(0xFF6B625A), Color(0xFF2A2725), Dark),
    center = Offset(o.x + w * 0.35f, o.y + h * 0.3f), radius = maxOf(w, h) * 0.9f,
)

/** Draws in a 320 x 210 design space, scaled and centred into the canvas. */
private inline fun DrawScope.designSpace(block: DrawScope.() -> Unit) {
    val s = min(size.width / 320f, size.height / 210f)
    translate((size.width - 320f * s) / 2f, (size.height - 210f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { block() }
    }
}

private fun DrawScope.shadowUnder() =
    drawOval(Color(0x73000000), Offset(40f, 181f), Size(240f, 22f))

private fun DrawScope.roundBox(x: Float, y: Float, w: Float, h: Float, r: Float) {
    val o = Offset(x, y)
    drawRoundRect(cup(o, w, h), o, Size(w, h), CornerRadius(r))
    drawRoundRect(Edge, o, Size(w, h), CornerRadius(r), style = Stroke(2f))
}

fun DrawScope.drawHeadphones() = designSpace {
    shadowUnder()
    val band = Path().apply { moveTo(70f, 128f); lineTo(70f, 100f); cubicTo(70f, 34f, 250f, 34f, 250f, 100f); lineTo(250f, 128f) }
    drawPath(band, Brush.horizontalGradient(listOf(Color(0xFF3B3632), Color(0xFF7A6A5C), Color(0xFF2C2825)), 70f, 250f),
        style = Stroke(16f, cap = StrokeCap.Round))
    val inner = Path().apply { moveTo(78f, 104f); cubicTo(82f, 52f, 238f, 52f, 242f, 104f) }
    drawPath(inner, Color(0x99B58660), style = Stroke(1.5f))
    roundBox(44f, 104f, 56f, 88f, 26f)
    roundBox(220f, 104f, 56f, 88f, 26f)
    drawRoundRect(Color(0xFF1B1918), Offset(88f, 118f), Size(10f, 60f), CornerRadius(5f))
    drawRoundRect(Color(0xFF1B1918), Offset(222f, 118f), Size(10f, 60f), CornerRadius(5f))
    drawCircle(Copper, 3f, Offset(72f, 134f))
}

fun DrawScope.drawEarbuds() = designSpace {
    shadowUnder()
    listOf(Triple(115f, -18f, 106f), Triple(205f, 18f, 214f)).forEach { (cx, angle, dot) ->
        rotate(angle, pivot = Offset(cx, 90f)) {
            val o = Offset(cx - 34f, 50f)
            drawOval(cup(o, 68f, 60f), o, Size(68f, 60f))
            drawOval(Edge, o, Size(68f, 60f), style = Stroke(2f))
            roundBox(cx - 12f, 96f, 24f, 70f, 12f)
            drawCircle(Dark, 9f, Offset(dot, 72f))
            drawCircle(Color(0xFF594536), 9f, Offset(dot, 72f), style = Stroke(1f))
        }
    }
}

fun DrawScope.drawDac() = designSpace {
    shadowUnder()
    roundBox(80f, 70f, 160f, 96f, 18f)
    drawCircle(Color(0xFF1B1918), 24f, Offset(200f, 118f))
    drawCircle(Copper, 24f, Offset(200f, 118f), style = Stroke(2f))
    drawCircle(Copper, 6f, Offset(200f, 118f))
    drawCircle(Dark, 6f, Offset(112f, 118f))
    drawLine(Edge, Offset(160f, 166f), Offset(160f, 184f), 5f, cap = StrokeCap.Round)
}

fun DrawScope.drawSpeaker() = designSpace {
    shadowUnder()
    roundBox(112f, 26f, 96f, 164f, 22f)
    drawCircle(Color(0xFF1B1918), 34f, Offset(160f, 132f))
    drawCircle(Copper, 34f, Offset(160f, 132f), style = Stroke(2f))
    drawCircle(Color(0xFF2A2725), 12f, Offset(160f, 132f))
    drawCircle(Color(0xFF1B1918), 12f, Offset(160f, 66f))
}

/** Device artwork for the active output. kind: head, bud, dac, spk. */
@Composable
fun DeviceArt(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        when (kind) {
            "head" -> drawHeadphones()
            "bud" -> drawEarbuds()
            "dac" -> drawDac()
            else -> drawSpeaker()
        }
    }
}

/** Listener in a room with four speakers; distance follows room size, brightness follows reverb on/off. */
@Composable
fun RoomScene(size: Float, on: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(210.dp)) {
        val s = min(this.size.width / 320f, this.size.height / 240f)
        translate((this.size.width - 320f * s) / 2f, (this.size.height - 240f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                val c = Offset(160f, 125f)
                drawOval(Brush.radialGradient(listOf(Color(0x38EDA86E), Color(0x00EDA86E)), c, 150f), Offset(10f, 25f), Size(300f, 200f))
                listOf(135f, 98f, 60f).forEach { w ->
                    drawOval(Color(0xFF765439), Offset(c.x - w, c.y - w * 0.65f), Size(w * 2, w * 1.3f), style = Stroke(1f))
                }
                val r = 52f + size * 0.5f
                listOf(-135f, -45f, 150f, 30f).forEach { a ->
                    val rad = Math.toRadians(a.toDouble())
                    val p = Offset(c.x + (cos(rad) * r * 1.35).toFloat(), c.y + (sin(rad) * r * 0.9).toFloat())
                    val alpha = if (on) 1f else 0.35f
                    drawLine(Copper.copy(alpha = 0.45f * alpha), c, p, 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
                    val o = Offset(p.x - 14f, p.y - 19f)
                    drawRoundRect(cup(o, 28f, 38f), o, Size(28f, 38f), CornerRadius(6f), alpha = alpha)
                    drawRoundRect(Edge, o, Size(28f, 38f), CornerRadius(6f), alpha = alpha, style = Stroke(1f))
                    drawCircle(Dark, 7f, Offset(p.x, p.y + 4f), alpha = alpha)
                    drawCircle(Copper, 7f, Offset(p.x, p.y + 4f), alpha = alpha, style = Stroke(1f))
                }
                val head = Offset(160f, 118f)
                drawCircle(cup(Offset(140f, 98f), 40f, 40f), 20f, head)
                drawCircle(Edge, 20f, head, style = Stroke(1.5f))
                drawArc(Copper, 180f, 180f, false, Offset(140f, 96f), Size(40f, 40f), style = Stroke(3f, cap = StrokeCap.Round))
                drawRoundRect(Copper, Offset(135f, 111f), Size(7f, 13f), CornerRadius(3f))
                drawRoundRect(Copper, Offset(178f, 111f), Size(7f, 13f), CornerRadius(3f))
            }
        }
    }
}
