package africa.myrada.audio.engine.dsp

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/** Left and right summed into both ears. */
object Mono {
    fun process(buf: FloatArray, frames: Int) {
        var k = 0
        repeat(frames) { val m = (buf[k] + buf[k + 1]) * 0.5f; buf[k] = m; buf[k + 1] = m; k += 2 }
    }
}

/** Gentle valve-style colour: a little soft saturation blended under the clean signal. */
object Warmth {
    fun process(buf: FloatArray, frames: Int, amount: Float = 0.25f) {
        for (i in 0 until frames * 2) {
            val x = buf[i]
            buf[i] = x * (1f - amount) + tanh(x * 1.6f).toFloat() / 1.6f * amount
        }
    }
}

/**
 * Loudness levelling: rides a slow gain toward a target RMS so quiet and loud songs or apps end up
 * closer. Slow attack and release (seconds) so it does not pump; capped at ±9 dB.
 */
class Leveller(private val rate: Int) {
    private var ms = 0.003f   // running mean square
    private var gain = 1f
    private val target = 0.12f
    fun process(buf: FloatArray, frames: Int) {
        var sum = 0f
        for (i in 0 until frames * 2) sum += buf[i] * buf[i]
        val blk = sum / (frames * 2)
        val a = frames / (rate * 3f)
        ms += (blk - ms) * a.coerceIn(0f, 1f)
        val rms = sqrt(ms.coerceAtLeast(1e-9f))
        val want = if (rms < 0.0005f) gain else (target / rms).coerceIn(0.35f, 2.8f)
        val step = frames / (rate * 1.5f)
        gain += (want - gain) * step.coerceIn(0f, 1f)
        for (i in 0 until frames * 2) buf[i] *= gain
    }
}

/** Last stage: never let the summed effects clip. */
object SafetyClip {
    fun process(buf: FloatArray, frames: Int) {
        for (i in 0 until frames * 2) { val x = buf[i]; if (abs(x) > 0.98f) buf[i] = tanh(x).toFloat() }
    }
}
