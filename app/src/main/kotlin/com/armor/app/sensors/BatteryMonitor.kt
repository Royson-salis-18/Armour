package com.armor.app.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Reports battery level and charging state; the system re-sends ACTION_BATTERY_CHANGED whenever either changes. */
class BatteryMonitor(private val context: Context, private val onChange: (percent: Int, charging: Boolean) -> Unit) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            onChange(level * 100 / scale, charging)
        }
    }

    fun start() { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }

    fun stop() { runCatching { context.unregisterReceiver(receiver) } }
}
