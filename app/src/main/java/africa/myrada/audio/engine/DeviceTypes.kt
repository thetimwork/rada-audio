package africa.myrada.audio.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject

/**
 * What kind of device each Bluetooth output is: speaker, headphones or earbuds.
 *
 * Android reports every Bluetooth audio device as the same generic type, so Rada works it out:
 * 1. What you saved for this device, if you have (the New device card). This always wins.
 * 2. Otherwise a suggestion from what the device says it is when it pairs (its Bluetooth Class of
 *    Device: loudspeaker, headphones, headset...), with the name only to break a tie.
 * Everything here is local and instant: no network, no database yet.
 */
object DeviceTypes {
    const val SPEAKER = "spk"
    const val HEADPHONES = "head"
    const val EARBUDS = "bud"

    data class Saved(val name: String, val kind: String)
    data class Suggestion(val kind: String, val reason: String)

    private var prefs: SharedPreferences? = null
    private fun prefs(ctx: Context) = prefs ?: ctx.applicationContext.getSharedPreferences("rada_devices", Context.MODE_PRIVATE).also { prefs = it }

    fun saved(ctx: Context, id: String): Saved? = runCatching {
        val o = JSONObject(prefs(ctx).getString(id, null) ?: return null)
        Saved(o.getString("name"), o.getString("kind"))
    }.getOrNull()

    fun save(ctx: Context, id: String, name: String, kind: String) {
        prefs(ctx).edit().putString(id, JSONObject().put("name", name).put("kind", kind).toString()).apply()
    }

    private val BUD_NAME = Regex("bud|pods|tws|wf-|\\bear|in-ear|beam", RegexOption.IGNORE_CASE)
    private val SPEAKER_NAME = Regex("speaker|sound ?bar|boom|flip|charge|clip|go ?\\d|sphere|box|party|pulse|xtreme|soundlink|wonderboom", RegexOption.IGNORE_CASE)

    /**
     * A suggestion for a device you have not saved yet. [bleType] is AudioDeviceInfo's type for
     * LE Audio devices (27 = speaker, 26 = headset) when Android reports one.
     */
    @SuppressLint("MissingPermission")
    fun suggest(ctx: Context, address: String?, name: String, bleType: Int? = null): Suggestion {
        if (bleType == 27) return Suggestion(SPEAKER, "It reports itself as a speaker.")
        val cod = classOf(ctx, address)
        when (cod) {
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> return Suggestion(SPEAKER, "It reports itself as a speaker.")
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ->
                return if (BUD_NAME.containsMatchIn(name)) Suggestion(EARBUDS, "It reports headphones, and the name suggests earbuds.")
                else Suggestion(HEADPHONES, "It reports itself as headphones.")
        }
        // Headset, hands-free or nothing useful: the name breaks the tie.
        return when {
            BUD_NAME.containsMatchIn(name) -> Suggestion(EARBUDS, "The name suggests earbuds. Check this is right.")
            SPEAKER_NAME.containsMatchIn(name) -> Suggestion(SPEAKER, "The name suggests a speaker. Check this is right.")
            else -> Suggestion(HEADPHONES, "It doesn't say clearly. Check this is right.")
        }
    }

    /** The paired device's Class of Device (minor class), or null if Android won't share it. */
    @SuppressLint("MissingPermission")
    private fun classOf(ctx: Context, address: String?): Int? {
        if (address.isNullOrBlank()) return null
        if (Build.VERSION.SDK_INT >= 31 &&
            ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return null
            adapter.getRemoteDevice(address).bluetoothClass?.deviceClass
        } catch (e: SecurityException) { null } catch (e: IllegalArgumentException) { null }
    }

    /** Crossfeed and speaker simulation are for headphones only. */
    fun isSpeaker(kind: String) = kind == SPEAKER || kind == "phone"
}
