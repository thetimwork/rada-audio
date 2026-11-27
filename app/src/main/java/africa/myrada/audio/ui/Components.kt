package africa.myrada.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.roundToInt

val CopperBrush = Brush.linearGradient(listOf(Color(0xFFF3BD88), Color(0xFFD48B51)))
val PanelBrush = Brush.linearGradient(listOf(Color(0xFF302C28), Color(0xFF191817)))
val AdvBrush = Brush.linearGradient(listOf(Color(0xFF3A2C22), Color(0xFF191817)))

// ---------- containers and labels ------------------------------------------------------------

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    brush: Brush = PanelBrush,
    border: Color = RadaColors.Line,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = RadaColors.Text, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
fun Heading(eyebrow: String, title: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(eyebrow.uppercase(), color = RadaColors.Copper, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = RadaColors.Text, letterSpacing = (-1).sp)
    }
}

@Composable
fun Note(text: String, color: Color = RadaColors.Muted, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier.padding(top = 10.dp))
}

enum class TagKind { Normal, Limit, Advanced }

@Composable
fun Tag(text: String, kind: TagKind = TagKind.Normal) {
    val (border, color, bg) = when (kind) {
        TagKind.Normal -> Triple(Color(0xFF856143), RadaColors.Copper, Color.Transparent)
        TagKind.Limit -> Triple(Color(0xFF6B5A2E), RadaColors.Amber, Color.Transparent)
        TagKind.Advanced -> Triple(Color(0xFF856143), RadaColors.Copper, Color(0x1AEDA86E))
    }
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = RadaColors.Copper, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Text(label.uppercase(), color = RadaColors.Muted, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

// ---------- controls -------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(items: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { name ->
            val on = name == selected
            Box(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) CopperBrush else Brush.linearGradient(listOf(RadaColors.Panel2, RadaColors.Panel2)))
                    .border(1.dp, if (on) Color(0xFFE9AB76) else RadaColors.Line, RoundedCornerShape(50))
                    .clickable { onPick(name) }
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, fontSize = 13.sp, color = if (on) RadaColors.OnCopper else RadaColors.Text,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun Segmented(items: List<String>, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E0D0C))
            .border(1.dp, Color(0xFF3A302A), RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEach { name ->
            val on = name == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) CopperBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onPick(name) },
                contentAlignment = Alignment.Center,
            ) {
                Text(name, fontSize = 13.sp, color = if (on) RadaColors.OnCopper else RadaColors.Muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun CopperSwitch(checked: Boolean, enabled: Boolean = true, label: String, onToggle: () -> Unit) {
    val x by rememberUpdatedState(if (checked) 18.dp else 0.dp)
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked && enabled) RadaColors.Copper else Color(0xFF393430))
            .alpha(if (enabled) 1f else 0.6f)
            .clickable { onToggle() }
            .semantics { contentDescription = label; stateDescription = if (checked) "On" else "Off" }
            .padding(4.dp)
    ) {
        Box(Modifier.padding(start = x).size(20.dp).clip(CircleShape)
            .background(if (checked && enabled) Color(0xFF2A211A) else Color(0xFFAAA29A)))
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String? = null,
    tagKind: TagKind = TagKind.Limit,
    locked: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (locked) 0.55f else 1f).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RadaColors.Text)
                if (tag != null) { Spacer(Modifier.width(8.dp)); Tag(tag, tagKind) }
            }
            Text(if (locked) "Needs Advanced mode (Shizuku)" else subtitle, color = RadaColors.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        CopperSwitch(checked = checked && !locked, enabled = !locked, label = title, onToggle = onToggle)
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = RadaColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(display, color = RadaColors.Copper, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFF3BD88), activeTrackColor = RadaColors.Copper,
                inactiveTrackColor = Color(0xFF3A322B), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
    }
}

@Composable
fun CtaButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(15.dp)).background(CopperBrush).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = RadaColors.OnCopper, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp)).background(RadaColors.Panel2)
            .border(1.dp, RadaColors.Line, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = RadaColors.Copper, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

/** Hold to hear the original sound; release to hear RADA again. */
@Composable
fun CompareButton(held: Boolean, onHold: (Boolean) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (held) Color(0xFF3A2A1C) else Color.Transparent)
            .border(BorderStroke(1.dp, if (held) RadaColors.Amber else Color(0xFF856143)), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { onHold(true); tryAwaitRelease(); onHold(false) })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (held) "Original sound (level-matched)" else "Hold to compare with original",
            color = if (held) RadaColors.Amber else RadaColors.Text, fontSize = 14.sp)
    }
}

/** Copper rotary dial: drag up or down. */
@Composable
fun Dial(label: String, value: Float, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 8.dp, bottom = 6.dp)
                .size(86.dp)
                .semantics { contentDescription = label; stateDescription = "${value.roundToInt()}" }
                .pointerInput(Unit) {
                    detectDragGestures { _, drag -> change((current - drag.y * 0.35f).coerceIn(0f, 100f)) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Color(0xFF494038), 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(RadaColors.Copper, 135f, 270f * value / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Box(
                Modifier
                    .size(74.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF57504A), Color(0xFF22201E)), center = Offset(70f, 50f), radius = 160f))
                    .border(1.dp, Color(0xFF6B5B4D), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${value.roundToInt()}", color = RadaColors.Copper, fontSize = 21.sp) }
        }
        Text(label, color = RadaColors.Muted, fontSize = 12.sp)
    }
}

@Composable
fun DialRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, content = content)
}

/** EQ response curve drawn from band gains. */
@Composable
fun EqCurve(gains: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        val mid = size.height / 2
        val scaleY = size.height / 2 / 13f
        listOf(-0.66f, -0.33f, 0.33f, 0.66f).forEach { f ->
            drawLine(Color(0xFF3D3229), Offset(pad, mid + f * mid), Offset(size.width - pad, mid + f * mid), 1f)
        }
        drawLine(Color(0xFF856143), Offset(pad, mid), Offset(size.width - pad, mid), 1.5f)
        val pts = gains.mapIndexed { i, g -> Offset(pad + i * w / (gains.size - 1), mid - g * scaleY) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val m = (pts[i - 1].x + pts[i].x) / 2
                cubicTo(m, pts[i - 1].y, m, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        val area = Path().apply { addPath(line); lineTo(pts.last().x, mid); lineTo(pts[0].x, mid); close() }
        drawPath(area, Brush.verticalGradient(listOf(Color(0x59EDA86E), Color(0x00EDA86E))))
        drawPath(line, RadaColors.Copper, style = Stroke(2.5.dp.toPx()))
        pts.forEach { drawCircle(Color(0xFFF1B783), 3.5.dp.toPx(), it) }
    }
}

/** Ten vertical faders with copper caps; drag or tap a track. */
@Composable
fun Faders(gains: List<Float>, labels: List<String>, trackHeight: Dp = 150.dp, onChange: (Int, Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        gains.forEachIndexed { i, g ->
            val change by rememberUpdatedState { v: Float -> onChange(i, v) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text((if (g > 0) "+" else "") + g.roundToInt(), color = RadaColors.Copper, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier
                        .width(28.dp)
                        .height(trackHeight)
                        .semantics { contentDescription = "${labels[i]} hertz"; stateDescription = "${g.roundToInt()} dB" }
                        .pointerInput(Unit) {
                            fun toGain(y: Float) = (12f - y / size.height * 24f).roundToInt().toFloat().coerceIn(-12f, 12f)
                            detectTapGestures { change(toGain(it.y)) }
                        }
                        .pointerInput(Unit) {
                            fun toGain(y: Float) = (12f - y / size.height * 24f).roundToInt().toFloat().coerceIn(-12f, 12f)
                            detectDragGestures(onDragStart = { change(toGain(it.y)) }) { c, _ -> change(toGain(c.position.y)) }
                        }
                ) {
                    val cx = size.width / 2
                    val mid = size.height / 2
                    val y = mid - g / 12f * (mid - 6.dp.toPx())
                    drawRoundRect(Color(0xFF3A322B), Offset(cx - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2f))
                    drawLine(RadaColors.Line, Offset(6.dp.toPx(), mid), Offset(size.width - 6.dp.toPx(), mid), 2f)
                    drawRoundRect(RadaColors.Copper, Offset(cx - 1.5.dp.toPx(), minOf(y, mid)), Size(3.dp.toPx(), kotlin.math.abs(mid - y)), CornerRadius(2f))
                    val capW = 22.dp.toPx(); val capH = 12.dp.toPx()
                    drawRoundRect(Color(0x99000000), Offset(cx - capW / 2, y - capH / 2 + 2.dp.toPx()), Size(capW, capH), CornerRadius(4.dp.toPx()))
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3BD88), Color(0xFFC47D47)), startY = y - capH / 2, endY = y + capH / 2),
                        Offset(cx - capW / 2, y - capH / 2), Size(capW, capH), CornerRadius(4.dp.toPx()))
                }
                Spacer(Modifier.height(8.dp))
                Text(labels[i], color = RadaColors.Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun rememberFloat(initial: Float) = remember { mutableFloatStateOf(initial) }
