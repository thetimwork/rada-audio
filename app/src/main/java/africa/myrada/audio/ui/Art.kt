package africa.myrada.audio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** Colours the flat device drawings use; read from the palette in force (the hero is a contrast card). */
private class ArtInk(val a1: Color, val a2: Color, val a3: Color, val sh: Color, val muted: Color, val bg: Color)

/** Draw in a 320 × 210 design space, centred and scaled to the canvas. */
private inline fun DrawScope.design(w: Float, h: Float, block: DrawScope.() -> Unit) {
    val s = min(size.width / w, size.height / h)
    translate((size.width - w * s) / 2, (size.height - h * s) / 2) { scale(s, s, Offset.Zero) { block() } }
}

private fun DrawScope.rr(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r))

private fun DrawScope.oval(c: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun DrawScope.headphones(k: ArtInk) {
    oval(k.sh, 160f, 198f, 104f, 5f)
    val band = Path().apply { moveTo(78f, 124f); lineTo(78f, 98f); cubicTo(78f, 34f, 242f, 34f, 242f, 98f); lineTo(242f, 124f) }
    drawPath(band, k.a1, style = Stroke(14f, cap = StrokeCap.Round))
    val inner = Path().apply { moveTo(96f, 92f); cubicTo(100f, 54f, 220f, 54f, 224f, 92f) }
    drawPath(inner, k.muted.copy(alpha = k.muted.alpha * 0.5f), style = Stroke(2f, cap = StrokeCap.Round))
    rr(k.a2, 71f, 106f, 14f, 26f, 7f); rr(k.a2, 235f, 106f, 14f, 26f, 7f)
    rr(k.a1, 44f, 120f, 68f, 80f, 32f); rr(k.a2, 57f, 131f, 42f, 58f, 21f)
    rr(k.a1, 208f, 120f, 68f, 80f, 32f); rr(k.a3, 221f, 131f, 42f, 58f, 21f)
    drawCircle(Red, 4f, Offset(242f, 180f))
}

private fun DrawScope.earbuds(k: ArtInk) {
    oval(k.sh, 160f, 198f, 104f, 5f)
    rotate(-14f, Offset(120f, 110f)) {
        rr(k.a1, 107f, 94f, 26f, 84f, 13f); oval(k.a1, 120f, 80f, 35f, 32f); oval(k.a2, 97f, 76f, 14f, 12f)
        drawCircle(Red, 4f, Offset(120f, 168f))
    }
    rotate(14f, Offset(205f, 110f)) {
        rr(k.a2, 193f, 102f, 24f, 76f, 12f); oval(k.a2, 205f, 88f, 32f, 30f); oval(Grey, 226f, 84f, 12f, 10f)
    }
}

private fun DrawScope.dac(k: ArtInk) {
    oval(k.sh, 160f, 198f, 104f, 5f)
    val cable = Path().apply { moveTo(84f, 120f); cubicTo(58f, 120f, 52f, 146f, 30f, 156f) }
    drawPath(cable, k.a2, style = Stroke(6f, cap = StrokeCap.Round))
    rr(k.a1, 84f, 70f, 156f, 96f, 28f)
    drawCircle(k.bg, 26f, Offset(198f, 118f)); drawCircle(k.a1, 4f, Offset(206f, 104f))
    drawCircle(k.bg, 10f, Offset(124f, 118f))
    rr(k.a2, 108f, 142f, 34f, 8f, 4f)
    drawCircle(Red, 4f, Offset(112f, 94f))
}

private fun DrawScope.phone(k: ArtInk) {
    oval(k.sh, 160f, 198f, 104f, 5f)
    rr(k.a1, 114f, 22f, 92f, 172f, 26f); rr(k.a3, 123f, 32f, 74f, 152f, 19f); rr(Grey, 146f, 40f, 28f, 4f, 2f)
    val ring = k.muted.copy(alpha = k.muted.alpha * 0.6f)
    drawCircle(ring, 30f, Offset(160f, 112f), style = Stroke(1.5f)); drawCircle(ring, 16f, Offset(160f, 112f), style = Stroke(1.5f))
    drawCircle(Red, 5f, Offset(160f, 112f))
    drawArc(k.muted, -41.8f, 83.6f, false, Offset(199.6f - 30f, 112f - 30f), Size(60f, 60f), style = Stroke(3f, cap = StrokeCap.Round))
    drawArc(k.muted, -42.8f, 85.6f, false, Offset(199.3f - 50f, 112f - 50f), Size(100f, 100f), style = Stroke(3f, cap = StrokeCap.Round))
}

@Composable
fun DeviceArt(kind: String, modifier: Modifier = Modifier) {
    val k = ArtInk(Pal.art1, Pal.art2, Pal.art3, Pal.artSh, Pal.muted, Pal.bg)
    Canvas(modifier.fillMaxWidth().height(196.dp)) {
        design(320f, 210f) {
            when (kind) { "bud" -> earbuds(k); "dac" -> dac(k); "phone" -> phone(k); else -> headphones(k) }
        }
    }
}

/**
 * The room: Rada's vinyl radar. The grooves are range rings, the four speakers are blips that move
 * out as the room grows, the red sweep turns clockwise and the tonearm rests. Always on warm white.
 */
@Composable
fun VinylRadar(roomSize: Float, on: Boolean, room: String, modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val tm = rememberTextMeasurer()
    val black = Color(0xFF010A10)
    val label = TextStyle(fontFamily = Figtree, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0x8CFFFBF2), letterSpacing = 1.sp)
    val word = TextStyle(fontFamily = Figtree, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-0.4).sp)
    val small = TextStyle(fontFamily = Figtree, fontSize = 5.6.sp, fontWeight = FontWeight.SemiBold, color = Color(0xCCFFFFFF), letterSpacing = 1.4.sp)
    Canvas(modifier.fillMaxWidth().height(232.dp).semantics { contentDescription = "$room room, size ${roomSize.toInt()} percent" }) {
        val s = min(this.size.width / 320f, this.size.height / 240f)
        val ox = (this.size.width - 320f * s) / 2; val oy = (this.size.height - 240f * s) / 2
        fun at(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val ringR = 38f + roomSize * 0.62f
        val d = ringR * 0.707f
        val spk = listOf(Triple("FRONT L", -1, -1), Triple("FRONT R", 1, -1), Triple("REAR L", -1, 1), Triple("REAR R", 1, 1))
        translate(ox, oy) {
            scale(s, s, Offset.Zero) {
                val c = Offset(160f, 120f)
                drawCircle(black, 112f, c)
                var r = 50f
                while (r <= 104f) { drawCircle(Color(0x0BFFFFFF), r, c, style = Stroke(1f)); r += 9f }
                drawCircle(Color(0x1FFFFFFF), 108f, c, style = Stroke(1f)); drawCircle(Color(0x1FFFFFFF), ringR, c, style = Stroke(1f))
                drawLine(Color(0x0DFFFFFF), Offset(48f, 120f), Offset(272f, 120f), 1f)
                drawLine(Color(0x0DFFFFFF), Offset(160f, 8f), Offset(160f, 232f), 1f)
                rotate(sweep, c) {
                    val a = if (on) 1f else 0.35f
                    val wedge = Path().apply { moveTo(160f, 120f); lineTo(160f, 8f); arcTo(androidx.compose.ui.geometry.Rect(c, 112f), -90f, 45f, false); close() }
                    drawPath(wedge, Red.copy(alpha = 0.26f * a))
                    drawLine(Red.copy(alpha = a), c, Offset(160f, 8f), 1.6f)
                }
                spk.forEach { (_, x, y) -> drawCircle(if (on) Red else Grey, 4.5f, Offset(160f + x * d, 120f + y * d)) }
                // tonearm, resting
                drawLine(Color(0xFF5E5A60), Offset(304f, 26f), Offset(250f, 50f), 3.4f, StrokeCap.Round)
                drawCircle(Color(0xFF5E5A60), 9f, Offset(305f, 25f))
                rotate(-24f, Offset(247f, 50f)) { drawRoundRect(Grey, Offset(238f, 45f), Size(18f, 9f), CornerRadius(2f)) }
                // centre label
                drawCircle(Red, 34f, c)
                drawCircle(Color(0x38FFFFFF), 29f, c, style = Stroke(1f))
                drawCircle(Color(0xFFFFFBF2), 2.6f, c)
            }
        }
        // text in screen space so it stays at its real size
        spk.forEach { (l, x, y) ->
            val m = tm.measure(l, label)
            val p = at(160f + x * d + (if (x < 0) -9f else 9f), 120f + y * d)
            drawText(m, topLeft = Offset(if (x < 0) p.x - m.size.width else p.x, p.y - m.size.height / 2f))
        }
        val wm = tm.measure("rada", word)
        val wc = at(160f, 115f)
        drawText(wm, topLeft = Offset(wc.x - wm.size.width / 2f, wc.y - wm.size.height / 2f))
        val rm = tm.measure(room.uppercase(), small)
        val rc = at(160f, 132f)
        drawText(rm, topLeft = Offset(rc.x - rm.size.width / 2f, rc.y - rm.size.height / 2f))
    }
}
