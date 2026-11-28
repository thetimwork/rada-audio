package africa.myrada.audio.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** The output Rada is tuning for. [key] is what profiles are saved under. */
data class OutputInfo(
    val key: String,
    val name: String,
    val mode: String,
    val bluetooth: Boolean,
    /** What it is: "spk", "head", "bud", "dac" or "phone". Picks the picture and which effects apply. */
    val kind: String = "head",
    /** Bluetooth devices only: where Rada remembers its name and type ("bt:" + address). */
    val id: String? = null,
    /** True once you have saved this device's name and type. */
    val known: Boolean = true,
    /** Bluetooth devices only: the name Bluetooth gives, before any rename in Rada. */
    val btName: String? = null,
    /** Bluetooth devices only: Rada's guess at the type, and why. */
    val suggestion: DeviceTypes.Suggestion? = null,
)

object Outputs {
    val SPEAKER = OutputInfo("speaker", "Phone speaker", "Phone speaker", false, kind = "phone")

    /**
     * Picks the output media most likely plays through. Android gives normal apps no public way to
     * read the exact route, so connected headphones win over the speaker, as Android itself does.
     */
    fun current(ctx: Context, am: AudioManager): OutputInfo {
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val order = mutableListOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
        if (Build.VERSION.SDK_INT >= 31) { order.add(1, 26); order.add(2, 27) } // AudioDeviceInfo.TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER
        for (type in order) {
            val d = devices.firstOrNull { it.type == type } ?: continue
            return describe(ctx, d)
        }
        return SPEAKER
    }

    private fun describe(ctx: Context, d: AudioDeviceInfo): OutputInfo {
        val product = d.productName?.toString()?.takeIf { it.isNotBlank() }
        return when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27 -> {
                // The saved-settings key stays on Android's product name; the label uses the name you
                // saved in Rada, else the Bluetooth name (your rename, if you gave it one).
                val bt = bluetoothName(ctx, d.address) ?: product ?: "Bluetooth audio"
                val id = d.address?.takeIf { it.isNotBlank() }?.let { "bt:$it" } ?: "bt:${product ?: "unknown"}"
                val saved = DeviceTypes.saved(ctx, id)
                val guess = DeviceTypes.suggest(ctx, d.address, bt, if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) null else d.type)
                OutputInfo("bt:${product ?: "unknown"}", saved?.name ?: bt, "Wireless", true,
                    kind = saved?.kind ?: guess.kind, id = id, known = saved != null, btName = bt, suggestion = guess)
            }
            AudioDeviceInfo.TYPE_USB_HEADSET ->
                OutputInfo("usb:${product ?: "headset"}", product ?: "USB headphones", "USB digital", false, kind = "head")
            AudioDeviceInfo.TYPE_USB_DEVICE ->
                OutputInfo("usb:${product ?: "dac"}", product ?: "USB DAC", "USB Hi-Fi", false, kind = "dac")
            else -> OutputInfo("wired", "Wired headphones", "Direct", false)
        }
    }

    /** Name of the paired Bluetooth device at this address: its alias (your rename) or its own name. */
    @SuppressLint("MissingPermission")
    private fun bluetoothName(ctx: Context, address: String?): String? {
        if (address.isNullOrBlank()) return null
        if (Build.VERSION.SDK_INT >= 31 &&
            ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return null
            val dev = adapter.getRemoteDevice(address)
            val alias = if (Build.VERSION.SDK_INT >= 30) dev.alias else null
            (alias ?: dev.name)?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) { null } catch (e: IllegalArgumentException) { null }
    }
}
