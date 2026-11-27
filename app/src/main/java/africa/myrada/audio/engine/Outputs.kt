package africa.myrada.audio.engine

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** The output RADA is tuning for. [key] is what profiles are saved under. */
data class OutputInfo(
    val key: String,
    val name: String,
    val mode: String,
    val bluetooth: Boolean,
)

object Outputs {
    val SPEAKER = OutputInfo("speaker", "Phone speaker", "Phone speaker", false)

    /**
     * Picks the output media most likely plays through. Android gives normal apps no public way to
     * read the exact route, so connected headphones win over the speaker, as Android itself does.
     */
    fun current(am: AudioManager): OutputInfo {
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val order = mutableListOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
        if (Build.VERSION.SDK_INT >= 31) order.add(1, 26) // AudioDeviceInfo.TYPE_BLE_HEADSET
        for (type in order) {
            val d = devices.firstOrNull { it.type == type } ?: continue
            return describe(d)
        }
        return SPEAKER
    }

    private fun describe(d: AudioDeviceInfo): OutputInfo {
        val product = d.productName?.toString()?.takeIf { it.isNotBlank() }
        return when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 ->
                OutputInfo("bt:${product ?: "unknown"}", product ?: "Bluetooth audio", "Wireless", true)
            AudioDeviceInfo.TYPE_USB_HEADSET ->
                OutputInfo("usb:${product ?: "headset"}", product ?: "USB headphones", "USB digital", false)
            AudioDeviceInfo.TYPE_USB_DEVICE ->
                OutputInfo("usb:${product ?: "dac"}", product ?: "USB DAC", "USB Hi-Fi", false)
            else -> OutputInfo("wired", "Wired headphones", "Direct", false)
        }
    }
}
