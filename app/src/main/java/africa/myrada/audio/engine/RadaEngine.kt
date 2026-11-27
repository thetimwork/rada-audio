package africa.myrada.audio.engine

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standard engine: Android's DynamicsProcessing effect.
 * First it tries the global output (session 0). Android has deprecated that and some phones
 * refuse it, so it falls back to the sessions that apps announce (Spotify and most players do).
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
        val message: String? = null,
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var global: DynamicsProcessing? = null
    private val sessions = LinkedHashMap<Int, Pair<String, DynamicsProcessing?>>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("rada_profiles", Context.MODE_PRIVATE)
        _settings.value = load(Outputs.SPEAKER.key)
    }

    // ---- lifecycle -------------------------------------------------------------------------

    @Synchronized
    fun start() {
        if (_status.value.running) return
        global = try {
            build(0)
        } catch (t: Throwable) {
            Log.w(TAG, "Session 0 refused, using per-app sessions", t)
            null
        }
        val reach = if (global != null) Reach.SYSTEM_WIDE else Reach.PER_APP
        _status.value = _status.value.copy(
            running = true, reach = reach,
            message = if (global == null) "This phone refused a system-wide effect. RADA works in apps that announce their audio." else null,
        )
        applyAll()
    }

    @Synchronized
    fun stop() {
        global?.release(); global = null
        sessions.values.forEach { it.second?.release() }
        sessions.clear()
        _status.value = _status.value.copy(running = false, reach = Reach.OFF, apps = emptyList(), message = null)
    }

    // ---- sessions announced by apps --------------------------------------------------------

    @Synchronized
    fun openSession(id: Int, pkg: String) {
        if (id <= 0 || !_status.value.running || sessions.containsKey(id)) return
        // With a working global effect, a second effect on the app session would double the EQ.
        val dp = if (global != null) null else try {
            build(id).also { apply(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not attach to session $id ($pkg)", t); null
        }
        sessions[id] = pkg to dp
        publishApps()
    }

    @Synchronized
    fun closeSession(id: Int) {
        sessions.remove(id)?.second?.release()
        publishApps()
    }

    private fun publishApps() {
        _status.value = _status.value.copy(apps = sessions.values.map { it.first }.distinct())
    }

    // ---- outputs and settings --------------------------------------------------------------

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

    /** Hold-to-compare: switches the effect off while pressed. Same level, nothing else changes. */
    @Synchronized
    fun setBypass(on: Boolean) {
        _status.value = _status.value.copy(bypassed = on)
        allEffects().forEach { runCatching { it.enabled = !on } }
    }

    fun savedOutputs(): List<String> = prefs?.all?.keys?.sorted() ?: emptyList()

    private fun load(key: String): Settings = Settings.fromJson(prefs?.getString(key, null)) ?: Settings()

    // ---- DynamicsProcessing ----------------------------------------------------------------

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

    private fun allEffects(): List<DynamicsProcessing> =
        listOfNotNull(global) + sessions.values.mapNotNull { it.second }

    @Synchronized
    private fun applyAll() {
        allEffects().forEach { runCatching { apply(it) }.onFailure { e -> Log.w(TAG, "apply failed", e) } }
    }

    private fun apply(dp: DynamicsProcessing) {
        val s = _settings.value

        val eq = DynamicsProcessing.Eq(true, true, Settings.BAND_COUNT)
        for (i in 0 until Settings.BAND_COUNT) {
            eq.setBand(i, DynamicsProcessing.EqBand(true, Settings.CUTOFFS[i], s.bandGain(i)))
        }
        dp.setPreEqAllChannelsTo(eq)

        val mbc = DynamicsProcessing.Mbc(true, s.compressor, 1)
        mbc.setBand(0, DynamicsProcessing.MbcBand(
            s.compressor, 20000f,
            10f, 200f,   // attack, release (ms)
            3f, -24f,    // ratio, threshold (dB)
            6f,          // knee width
            -90f, 1f,    // noise gate threshold, expander ratio (off)
            0f, 3f,      // pre gain, post gain (dB): a little make-up gain
        ))
        dp.setMbcAllChannelsTo(mbc)

        dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(
            true, s.limiter, 0,
            1f, 60f,     // attack, release (ms)
            10f, -1f,    // ratio, threshold (dB)
            0f,
        ))

        // Balance cuts the side it leans away from, so it never adds gain.
        val left = s.preamp + if (s.balance > 0) -s.balance / 100f * 12f else 0f
        val right = s.preamp + if (s.balance < 0) s.balance / 100f * 12f else 0f
        dp.setInputGainbyChannel(0, left)
        dp.setInputGainbyChannel(1, right)

        dp.enabled = !_status.value.bypassed
    }
}
