package africa.myrada.audio.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.DynamicsProcessing
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import africa.myrada.audio.engine.dsp.Crossfeed
import africa.myrada.audio.engine.dsp.Freeverb
import africa.myrada.audio.engine.dsp.Leveller
import africa.myrada.audio.engine.dsp.Mono
import africa.myrada.audio.engine.dsp.SafetyClip
import africa.myrada.audio.engine.dsp.SpeakerSim
import africa.myrada.audio.engine.dsp.Warmth
import kotlin.math.sqrt

/**
 * Advanced engine (Android 10+, set up through Shizuku).
 *
 * 1. Finds playing apps and their audio sessions from `dumpsys audio` (DUMP permission).
 * 2. Probes each app with a playback capture limited to its uid. Apps that block capture give
 *    silence and are left alone on the Standard engine.
 * 3. For apps that do give audio: captures them, runs Rada's own DSP (rooms, virtual surround,
 *    crossfeed, mono, warmth, levelling), plays the result, and mutes the original sessions with a
 *    session effect so they are not heard twice. The Standard EQ on the global output still applies.
 *
 * Safety: if capture goes silent while muted apps are still playing, every session is unmuted and
 * the engine stops, so an app is never left silent.
 */
@RequiresApi(29)
class CaptureEngine(private val ctx: Context, private val projection: MediaProjection, private val onStopped: (String?) -> Unit) {

    private companion object {
        const val TAG = "CaptureEngine"
        const val RATE = 48000
        const val BLOCK = 480                     // 10 ms
        val CAPTURE_USAGES = setOf("USAGE_MEDIA", "USAGE_GAME", "USAGE_UNKNOWN")
    }

    private data class Player(val uid: Int, val session: Int, val usage: String)

    @Volatile private var running = false
    @Volatile private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var audioThread: Thread? = null
    private var scanThread: Thread? = null

    private val confirmed = LinkedHashSet<Int>()          // uids we capture and mute
    private val rejectedUntil = HashMap<Int, Long>()      // uids that gave silence: retry later
    private val muted = HashMap<Int, DynamicsProcessing>()
    @Volatile private var lastSignal = 0L

    // DSP
    private val reverb = Freeverb(RATE)
    private val crossfeed = Crossfeed(RATE)
    private val surround = SpeakerSim(RATE)
    private val leveller = Leveller(RATE)
    private var roomKey = ""

    fun start(): Boolean {
        val t = buildTrack() ?: return false
        track = t
        t.play()
        RadaEngine.openSession(t.audioSessionId, ctx.packageName)   // EQ on our output if there is no global effect
        running = true
        lastSignal = System.currentTimeMillis()
        audioThread = Thread({ audioLoop() }, "rada-capture").apply { start() }
        scanThread = Thread({ scanLoop() }, "rada-scan").apply { start() }
        return true
    }

    fun stop(reason: String? = null) {
        if (!running) return
        running = false
        synchronized(muted) { muted.values.forEach { runCatching { it.release() } }; muted.clear() }
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        track?.let { RadaEngine.closeSession(it.audioSessionId); runCatching { it.stop() }; runCatching { it.release() } }
        track = null
        runCatching { projection.stop() }
        RadaEngine.onCaptureChanged(false, emptyList(), reason)
        onStopped(reason)
    }

    // ---- audio: capture → DSP → playback ---------------------------------------------------

    private fun buildTrack(): AudioTrack? = runCatching {
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(fmt).setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }.onFailure { Log.w(TAG, "track", it) }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun buildRecord(uids: Collection<Int>): AudioRecord? {
        if (uids.isEmpty()) return null
        return runCatching {
            val cfg = AudioPlaybackCaptureConfiguration.Builder(projection).apply { uids.forEach { addMatchingUid(it) } }.build()
            val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(maxOf(min, BLOCK * 8) * 2)
                .setAudioPlaybackCaptureConfig(cfg).build()
                .also { if (it.state != AudioRecord.STATE_INITIALIZED) { it.release(); error("record not initialised") } }
        }.onFailure { Log.w(TAG, "record", it) }.getOrNull()
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = FloatArray(BLOCK * 2)
        while (running) {
            val rec = record
            if (rec == null) { Thread.sleep(20); continue }
            val n = runCatching { rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING) }.getOrDefault(-1)
            if (n <= 0) { Thread.sleep(5); continue }
            val frames = n / 2
            var sum = 0f
            for (i in 0 until n) sum += buf[i] * buf[i]
            if (sqrt(sum / n) > 1e-6f) lastSignal = System.currentTimeMillis()
            if (!RadaEngine.status.value.bypassed) process(buf, frames, RadaEngine.settings.value)
            track?.write(buf, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun process(buf: FloatArray, frames: Int, s: Settings) {
        if (s.levelling) leveller.process(buf, frames)
        // Crossfeed and speaker simulation are headphone effects: off when the output is a speaker.
        val onHeadphones = !DeviceTypes.isSpeaker(RadaEngine.status.value.output.kind)
        if (s.crossfeed && onHeadphones) crossfeed.process(buf, frames)
        if (s.virtualizer && onHeadphones) surround.process(buf, frames)
        if (s.reverb) {
            val key = "${s.room}:${s.roomSize}"
            if (key != roomKey) { roomKey = key; configureRoom(s) }
            reverb.process(buf, frames)
        }
        if (s.warmth) Warmth.process(buf, frames)
        if (s.mono) Mono.process(buf, frames)
        SafetyClip.process(buf, frames)
    }

    /** Room models: base room size, damping, wet level and pre-delay, scaled by the size slider. */
    private fun configureRoom(s: Settings) {
        data class Room(val size: Float, val damp: Float, val wet: Float, val preMs: Float)
        val r = when (s.room) {
            "Studio" -> Room(0.42f, 0.55f, 0.20f, 4f)
            "Concert" -> Room(0.83f, 0.32f, 0.30f, 22f)
            "Hall" -> Room(0.92f, 0.22f, 0.33f, 34f)
            else -> Room(0.68f, 0.45f, 0.26f, 12f)   // Club
        }
        val k = s.roomSize / 100f
        reverb.set(
            room = (r.size + (k - 0.45f) * 0.18f).coerceIn(0f, 0.98f),
            damp = r.damp,
            wet = r.wet * (0.7f + 0.6f * k),
            width = 1f,
            preDelayMs = r.preMs * (0.5f + k),
        )
    }

    // ---- finding apps, probing, muting -----------------------------------------------------

    private fun scanLoop() {
        while (running) {
            runCatching { scanOnce() }.onFailure { Log.w(TAG, "scan", it) }
            Thread.sleep(1500)
        }
    }

    private fun scanOnce() {
        val players = readPlayers() ?: return
        val me = Process.myUid()
        val active = players.filter { it.uid != me && it.uid >= 10000 && it.usage in CAPTURE_USAGES }
        val now = System.currentTimeMillis()

        // New apps: probe once, keep the ones that give audio.
        var changed = false
        active.map { it.uid }.distinct().filter { it !in confirmed && (rejectedUntil[it] ?: 0L) < now }.forEach { uid ->
            if (probe(uid)) { confirmed += uid; changed = true } else rejectedUntil[uid] = now + 20_000
        }
        if (changed) swapRecord()

        // Mute sessions of confirmed apps; release mutes for sessions that ended.
        val want = active.filter { it.uid in confirmed }.map { it.session }.filter { it > 0 }.toSet()
        synchronized(muted) {
            (muted.keys - want).forEach { muted.remove(it)?.release() }
            (want - muted.keys).forEach { id -> muteSession(id)?.let { muted[id] = it } }
        }

        // Safety: muted apps are playing but capture is silent → give them back their sound.
        if (muted.isNotEmpty() && now - lastSignal > 4000) {
            Log.w(TAG, "capture silent while apps are muted, stopping")
            stop("This phone returned silence from capture, so Advanced has stopped and every app is back on Standard.")
            return
        }
        RadaEngine.onCaptureChanged(true, active.filter { it.uid in confirmed }.map { nameFor(it.uid) }.distinct(), null)
    }

    private fun swapRecord() {
        val next = buildRecord(confirmed) ?: return
        runCatching { next.startRecording() }
        val old = record
        record = next
        old?.let { runCatching { it.stop() }; runCatching { it.release() } }
    }

    /** True when capture limited to this app returns sound within ~0.6 s. */
    private fun probe(uid: Int): Boolean {
        val rec = buildRecord(listOf(uid)) ?: return false
        return try {
            rec.startRecording()
            val buf = FloatArray(BLOCK * 2)
            var heard = false
            repeat(60) {
                if (heard) return@repeat
                val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n > 0) { var s = 0f; for (i in 0 until n) s += buf[i] * buf[i]; if (sqrt(s / n) > 1e-5f) heard = true }
            }
            heard
        } catch (t: Throwable) { false } finally { runCatching { rec.stop() }; rec.release() }
    }

    private fun muteSession(id: Int): DynamicsProcessing? = runCatching {
        DynamicsProcessing(2000, id, null).apply {
            setInputGainAllChannelsTo(-120f)
            setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -80f, -40f))
            enabled = true
        }
    }.onFailure { Log.w(TAG, "mute $id", it) }.getOrNull()

    private fun nameFor(uid: Int): String {
        val pm = ctx.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return "uid $uid"
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    /**
     * Reads playing players from `dumpsys audio`. The line format differs between Android versions,
     * so fields are matched individually: u/pid, state, usage and session.
     */
    private fun readPlayers(): List<Player>? {
        val text = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("dumpsys", "audio"))
            val out = p.inputStream.bufferedReader().readText(); p.waitFor(); out
        }.getOrNull()?.takeIf { "u/pid" in it } ?: ShizukuBridge.shellOutput("dumpsys audio") ?: return null
        val uidRe = Regex("""u/pid:(\d+)/""")
        val stateRe = Regex("""state:(\w+)""")
        val usageRe = Regex("""usage=(\w+)""")
        val sessionRe = Regex("""\bsession(?:Id)?[:=]\s*(\d+)""")
        return text.lineSequence()
            .filter { ("piid" in it || "AudioPlaybackConfiguration" in it) && "u/pid" in it }
            .mapNotNull { line ->
                val state = stateRe.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                if (state != "started") return@mapNotNull null
                val uid = uidRe.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                val session = sessionRe.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val usage = usageRe.find(line)?.groupValues?.get(1) ?: "USAGE_UNKNOWN"
                Player(uid, session, usage)
            }.toList()
    }
}
