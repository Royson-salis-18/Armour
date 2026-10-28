package com.armor.app.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.armor.app.ArmorApp

/**
 * Starts ARMOR's protection by itself after the phone boots or the app is updated, when the rider has turned on
 * "Keep protecting me in the background". Nothing is asked and nothing is shown beyond the ongoing notification.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = ArmorApp.instance.prefs
        val located = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (prefs.current.alwaysOn && located) RideService.start(context, demo = false)
    }
}
