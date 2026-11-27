package africa.myrada.audio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private const val FRAMES = 48
private const val DEG_PER_SEC = 24f

/**
 * The connected device as a rendered 3D turntable (48 frames, every 7.5°, transparent background).
 * Turns slowly on its own unless the phone's animations are switched off; drag to turn it by hand.
 * Adjacent frames are blended so the motion stays smooth.
 */
@Composable
fun DeviceSpin(kind: String, name: String, modifier: Modifier = Modifier, height: Dp = 200.dp) {
    val ctx = LocalContext.current
    val frames by produceState<List<ImageBitmap>?>(null, kind) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (0 until FRAMES).map { i ->
                    ctx.assets.open("devices/${kind}_%02d.webp".format(i)).use { input ->
                        val opts = BitmapFactory.Options().apply {
                            if (Build.VERSION.SDK_INT >= 26) inPreferredConfig = Bitmap.Config.HARDWARE
                        }
                        BitmapFactory.decodeStream(input, null, opts)!!.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
    }
    val still = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    var angle by remember(kind) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var resumeAt by remember { mutableStateOf(0L) }

    LaunchedEffect(kind, still) {
        if (still) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { t ->
                val dt = (t - last) / 1e9f; last = t
                if (!dragging && System.currentTimeMillis() >= resumeAt) angle = (angle + dt * DEG_PER_SEC) % 360f
            }
        }
    }

    val list = frames
    if (list == null) {
        // Until the frames are decoded, show the flat drawing.
        DeviceArt(kind, modifier)
        return
    }
    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = "3D view of $name. Drag sideways to turn it." }
            .pointerInput(kind) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; resumeAt = System.currentTimeMillis() + 1500 },
                    onDragCancel = { dragging = false },
                ) { _, dx -> angle = ((angle - dx * 0.45f) % 360f + 360f) % 360f }
            }
    ) {
        val pos = ((angle % 360f + 360f) % 360f) / 360f * FRAMES
        val i0 = floor(pos).toInt() % FRAMES
        val i1 = (i0 + 1) % FRAMES
        val f = pos - floor(pos)
        val img = list[i0]
        val s = min(size.width / img.width, size.height / img.height)
        val w = (img.width * s).roundToInt(); val h = (img.height * s).roundToInt()
        val off = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt())
        drawImage(list[i0], dstOffset = off, dstSize = IntSize(w, h), alpha = 1f)
        if (f > 0.02f) drawImage(list[i1], dstOffset = off, dstSize = IntSize(w, h), alpha = f)
    }
}
