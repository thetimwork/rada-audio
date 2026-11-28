package africa.myrada.audio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private const val TURN_FRAMES = 31          // 90 degrees, 3 degrees apart
private const val TURN_W = 540
private const val TURN_H = 314
private const val TURN_COLS = 8
private const val TURN_MS = 1300f

// ---- feel: the "Rada" preset from the design page ----
private const val LEAN = 13f                // degrees of lean at full tilt
private const val SPRING = 160f             // spring stiffness
private const val BOUNCE = 0.5f             // damping ratio: lower means more overshoot
private const val FULL_TILT = 9f            // a 9 degree tilt of the phone gives the full lean
private const val DEAD_ZONE = 0.25f         // degrees ignored as sensor jitter
private const val SMOOTH_S = 0.07f          // sensor smoothing time, seconds
private const val RECENTRE_S = 6f           // how slowly the resting position follows your hand

/** Devices whose quarter-turn has already played since the app started: it plays once per connection. */
private val played = mutableSetOf<String>()

private fun load(ctx: Context, path: String): ImageBitmap? = runCatching {
    ctx.assets.open(path).use { input ->
        val opts = BitmapFactory.Options().apply { if (Build.VERSION.SDK_INT >= 26) inPreferredConfig = Bitmap.Config.HARDWARE }
        BitmapFactory.decodeStream(input, null, opts)!!.asImageBitmap()
    }
}.getOrNull()

/** One damped spring pulled towards [t]. */
private class Spring {
    var p = 0f; var v = 0f; var t = 0f
    fun step(h: Float, k: Float, z: Float) { val c = 2f * z * sqrt(k); v += (-k * (p - t) - c * v) * h; p += v * h }
}

/** All the motion state. Only the drawing layers read it, so the card itself never recomposes. */
private class Motion {
    val x = Spring(); val y = Spring(); val squash = Spring(); val hop = Spring()
    var time by mutableFloatStateOf(0f)
}

/**
 * The device's own stage at the top of the device card: a still render over a soft floor shadow,
 * with the type and connection labels on top. It behaves like a small object on a spring: it leans
 * with the phone (smoothed, so no jitter), overshoots a touch and settles back straight, floats
 * gently when still, hops when tapped, and makes one quarter-turn when the device connects.
 * With the phone's animations switched off it is a plain still.
 */
@Composable
fun DeviceStage(kind: String, name: String, typeLabel: String, modeLabel: String, id: String, modifier: Modifier = Modifier, height: Dp = 262.dp) {
    val ctx = LocalContext.current
    val still by produceState<ImageBitmap?>(null, kind) { value = withContext(Dispatchers.IO) { load(ctx, "devices/${kind}_still.webp") } }
    val calm = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val m = remember { Motion() }
    val scope = rememberCoroutineScope()

    // ---- quarter-turn on connect, landing with a small wobble ----
    var frame by remember(id) { mutableIntStateOf(if (calm || id in played) -1 else 0) }
    val sheet by produceState<ImageBitmap?>(null, kind, frame >= 0) {
        if (frame >= 0) value = withContext(Dispatchers.IO) { load(ctx, "devices/${kind}_turn.webp") }
    }
    LaunchedEffect(id, sheet) {
        if (frame < 0 || sheet == null) return@LaunchedEffect
        played += id
        val t0 = withFrameNanos { it }
        while (true) {
            val p = withFrameNanos { (it - t0) / 1e6f / TURN_MS }.coerceAtMost(1f)
            frame = ((1f - (1f - p).pow(3)) * (TURN_FRAMES - 1)).roundToInt()
            if (p >= 1f) break
        }
        frame = -1
        m.x.v += 8.4f
    }

    if (!calm) {
        // ---- phone tilt, from gravity: works whether the phone is flat or upright ----
        DisposableEffect(Unit) {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var base: FloatArray? = null
            var fx = 0f; var fy = 0f; var last = 0L
            val deg = (180.0 / Math.PI).toFloat()
            fun dz(v: Float) = if (abs(v) < DEAD_ZONE) 0f else v - sign(v) * DEAD_ZONE
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                    val side = atan2(gx, sqrt(gy * gy + gz * gz)) * deg      // left / right
                    val pitch = atan2(gz, gy) * deg                           // towards / away
                    val dt = if (last == 0L) 0f else ((e.timestamp - last) / 1e9f).coerceIn(0f, 0.1f)
                    last = e.timestamp
                    val b = base ?: floatArrayOf(side, pitch).also { base = it }
                    val follow = 1f - exp(-dt / RECENTRE_S)
                    b[0] += (side - b[0]) * follow; b[1] += (pitch - b[1]) * follow
                    val lp = 1f - exp(-dt / SMOOTH_S)
                    fx += (side - b[0] - fx) * lp; fy += (pitch - b[1] - fy) * lp
                    m.x.t = (-dz(fx) / FULL_TILT).coerceIn(-1.3f, 1.3f)
                    m.y.t = (dz(fy) / FULL_TILT).coerceIn(-1.3f, 1.3f)
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            onDispose { sm.unregisterListener(listener) }
        }
        // ---- springs, stepped in small fixed slices so they stay stable at any frame rate ----
        LaunchedEffect(Unit) {
            var lastN = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val dt = ((now - lastN) / 1e9f).coerceIn(0f, 0.05f); lastN = now
                val n = ceil(dt * 240f).toInt().coerceAtLeast(1); val h = dt / n
                repeat(n) {
                    m.x.step(h, SPRING, BOUNCE); m.y.step(h, SPRING, BOUNCE)
                    m.squash.step(h, 260f, 0.3f); m.hop.step(h, 90f, 0.28f)
                }
                m.time += dt
            }
        }
    }

    val shape = RoundedCornerShape(TileRadius)
    val dark = Pal.dark
    Box(
        modifier.fillMaxWidth().height(height).clip(shape).background(Pal.black.copy(alpha = if (dark) 0.06f else 0.045f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !calm) {
                // A proper hop: squash down, spring up, land with a squash and settle.
                m.squash.v += 5f
                scope.launch { delay(70); m.hop.v -= 520f; m.squash.v -= 7f; m.x.v += 2f; m.y.v -= 4f }
            }
            .semantics { contentDescription = "Picture of $name" }
    ) {
        // floor shadow: shrinks and fades while the device is in the air
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            m.time // read the clock so this layer redraws every frame with the springs
            val up =(-m.hop.p * 0.8f / 50f).coerceIn(0f, 1f)
            translationX = m.x.p.coerceIn(-1.6f, 1.6f) * 8.dp.toPx()
            scaleX = 1f - up * 0.45f; alpha = 1f - up * 0.6f
        }) {
            val w = size.width * 0.62f; val h = 26.dp.toPx()
            val c = Offset(size.width / 2, size.height - 34.dp.toPx() - h / 2)
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = if (dark) 0.22f else 0.14f), Color.Transparent), c, w / 2),
                topLeft = Offset(c.x - w / 2, c.y - h / 2), size = Size(w, h))
        }
        val img = still
        if (img != null) Canvas(
            Modifier.fillMaxSize().padding(bottom = 10.dp)
                .graphicsLayer {
                    val t = m.time
                    val x = m.x.p.coerceIn(-1.6f, 1.6f); val y = m.y.p.coerceIn(-1.6f, 1.6f)
                    val sq = (m.squash.p * 0.06f).coerceIn(-0.6f, 0.6f)
                    val float = if (calm) 0f else sin(t * 1.3f) * 3f
                    val sway = if (calm) 0f else sin(t * 0.9f) * 1.2f
                    rotationY = x * LEAN + sway
                    rotationX = -y * LEAN * 0.6f
                    rotationZ = sway * 0.4f
                    translationX = -x * 10.dp.toPx()
                    translationY = (-y * 6f + m.hop.p * 0.8f + sq * 12f + float).dp.toPx()
                    scaleX = 1f + sq * 0.5f; scaleY = 1f - sq * 0.5f
                    transformOrigin = TransformOrigin(0.5f, 0.7f)
                    cameraDistance = 12f * density
                }
        ) {
            val f = frame; val sh = sheet
            val (w0, h0) = if (f >= 0 && sh != null) TURN_W to TURN_H else img.width to img.height
            val s = min(size.width * 1.08f / w0, size.height / h0)
            val w = (w0 * s).roundToInt(); val h = (h0 * s).roundToInt()
            val off = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt())
            if (f >= 0 && sh != null) drawImage(sh, srcOffset = IntOffset((f % TURN_COLS) * TURN_W, (f / TURN_COLS) * TURN_H),
                srcSize = IntSize(TURN_W, TURN_H), dstOffset = off, dstSize = IntSize(w, h))
            else drawImage(img, dstOffset = off, dstSize = IntSize(w, h))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(Pal.bg).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Red)); Spacer(Modifier.width(6.dp))
                Text(typeLabel.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = Pal.black)
            }
            Spacer(Modifier.weight(1f))
            Text(modeLabel.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = Pal.muted,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Pal.line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}
