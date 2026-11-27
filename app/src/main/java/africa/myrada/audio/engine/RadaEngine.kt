package africa.myrada.audio.engine

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standard engine: Android's own effects.
 * - DynamicsProcessing for EQ, compressor, limiter and gain. First on the global output
 *   (session 0); Android has deprecated that and some phones refuse it, so it falls back to the
 *   sessions apps announce (Spotify and most players).
 * - EnvironmentalReverb on announced sessions (a global reverb receives no audio).
 * - Virtualizer on the global output, opt-in, honoured by some phones only.
 */
object RadaEngine {
    private const val TAG = "RadaEngine"
    private const val PRIORITY = 1000

    enum class Reach { OFF, SYSTEM_WIDE, PER_APP }

    data class Status(
        val running: Boolean = false,
        val reach: Reach = Reach.OFF,
        val apps: List<String> = emptyList(),
        val output: OutputInfo = Outputs.SPEAKER,
        val bypassed: Boolean = false,
        val virtualizerAccepted: Boolean? = null,
        val reverbApps: Int = 0,
        val advanced: Boolean = false,
        val message: String? = null,
    )

    /** A sound the user saved by name, for a type of listening gear. */
    data class SavedProfile(
        val id: String,
        val name: String,
        val type: String,
        val outputKey: String,
        val outputName: String,
        val settings: Settings,
        val savedAt: Long,
    )

    val PROFILE_TYPES = listOf("Headphones", "Earphones", "USB DAC", "Speaker")

    private val _library = MutableStateFlow<List<SavedProfile>>(emptyList())
    val library: StateFlow<List<SavedProfile>> = _library.asStateFlow()

    private class Session(val pkg: String, var dp: DynamicsProcessing?, var reverb: EnvironmentalReverb?)

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var global: DynamicsProcessing? = null
    private var virtualizer: Virtualizer? = null
    private val sessions = LinkedHashMap<Int, Session>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("rada_profiles", Context.MODE_PRIVATE)
        _settings.value = load(Outputs.SPEAKER.key)
        _status.value = _status.value.copy(advanced = prefs?.getBoolean("advanced", false) == true)
        _library.value = readLibrary()
    }

    // ---- lifecycle -------------------------------------------------------------------------

    @Synchronized
    fun start() {
        if (_status.value.running) return
        global = try {
            build(0)
        } catch (t: Throwable) {
            Log.w(TAG, "Session 0 refused, using per-app sessions", t); null
        }
        _status.value = _status.value.copy(
            running = true,
            reach = if (global != null) Reach.SYSTEM_WIDE else Reach.PER_APP,
            message = if (global == null) "This phone refused a system-wide effect. RADA works in apps that announce their audio." else null,
        )
        applyAll()
    }

    @Synchronized
    fun stop() {
        global?.release(); global = null
        virtualizer?.release(); virtualizer = null
        sessions.values.forEach { it.dp?.release(); it.reverb?.release() }
        sessions.clear()
        _status.value = _status.value.copy(running = false, reach = Reach.OFF, apps = emptyList(), reverbApps = 0,
            virtualizerAccepted = null, message = null)
    }

    // ---- sessions announced by apps --------------------------------------------------------

    @Synchronized
    fun openSession(id: Int, pkg: String) {
        if (id <= 0 || !_status.value.running || sessions.containsKey(id)) return
        // With a working global effect, a second DynamicsProcessing on the session would double the EQ.
        val dp = if (global != null) null else runCatching { build(id) }.onFailure { Log.w(TAG, "dp $id", it) }.getOrNull()
        sessions[id] = Session(pkg, dp, null)
        applyAll()
    }

    @Synchronized
    fun closeSession(id: Int) {
        sessions.remove(id)?.let { it.dp?.release(); it.reverb?.release() }
        publishApps()
    }

    private fun publishApps() {
        _status.value = _status.value.copy(
            apps = sessions.values.map { it.pkg }.distinct(),
            reverbApps = sessions.values.count { it.reverb != null },
        )
    }

    // ---- outputs, settings, modes ----------------------------------------------------------

    fun onOutputChanged(output: OutputInfo) {
        if (output == _status.value.output) return
        _status.value = _status.value.copy(output = output)
        _settings.value = load(output.key)
        applyAll()
    }

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs?.edit()?.putString(_status.value.output.key, next.toJson())?.apply()
        applyAll()
    }

    fun resetOutput() = update { Settings() }

    fun setAdvanced(on: Boolean) {
        prefs?.edit()?.putBoolean("advanced", on)?.apply()
        _status.value = _status.value.copy(advanced = on)
    }

    /** Hold-to-compare: every effect off while pressed. */
    @Synchronized
    fun setBypass(on: Boolean) {
        _status.value = _status.value.copy(bypassed = on)
        applyAll()
    }

    fun savedOutputs(): List<String> = prefs?.all?.keys?.filter { it != "advanced" && it != "library" }?.sorted() ?: emptyList()

    /** Saves the current sound under [name]. Saving with an existing name and type replaces it. */
    fun saveProfile(name: String, type: String): SavedProfile {
        val clean = name.trim().ifEmpty { "My sound" }
        val out = _status.value.output
        val existing = _library.value.firstOrNull { it.name.equals(clean, true) && it.type == type }
        val p = SavedProfile(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = clean, type = type, outputKey = out.key, outputName = out.name,
            settings = _settings.value.copy(preset = clean), savedAt = System.currentTimeMillis(),
        )
        val next = listOf(p) + _library.value.filter { it.id != p.id }
        writeLibrary(next)
        update { it.copy(preset = clean) }
        return p
    }

    /** Puts a saved profile on the output that is playing now. */
    fun loadProfile(id: String) {
        val p = _library.value.firstOrNull { it.id == id } ?: return
        update { p.settings.copy(preset = p.name) }
    }

    fun deleteProfile(id: String) = writeLibrary(_library.value.filter { it.id != id })

    private fun writeLibrary(list: List<SavedProfile>) {
        _library.value = list
        val arr = org.json.JSONArray()
        list.forEach { p ->
            arr.put(org.json.JSONObject()
                .put("id", p.id).put("name", p.name).put("type", p.type)
                .put("outputKey", p.outputKey).put("outputName", p.outputName)
                .put("settings", p.settings.toJson()).put("savedAt", p.savedAt))
        }
        prefs?.edit()?.putString("library", arr.toString())?.apply()
    }

    private fun readLibrary(): List<SavedProfile> = try {
        val arr = org.json.JSONArray(prefs?.getString("library", null) ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val s = Settings.fromJson(o.optString("settings")) ?: return@mapNotNull null
            SavedProfile(o.getString("id"), o.getString("name"), o.optString("type", "Headphones"),
                o.optString("outputKey"), o.optString("outputName"), s, o.optLong("savedAt"))
        }
    } catch (e: Exception) { Log.w(TAG, "library unreadable", e); emptyList() }

    fun profileFor(key: String): Settings = load(key)

    private fun load(key: String): Settings = Settings.fromJson(prefs?.getString(key, null)) ?: Settings()

    // ---- effects ---------------------------------------------------------------------------

    private fun build(session: Int): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,                          // stereo
            true, Settings.BAND_COUNT,  // pre-EQ: the graphic EQ
            true, 1,                    // one-band compressor
            false, 1,                   // post-EQ unused
            true,                       // limiter
        ).build()
        return DynamicsProcessing(PRIORITY, session, config)
    }

    @Synchronized
    private fun applyAll() {
        if (!_status.value.running) return
        val s = _settings.value
        val active = !_status.value.bypassed
        (listOfNotNull(global) + sessions.values.mapNotNull { it.dp }).forEach { dp ->
            runCatching { applyDynamics(dp, s, active) }.onFailure { Log.w(TAG, "dynamics", it) }
        }
        applyReverb(s, active)
        applyVirtualizer(s, active)
        publishApps()
    }

    private fun applyDynamics(dp: DynamicsProcessing, s: Settings, active: Boolean) {
        for (ch in 0..1) {
            val eq = DynamicsProcessing.Eq(true, true, Settings.BAND_COUNT)
            for (i in 0 until Settings.BAND_COUNT) {
                eq.setBand(i, DynamicsProcessing.EqBand(true, Settings.CUTOFFS[i], s.bandGain(i) + s.widthCut(ch, i)))
            }
            dp.setPreEqByChannelIndex(ch, eq)
        }

        val mbc = DynamicsProcessing.Mbc(true, s.compressor, 1)
        mbc.setBand(0, DynamicsProcessing.MbcBand(
            s.compressor, 20000f,
            10f, 200f,   // attack, release (ms)
            3f, -24f,    // ratio, threshold (dB)
            6f,          // knee width
            -90f, 1f,    // noise gate off
            0f, 3f,      // pre gain, make-up gain (dB)
        ))
        dp.setMbcAllChannelsTo(mbc)

        dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, s.limiter, 0, 1f, 60f, 10f, -1f, 0f))

        // Balance cuts the side it leans away from; ear trims are per channel.
        val left = s.preamp + s.leftEar + if (s.balance > 0) -s.balance / 100f * 12f else 0f
        val right = s.preamp + s.rightEar + if (s.balance < 0) s.balance / 100f * 12f else 0f
        dp.setInputGainbyChannel(0, left)
        dp.setInputGainbyChannel(1, right)
        dp.enabled = active
    }

    private fun applyReverb(s: Settings, active: Boolean) {
        val want = active && s.reverb
        sessions.forEach { (id, sess) ->
            if (!want) {
                sess.reverb?.release(); sess.reverb = null
                return@forEach
            }
            if (sess.reverb == null) {
                sess.reverb = runCatching { EnvironmentalReverb(PRIORITY, id) }
                    .onFailure { Log.w(TAG, "reverb $id", it) }.getOrNull()
            }
            sess.reverb?.let { rv -> runCatching { configureReverb(rv, s); rv.enabled = true } }
        }
    }

    private fun configureReverb(rv: EnvironmentalReverb, s: Settings) {
        val size = s.roomSize / 100f
        val decay = when (s.room) { "Studio" -> 500; "Concert" -> 1800; "Hall" -> 2800; else -> 1100 }
        rv.decayTime = (decay * (0.6f + size * 0.8f)).toInt().coerceIn(100, 20000)
        rv.decayHFRatio = 800.toShort()
        rv.roomLevel = (-1200 + size * 600).toInt().toShort()
        rv.roomHFLevel = (-600).toShort()
        rv.reflectionsLevel = (-1500 + size * 700).toInt().toShort()
        rv.reflectionsDelay = (8 + size * 40).toInt()
        rv.reverbLevel = (-1800 + size * 1100).toInt().toShort()
        rv.reverbDelay = (10 + size * 50).toInt()
        rv.diffusion = 1000.toShort()
        rv.density = 1000.toShort()
    }

    private fun applyVirtualizer(s: Settings, active: Boolean) {
        val want = active && s.virtualizer
        if (!want) {
            virtualizer?.release(); virtualizer = null
            return
        }
        if (virtualizer == null) {
            virtualizer = runCatching { Virtualizer(PRIORITY, 0) }.onFailure { Log.w(TAG, "virtualizer", it) }.getOrNull()
        }
        val v = virtualizer
        val ok = v != null && runCatching {
            if (v.strengthSupported) v.setStrength(800.toShort())
            v.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
            v.enabled = true
        }.isSuccess
        _status.value = _status.value.copy(virtualizerAccepted = ok)
    }
}
