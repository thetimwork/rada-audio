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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TURN_FRAMES = 31          // 90 degrees, 3 degrees apart
private const val TURN_W = 540
private const val TURN_H = 314
private const val TURN_COLS = 8
private const val TURN_MS = 1400f
private const val MAX_TILT = 7f             // degrees

/** Devices whose quarter-turn has already played since the app started: it plays once per connection. */
private val played = mutableSetOf<String>()

private fun load(ctx: Context, path: String): ImageBitmap? = runCatching {
    ctx.assets.open(path).use { input ->
        val opts = BitmapFactory.Options().apply { if (Build.VERSION.SDK_INT >= 26) inPreferredConfig = Bitmap.Config.HARDWARE }
        BitmapFactory.decodeStream(input, null, opts)!!.asImageBitmap()
    }
}.getOrNull()

/**
 * The connected device as one sharp still render. When a device connects it makes one slow
 * quarter-turn (31 frames drawn once, no blending) and then rests; while it rests it leans up to
 * 7 degrees as you tilt the phone. With the phone's animations off it is just the still.
 * [id] identifies the connection, so the turn plays once per device, not every time the card shows.
 */
@Composable
fun DeviceSpin(kind: String, name: String, modifier: Modifier = Modifier, height: Dp = 200.dp, id: String = kind) {
    val ctx = LocalContext.current
    val still by produceState<ImageBitmap?>(null, kind) { value = withContext(Dispatchers.IO) { load(ctx, "devices/${kind}_still.webp") } }
    val calm = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }

    // ---- quarter-turn on connect ----
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
            val e = 1f - (1f - p).pow(3)
            frame = (e * (TURN_FRAMES - 1)).roundToInt()
            if (p >= 1f) break
        }
        frame = -1
    }

    // ---- tilt with the phone ----
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var sx by remember { mutableFloatStateOf(0f) }
    var sy by remember { mutableFloatStateOf(0f) }
    if (!calm) {
        DisposableEffect(Unit) {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val rot = FloatArray(9); val ang = FloatArray(3)
            var base: FloatArray? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, e.values)
                    SensorManager.getOrientation(rot, ang)
                    val b = base ?: ang.copyOf().also { base = it }
                    // Drift the resting position slowly so the picture always comes back to centre.
                    for (i in 1..2) b[i] += (ang[i] - b[i]) * 0.01f
                    val deg = 180f / Math.PI.toFloat()
                    tx = ((ang[2] - b[2]) * deg / 20f).coerceIn(-1f, 1f)   // roll: left/right
                    ty = ((ang[1] - b[1]) * deg / 20f).coerceIn(-1f, 1f)   // pitch: towards/away
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            onDispose { sm.unregisterListener(listener) }
        }
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { sx += (tx - sx) * 0.08f; sy += (ty - sy) * 0.08f }
        }
    }

    val img = still
    if (img == null) { androidx.compose.foundation.layout.Spacer(modifier.fillMaxWidth().height(height)); return }
    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = "Picture of $name" }
            .graphicsLayer {
                rotationY = sx * MAX_TILT
                rotationX = -sy * MAX_TILT * 0.6f
                translationX = -sx * 6.dp.toPx()
                translationY = -sy * 4.dp.toPx()
                cameraDistance = 12f * density
            }
    ) {
        val f = frame; val sh = sheet
        val (w0, h0) = if (f >= 0 && sh != null) TURN_W to TURN_H else img.width to img.height
        val s = min(size.width / w0, size.height / h0)
        val w = (w0 * s).roundToInt(); val h = (h0 * s).roundToInt()
        val off = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt())
        if (f >= 0 && sh != null) {
            drawImage(sh, srcOffset = IntOffset((f % TURN_COLS) * TURN_W, (f / TURN_COLS) * TURN_H), srcSize = IntSize(TURN_W, TURN_H),
                dstOffset = off, dstSize = IntSize(w, h))
        } else {
            drawImage(img, dstOffset = off, dstSize = IntSize(w, h))
        }
    }
}
