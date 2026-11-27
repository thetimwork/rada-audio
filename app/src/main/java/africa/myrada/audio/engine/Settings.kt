package africa.myrada.audio.engine

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/** One complete sound setting for one output. Presets are full snapshots of this. */
data class Settings(
    val preset: String = "Signature",
    val bands: List<Float> = Presets.curve("Signature"),
    val preamp: Float = -4f,       // dB before the EQ, so boosts have headroom
    val treble: Float = 0f,        // dB tilt on the top bands
    val bass: Float = 35f,         // 0..100 quick control, up to +6 dB on low bands
    val clarity: Float = 20f,      // 0..100 quick control, up to +5 dB on presence bands
    val width: Float = 20f,        // 0..100; subtle per-channel lean in Standard
    val balance: Float = 0f,       // -100 (left) .. 100 (right)
    val leftEar: Float = 0f,       // dB trim
    val rightEar: Float = 0f,      // dB trim
    val limiter: Boolean = true,
    val compressor: Boolean = false,
    val reverb: Boolean = false,
    val room: String = "Club",
    val roomSize: Float = 45f,     // 0..100
    val virtualizer: Boolean = false,
    val autoEq: Boolean = false,
    val noise: String = "ANC",     // manual noise-mode label for presets per mode
    // Advanced engine (Shizuku) effects. Stored now, processed by the capture engine.
    val spatial: Boolean = false,
    val crossfeed: Boolean = false,
    val mono: Boolean = false,
    val convolution: Boolean = false,
    val warmth: Boolean = false,
    val levelling: Boolean = false,
) {
    /** Final gain for band [i], after quick controls and treble are mixed in. */
    fun bandGain(i: Int): Float {
        val bassW = floatArrayOf(1f, 0.85f, 0.55f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f)
        val clarW = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.4f, 1f, 0.8f, 0.3f, 0f)
        val trebW = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0.2f, 0.5f, 0.85f, 1f)
        val g = bands[i] + bassW[i] * bass / 100f * 6f + clarW[i] * clarity / 100f * 5f + trebW[i] * treble
        return g.coerceIn(-15f, 15f)
    }

    /**
     * Standard-engine stereo width: Android won't let an effect mix channels, so RADA leans
     * alternate mid bands to one side by cutting the other. Never adds gain.
     */
    fun widthCut(channel: Int, band: Int): Float {
        if (band !in 3..8 || width <= 0f) return 0f
        val cut = -width / 100f * 3f
        val leansLeft = band % 2 == 1
        return if ((channel == 1 && leansLeft) || (channel == 0 && !leansLeft)) cut else 0f
    }

    fun toJson(): String = JSONObject().apply {
        put("preset", preset)
        put("bands", JSONArray(bands.map { it.toDouble() }))
        listOf("preamp" to preamp, "treble" to treble, "bass" to bass, "clarity" to clarity, "width" to width,
            "balance" to balance, "leftEar" to leftEar, "rightEar" to rightEar, "roomSize" to roomSize)
            .forEach { (k, v) -> put(k, v.toDouble()) }
        listOf("limiter" to limiter, "compressor" to compressor, "reverb" to reverb, "virtualizer" to virtualizer,
            "autoEq" to autoEq, "spatial" to spatial, "crossfeed" to crossfeed, "mono" to mono,
            "convolution" to convolution, "warmth" to warmth, "levelling" to levelling)
            .forEach { (k, v) -> put(k, v) }
        put("room", room)
        put("noise", noise)
    }.toString()

    companion object {
        val FREQS = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val LABELS = listOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        const val BAND_COUNT = 10

        /** Upper edge of each band: halfway (geometrically) to the next centre. */
        val CUTOFFS: FloatArray = FloatArray(BAND_COUNT) { i ->
            if (i == BAND_COUNT - 1) 20000f else sqrt(FREQS[i] * FREQS[i + 1])
        }

        fun fromJson(text: String?): Settings? = try {
            if (text == null) null else {
                val o = JSONObject(text)
                val arr = o.getJSONArray("bands")
                val d = Settings()
                fun f(k: String, def: Float, lo: Float, hi: Float) = o.optDouble(k, def.toDouble()).toFloat().coerceIn(lo, hi)
                if (arr.length() != BAND_COUNT) null else Settings(
                    preset = o.optString("preset", "Custom"),
                    bands = List(BAND_COUNT) { arr.getDouble(it).toFloat().coerceIn(-12f, 12f) },
                    preamp = f("preamp", d.preamp, -18f, 6f),
                    treble = f("treble", d.treble, -12f, 12f),
                    bass = f("bass", d.bass, 0f, 100f),
                    clarity = f("clarity", d.clarity, 0f, 100f),
                    width = f("width", d.width, 0f, 100f),
                    balance = f("balance", d.balance, -100f, 100f),
                    leftEar = f("leftEar", 0f, -12f, 12f),
                    rightEar = f("rightEar", 0f, -12f, 12f),
                    limiter = o.optBoolean("limiter", true),
                    compressor = o.optBoolean("compressor", false),
                    reverb = o.optBoolean("reverb", false),
                    room = o.optString("room", "Club"),
                    roomSize = f("roomSize", d.roomSize, 0f, 100f),
                    virtualizer = o.optBoolean("virtualizer", false),
                    autoEq = o.optBoolean("autoEq", false),
                    noise = o.optString("noise", "ANC"),
                    spatial = o.optBoolean("spatial", false),
                    crossfeed = o.optBoolean("crossfeed", false),
                    mono = o.optBoolean("mono", false),
                    convolution = o.optBoolean("convolution", false),
                    warmth = o.optBoolean("warmth", false),
                    levelling = o.optBoolean("levelling", false),
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}

object Presets {
    private val curves = linkedMapOf(
        "Signature" to listOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 2f, 1f),
        "Reference" to List(10) { 0f },
        "Deep Bass" to listOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 1f, 1f, 0f),
        "Cinema" to listOf(5f, 4f, 2f, 0f, 1f, 3f, 4f, 3f, 2f, 1f),
        "Vocal" to listOf(-3f, -2f, 0f, 2f, 4f, 5f, 3f, 1f, 0f, -1f),
        "Live" to listOf(2f, 3f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f),
        "Night" to listOf(0f, 0f, 0f, 0f, 1f, 2f, 1f, 0f, 0f, 0f),
        "Pure" to List(10) { 0f },
    )
    val names: List<String> get() = curves.keys.toList()
    val rooms = listOf("Studio", "Club", "Concert", "Hall")
    fun curve(name: String): List<Float> = curves[name] ?: List(10) { 0f }

    /** Applies a preset. Pure bypasses every effect, not just the EQ. */
    fun apply(name: String, s: Settings): Settings = when (name) {
        "Pure" -> s.copy(preset = "Pure", bands = curve("Pure"), preamp = 0f, treble = 0f, bass = 0f, clarity = 0f,
            width = 0f, balance = 0f, limiter = false, compressor = false, reverb = false, virtualizer = false)
        "Night" -> s.copy(preset = name, bands = curve(name), compressor = true)
        else -> s.copy(preset = name, bands = curve(name))
    }
}
