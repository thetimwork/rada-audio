package africa.myrada.audio.engine.dsp

import kotlin.math.PI
import kotlin.math.exp

/** One-pole lowpass. */
private class OnePole(rate: Int, hz: Float) {
    private val a = exp(-2.0 * PI * hz / rate).toFloat()
    private var z = 0f
    fun process(x: Float): Float { z = x * (1f - a) + z * a; return z }
    fun clear() { z = 0f }
}

/** Fixed fractional-free delay line. */
private class Delay(samples: Int) {
    private val buf = FloatArray(samples.coerceAtLeast(1) + 1); private var i = 0
    private val len = samples.coerceAtLeast(1)
    fun process(x: Float): Float {
        var r = i - len; if (r < 0) r += buf.size
        val y = buf[r]; buf[i] = x; if (++i >= buf.size) i = 0; return y
    }
    fun clear() = buf.fill(0f)
}

/**
 * Crossfeed for headphones, after Bauer's stereophonic-to-binaural idea: each ear also hears the
 * other channel, lowpassed (~700 Hz), delayed ~0.3 ms and about 4.5 dB down, so hard-panned
 * sounds stop sitting inside one ear. The direct path is trimmed to keep the level steady.
 */
class Crossfeed(rate: Int) {
    private val lpL = OnePole(rate, 700f); private val lpR = OnePole(rate, 700f)
    private val dL = Delay((0.0003f * rate).toInt()); private val dR = Delay((0.0003f * rate).toInt())
    private val direct = 0.75f
    private val cross = 0.45f   // 0.45 / 0.75 ≈ -4.4 dB relative to the direct path
    fun process(buf: FloatArray, frames: Int) {
        var k = 0
        repeat(frames) {
            val l = buf[k]; val r = buf[k + 1]
            val xl = dR.process(lpR.process(r)); val xr = dL.process(lpL.process(l))
            buf[k] = l * direct + xl * cross
            buf[k + 1] = r * direct + xr * cross
            k += 2
        }
    }
}

/**
 * Virtual surround for headphones: places the two channels as speakers about 30° either side.
 * Each speaker reaches the far ear later (~0.26 ms), quieter and darker (head shadow), plus a few
 * early reflections from a small room on alternating sides, which is what moves sound out of the head.
 */
class SpeakerSim(rate: Int) {
    private val itd = (0.00026f * rate).toInt()
    private val shadowL = OnePole(rate, 1500f); private val shadowR = OnePole(rate, 1500f)
    private val farL = Delay(itd); private val farR = Delay(itd)
    // early reflections: (delay ms, gain, same side?)
    private val taps = listOf(Triple(7.1f, 0.30f, true), Triple(11.3f, 0.24f, false), Triple(14.9f, 0.20f, true),
        Triple(19.7f, 0.16f, false), Triple(23.3f, 0.12f, true), Triple(29.1f, 0.09f, false))
    private val tapL = taps.map { Delay((it.first / 1000f * rate).toInt()) }
    private val tapR = taps.map { Delay((it.first / 1000f * rate).toInt()) }
    private val erTone = OnePole(rate, 5000f); private val erTone2 = OnePole(rate, 5000f)
    var amount = 1f
    fun process(buf: FloatArray, frames: Int) {
        var k = 0
        repeat(frames) {
            val l = buf[k]; val r = buf[k + 1]
            // speaker L reaches right ear late and shadowed, and vice versa
            val lToR = farL.process(shadowL.process(l)) * 0.62f
            val rToL = farR.process(shadowR.process(r)) * 0.62f
            var outL = l + rToL
            var outR = r + lToR
            var erL = 0f; var erR = 0f
            for (t in taps.indices) {
                val (_, g, same) = taps[t]
                val a = tapL[t].process(l) * g; val b = tapR[t].process(r) * g
                if (same) { erL += a; erR += b } else { erL += b; erR += a }
            }
            outL += erTone.process(erL); outR += erTone2.process(erR)
            // level match: two paths summed plus reflections
            buf[k] = l + (outL * 0.72f - l) * amount
            buf[k + 1] = r + (outR * 0.72f - r) * amount
            k += 2
        }
    }
}
