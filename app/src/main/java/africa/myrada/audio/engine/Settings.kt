package africa.myrada.audio.engine

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/** One complete sound setting for one output. Presets are full snapshots of this. */
data class Settings(
    val preset: String = "Signature",
    val bands: List<Float> = Presets.curve("Signature"),
    val preamp: Float = -4f,      // dB, applied before the EQ so boosts have headroom
    val bass: Float = 0f,         // 0..100, adds up to +6 dB to the low bands
    val clarity: Float = 0f,      // 0..100, adds up to +5 dB to the presence bands
    val balance: Float = 0f,      // -100 (left) .. 100 (right)
    val limiter: Boolean = true,
    val compressor: Boolean = false,
) {
    /** Final gain for band [i], after quick controls are mixed in. */
    fun bandGain(i: Int): Float {
        val bassW = floatArrayOf(1f, 0.85f, 0.55f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f)
        val clarW = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.4f, 1f, 0.8f, 0.3f, 0f)
        val g = bands[i] + bassW[i] * bass / 100f * 6f + clarW[i] * clarity / 100f * 5f
        return g.coerceIn(-15f, 15f)
    }

    fun toJson(): String = JSONObject().apply {
        put("preset", preset)
        put("bands", JSONArray(bands.map { it.toDouble() }))
        put("preamp", preamp.toDouble())
        put("bass", bass.toDouble())
        put("clarity", clarity.toDouble())
        put("balance", balance.toDouble())
        put("limiter", limiter)
        put("compressor", compressor)
    }.toString()

    companion object {
        val FREQS = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        const val BAND_COUNT = 10

        /** Upper edge of each band: halfway (geometrically) to the next centre. */
        val CUTOFFS: FloatArray = FloatArray(BAND_COUNT) { i ->
            if (i == BAND_COUNT - 1) 20000f else sqrt(FREQS[i] * FREQS[i + 1])
        }

        fun fromJson(text: String?): Settings? = try {
            if (text == null) null else {
                val o = JSONObject(text)
                val arr = o.getJSONArray("bands")
                if (arr.length() != BAND_COUNT) null else Settings(
                    preset = o.optString("preset", "Custom"),
                    bands = List(BAND_COUNT) { arr.getDouble(it).toFloat().coerceIn(-12f, 12f) },
                    preamp = o.optDouble("preamp", -4.0).toFloat().coerceIn(-18f, 6f),
                    bass = o.optDouble("bass", 0.0).toFloat().coerceIn(0f, 100f),
                    clarity = o.optDouble("clarity", 0.0).toFloat().coerceIn(0f, 100f),
                    balance = o.optDouble("balance", 0.0).toFloat().coerceIn(-100f, 100f),
                    limiter = o.optBoolean("limiter", true),
                    compressor = o.optBoolean("compressor", false),
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
    fun curve(name: String): List<Float> = curves[name] ?: List(10) { 0f }

    /** Applies a preset to [s]. Pure bypasses every effect, not just the EQ. */
    fun apply(name: String, s: Settings): Settings = when (name) {
        "Pure" -> Settings(preset = "Pure", bands = curve("Pure"), preamp = 0f, bass = 0f, clarity = 0f,
            balance = 0f, limiter = false, compressor = false)
        "Night" -> s.copy(preset = name, bands = curve(name), compressor = true)
        else -> s.copy(preset = name, bands = curve(name))
    }
}
