package africa.myrada.audio.engine.dsp

/**
 * Freeverb (Jezar at Dreampoint, public domain): eight parallel lowpass-feedback comb filters and
 * four series allpasses per channel, the right channel offset by a stereo spread. Tunings are the
 * published 44.1 kHz values, scaled to the running sample rate. A pre-delay sits in front.
 */
class Freeverb(private val rate: Int) {
    private val scale = rate / 44100f
    private fun n(x: Int) = (x * scale).toInt().coerceAtLeast(1)

    private class Comb(size: Int) {
        val buf = FloatArray(size); var i = 0; var store = 0f
        var feedback = 0f; var damp1 = 0f; var damp2 = 0f
        fun process(x: Float): Float {
            val out = buf[i]
            store = out * damp2 + store * damp1
            buf[i] = x + store * feedback
            if (++i >= buf.size) i = 0
            return out
        }
        fun clear() { buf.fill(0f); store = 0f }
    }

    private class Allpass(size: Int) {
        val buf = FloatArray(size); var i = 0
        fun process(x: Float): Float {
            val b = buf[i]
            buf[i] = x + b * 0.5f
            if (++i >= buf.size) i = 0
            return b - x
        }
        fun clear() = buf.fill(0f)
    }

    private val combTune = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val apTune = intArrayOf(556, 441, 341, 225)
    private val spread = 23
    private val combL = combTune.map { Comb(n(it)) }
    private val combR = combTune.map { Comb(n(it + spread)) }
    private val apL = apTune.map { Allpass(n(it)) }
    private val apR = apTune.map { Allpass(n(it + spread)) }

    private val pre = FloatArray(rate / 10 + 1)   // up to 100 ms pre-delay (mono send)
    private var preW = 0
    private var preLen = 1

    private var wet1 = 0f; private var wet2 = 0f

    /** room 0..1, damp 0..1, wet 0..1, width 0..1, preDelayMs 0..100. */
    fun set(room: Float, damp: Float, wet: Float, width: Float = 1f, preDelayMs: Float = 0f) {
        val fb = room * 0.28f + 0.7f
        val d = damp * 0.4f
        (combL + combR).forEach { it.feedback = fb; it.damp1 = d; it.damp2 = 1f - d }
        val w = wet * 3f
        wet1 = w * (width / 2f + 0.5f)
        wet2 = w * ((1f - width) / 2f)
        preLen = (preDelayMs / 1000f * rate).toInt().coerceIn(1, pre.size - 1)
    }

    fun clear() { (combL + combR).forEach { it.clear() }; (apL + apR).forEach { it.clear() }; pre.fill(0f) }

    /** Adds the reverb to interleaved stereo [buf] in place. */
    fun process(buf: FloatArray, frames: Int) {
        var k = 0
        for (f in 0 until frames) {
            val l = buf[k]; val r = buf[k + 1]
            pre[preW] = (l + r) * 0.015f
            var ri = preW - preLen; if (ri < 0) ri += pre.size
            val x = pre[ri]
            if (++preW >= pre.size) preW = 0
            var ol = 0f; var or = 0f
            for (c in 0 until 8) { ol += combL[c].process(x); or += combR[c].process(x) }
            for (a in 0 until 4) { ol = apL[a].process(ol); or = apR[a].process(or) }
            buf[k] = l + ol * wet1 + or * wet2
            buf[k + 1] = r + or * wet1 + ol * wet2
            k += 2
        }
    }
}
