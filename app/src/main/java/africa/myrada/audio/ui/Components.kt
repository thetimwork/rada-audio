package africa.myrada.audio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- containers and labels ------------------------------------------------------------

/** A card. contrast = drawn in the opposite colour set (black in light mode, warm white in dark). */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    contrast: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        val shape = RoundedCornerShape(CardRadius)
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (contrast) Pal.bg else Pal.card)
                .border(1.dp, if (contrast) Pal.bg else Pal.line, shape)
                .padding(20.dp)
        ) {
            if (title != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Pal.black, letterSpacing = (-0.35).sp, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            content()
        }
    }
    if (contrast) Inverse(body) else body()
}

@Composable
fun Heading(eyebrow: String, title: String, big: Boolean = false) {
    Column(Modifier.padding(top = 6.dp, bottom = 18.dp)) {
        Eyebrow(eyebrow, Red)
        Text(title, fontSize = if (big) 42.sp else 34.sp, fontWeight = FontWeight.ExtraBold, color = Pal.black,
            letterSpacing = if (big) (-1.7).sp else (-1.36).sp, lineHeight = if (big) 44.sp else 36.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Pal.muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = color, fontSize = 10.5.sp, letterSpacing = 2.1.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun Note(text: String, color: Color = Pal.muted2, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 12.5.sp, lineHeight = 19.sp, modifier = modifier.padding(top = 12.dp))
}

enum class TagKind { Normal, Limit, Advanced, Live }

@Composable
fun Tag(text: String, kind: TagKind = TagKind.Normal) {
    val shape = RoundedCornerShape(50)
    val (bg, border, color) = when (kind) {
        TagKind.Normal -> Triple(Color.Transparent, Pal.line, Pal.muted2)
        TagKind.Limit -> Triple(Pal.powder2, Color.Transparent, Pal.muted2)
        TagKind.Advanced -> Triple(Pal.pri, Pal.pri, Pal.onPri)
        TagKind.Live -> Triple(Color.Transparent, Red.copy(alpha = 0.3f), Red)
    }
    Text(
        text.uppercase(), color = color, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp, maxLines = 1,
        modifier = Modifier.clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(TileRadius)).background(Pal.powder2).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Pal.black, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, letterSpacing = (-0.3).sp)
        Text(label.uppercase(), color = Pal.muted, fontSize = 9.5.sp, letterSpacing = 1.5.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- controls -------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(items: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { name ->
            val on = name == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.height(40.dp).clip(shape).background(if (on) Pal.pri else Pal.card)
                    .border(1.dp, if (on) Pal.pri else Pal.line, shape).clickable { onPick(name) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(name, fontSize = 13.5.sp, color = if (on) Pal.onPri else Pal.ink, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

@Composable
fun Segmented(items: List<String>, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Pal.powder2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { name ->
            val on = name == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50)).background(if (on) Pal.pri else Color.Transparent)
                    .clickable { onPick(name) }.padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(name, fontSize = 13.5.sp, color = if (on) Pal.onPri else Pal.muted2, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

@Composable
fun RadaSwitch(checked: Boolean, enabled: Boolean = true, label: String, onToggle: () -> Unit) {
    val on = checked && enabled
    Box(
        Modifier.size(width = 50.dp, height = 30.dp).clip(RoundedCornerShape(50))
            .background(if (on) Pal.pri else Pal.powder)
            .clickable { onToggle() }
            .semantics { contentDescription = label; stateDescription = if (checked) "On" else "Off" }
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(if (on) Pal.onPri else Pal.card)) }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String? = null,
    tagKind: TagKind = TagKind.Limit,
    locked: Boolean = false,
    lockedText: String = "Needs Advanced mode (Shizuku)",
    onToggle: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().alpha(if (locked) 0.6f else 1f).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Pal.black)
                if (tag != null) { Spacer(Modifier.width(8.dp)); Tag(tag, tagKind) }
            }
            Text(if (locked) lockedText else subtitle, color = Pal.muted2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(14.dp))
        RadaSwitch(checked = checked && !locked, enabled = !locked, label = title, onToggle = onToggle)
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.line2))

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(display, color = Pal.black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Pal.pri, activeTrackColor = Pal.pri, inactiveTrackColor = Pal.powder2,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
    }
}

@Composable
fun CtaButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(Pal.pri).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Pal.onPri, fontWeight = FontWeight.Bold, fontSize = 14.5.sp) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).background(Pal.card).border(1.dp, Pal.line, shape).clickable { onClick() }.padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Pal.black, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1) }
}

@Composable
fun RedDot(size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(Red))

/** Hold to hear the original sound; release to hear Rada again. */
@Composable
fun CompareButton(held: Boolean, onHold: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (held) Color.Transparent else Pal.pri)
            .border(1.5.dp, if (held) Pal.powder else Pal.pri, shape)
            .pointerInput(Unit) { detectTapGestures(onPress = { onHold(true); tryAwaitRelease(); onHold(false) }) },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (held) Pal.powder else Red))
        Spacer(Modifier.width(10.dp))
        Text(if (held) "Original sound (level-matched)" else "Hold to compare with original",
            color = if (held) Pal.black else Pal.onPri, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Rotary dial: drag up or down. */
@Composable
fun Dial(label: String, value: Float, size: Dp = 88.dp, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val track = Pal.powder
    val arcColor = Pal.pri
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.padding(top = 6.dp, bottom = 8.dp).size(size)
                .semantics { contentDescription = label; stateDescription = "${value.roundToInt()}" }
                .pointerInput(Unit) { detectDragGestures { _, drag -> change((current - drag.y * 0.35f).coerceIn(0f, 100f)) } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arc = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(arcColor, 135f, 270f * current / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Box(
                Modifier.size(size - 14.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Pal.card),
                contentAlignment = Alignment.Center,
            ) { Text("${value.roundToInt()}", color = Pal.black, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp) }
        }
        Text(label.uppercase(), color = Pal.muted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DialRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, content = content)
}

// ---------- EQ: response curve, chart and fader bank -----------------------------------------

private val DB = listOf(12, 6, 0, -6, -12)

/** y of a gain inside a plot of height h with padding pad: ±12 dB fills the space between the pads. */
private fun gainY(g: Float, h: Float, pad: Float) = h / 2 - g * ((h / 2 - pad) / 12f)

/** Smooth response through the band centres (Catmull-Rom), flat beyond the end bands. */
private fun responsePath(gains: List<Float>, w: Float, h: Float, pad: Float): Path {
    val n = gains.size
    val pts = buildList {
        add(Offset(0f, gainY(gains[0], h, pad)))
        gains.forEachIndexed { i, g -> add(Offset(w * (i + 0.5f) / n, gainY(g, h, pad))) }
        add(Offset(w, gainY(gains[n - 1], h, pad)))
    }
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[max(0, i - 1)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[min(pts.size - 1, i + 2)]
            cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
        }
    }
}

private fun DrawScope.dbGrid(h: Float, pad: Float, line: Color, zero: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    DB.forEach { v ->
        val y = gainY(v.toFloat(), h, pad)
        if (v == 0) drawLine(zero, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
        else drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
    }
}

@Composable
private fun DbScale(height: Dp, pad: Dp, top: Dp) {
    Box(Modifier.width(26.dp).padding(top = top).height(height)) {
        DB.forEach { v ->
            val y = height / 2 - pad * 0 - (height / 2 - pad) * (v / 12f)
            Text((if (v > 0) "+" else "") + v, fontSize = 9.5.sp, color = Pal.muted, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = (y - 6.dp).coerceAtLeast(0.dp), end = 2.dp))
        }
    }
}

private fun fmtGain(g: Float) = (if (g > 0) "+" else "") + g.roundToInt()

/** Home chart: dB scale, fine grid, smooth response with a light fill, a marker at each band. */
@Composable
fun EqChart(gains: List<Float>, labels: List<String>, height: Dp = 170.dp) {
    val pad = 18.dp
    val line = Pal.line; val zero = Pal.powder; val ink = Pal.black; val card = Pal.card
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DbScale(height, pad, 0.dp)
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val h = size.height; val w = size.width; val pd = pad.toPx()
                dbGrid(h, pd, line, zero)
                val path = responsePath(gains, w, h, pd)
                val area = Path().apply { addPath(path); lineTo(w, h / 2); lineTo(0f, h / 2); close() }
                drawPath(area, ink.copy(alpha = 0.055f))
                drawPath(path, ink, style = Stroke(2.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                gains.forEachIndexed { i, g ->
                    val c = Offset(w * (i + 0.5f) / gains.size, gainY(g, h, pd))
                    if (g > 0) drawCircle(Red, 4.5.dp.toPx(), c)
                    else { drawCircle(card, 4.5.dp.toPx(), c); drawCircle(ink, 3.5.dp.toPx(), c, style = Stroke(2.dp.toPx())) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                gains.forEachIndexed { i, g ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtGain(g), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (g > 0) Red else Pal.muted2, maxLines = 1)
                        Text(labels[i].uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Pal.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * EQ tab: ten tall faders with wide grip caps, set-in tracks and a dB scale. The response curve
 * runs behind the faders and through the centre of every cap. Drag or tap a track.
 */
@Composable
fun FaderBank(gains: List<Float>, labels: List<String>, trackHeight: Dp = 236.dp, onChange: (Int, Float) -> Unit) {
    val pad = 18.dp
    val line = Pal.line; val zero = Pal.powder; val ink = Pal.black
    val groove = Pal.powder2; val cap = Pal.pri; val grip = Pal.onPri; val ring = Pal.card
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DbScale(trackHeight, pad, 26.dp)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(26.dp)) {
                gains.forEach { g ->
                    Text(fmtGain(g), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (g > 0) Red else Pal.muted2,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
            Box(Modifier.fillMaxWidth().height(trackHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val h = size.height; val w = size.width; val pd = pad.toPx()
                    dbGrid(h, pd, line, zero)
                    val path = responsePath(gains, w, h, pd)
                    val area = Path().apply { addPath(path); lineTo(w, h / 2); lineTo(0f, h / 2); close() }
                    drawPath(area, ink.copy(alpha = 0.05f))
                    drawPath(path, ink.copy(alpha = 0.28f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Row(Modifier.fillMaxSize()) {
                    gains.forEachIndexed { i, g ->
                        val change by rememberUpdatedState { v: Float -> onChange(i, v) }
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .semantics { contentDescription = "${labels[i]} hertz"; stateDescription = "${g.roundToInt()} dB" }
                                .pointerInput(Unit) {
                                    fun toGain(y: Float): Float {
                                        val pd = pad.toPx()
                                        return (12f - (y - pd) / (size.height - 2 * pd) * 24f).roundToInt().toFloat().coerceIn(-12f, 12f)
                                    }
                                    detectTapGestures { change(toGain(it.y)) }
                                }
                                .pointerInput(Unit) {
                                    fun toGain(y: Float): Float {
                                        val pd = pad.toPx()
                                        return (12f - (y - pd) / (size.height - 2 * pd) * 24f).roundToInt().toFloat().coerceIn(-12f, 12f)
                                    }
                                    detectDragGestures(onDragStart = { change(toGain(it.y)) }) { c, _ -> change(toGain(c.position.y)) }
                                }
                                .drawBehind {
                                    val cx = size.width / 2; val h = size.height; val pd = pad.toPx()
                                    val y = gainY(g, h, pd); val mid = h / 2
                                    // set-in groove
                                    val gw = 6.dp.toPx()
                                    drawRoundRect(groove, Offset(cx - gw / 2, 6.dp.toPx()), Size(gw, h - 12.dp.toPx()), CornerRadius(gw / 2))
                                    // travel from 0 dB to the cap
                                    drawRect(ink.copy(alpha = 0.5f), Offset(cx - 1.dp.toPx(), min(y, mid)), Size(2.dp.toPx(), abs(mid - y)))
                                    // the cap: wide, flat, with grip lines
                                    val cw = min(44.dp.toPx(), size.width * 0.78f); val ch = 22.dp.toPx(); val r = 7.dp.toPx()
                                    val ringW = 3.dp.toPx()
                                    drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(cx - cw / 2, y - ch / 2 + 4.dp.toPx()), Size(cw, ch), CornerRadius(r))
                                    drawRoundRect(ring, Offset(cx - cw / 2 - ringW, y - ch / 2 - ringW), Size(cw + ringW * 2, ch + ringW * 2), CornerRadius(r + ringW))
                                    drawRoundRect(cap, Offset(cx - cw / 2, y - ch / 2), Size(cw, ch), CornerRadius(r))
                                    val gl = cw * 0.4f
                                    drawLine(grip.copy(alpha = 0.3f), Offset(cx - gl / 2, y - 3.5.dp.toPx()), Offset(cx + gl / 2, y - 3.5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                                    drawLine(grip.copy(alpha = 0.55f), Offset(cx - gl / 2, y), Offset(cx + gl / 2, y), 1.5.dp.toPx(), StrokeCap.Round)
                                    drawLine(grip.copy(alpha = 0.3f), Offset(cx - gl / 2, y + 3.5.dp.toPx()), Offset(cx + gl / 2, y + 3.5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                                }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                labels.forEach { l ->
                    Text(l.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Pal.black, letterSpacing = 0.2.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
        }
    }
}
