package com.armor.app.sensors

import com.armor.app.requireService
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler

/**
 * Lists Bluetooth audio outputs that are currently connected. A helmet intercom pairs with the phone as an
 * ordinary headset, so this is how ARMOR notices one — it needs no Bluetooth permission. Whether a connected
 * device is an intercom or plain earphones is the rider's call (Settings → "I use a helmet intercom").
 */
class BluetoothAudioMonitor(private val context: Context, private val handler: Handler, private val onChange: (names: List<String>) -> Unit) {
    private val audio = context.requireService<AudioManager>()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = publish()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = publish()
    }

    fun start() {
        audio.registerAudioDeviceCallback(callback, handler)
        publish()
    }

    fun stop() = audio.unregisterAudioDeviceCallback(callback)

    private fun publish() = onChange(connectedNames(context))

    companion object {
        fun connectedNames(context: Context): List<String> =
            context.requireService<AudioManager>()
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                .map { it.productName.toString() }
                .distinct()
    }
}
