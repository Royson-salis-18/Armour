package com.armor.app.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.armor.app.ArmorApp

/**
 * Picks a ride back up after the phone restarts or the app is updated, but only if a ride was on when it happened.
 * With no ride on, nothing runs: no detection, no alerts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system broadcasts only; anything else sent here is ignored.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = ArmorApp.instance.prefs
        val located = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        // Only a ride that was genuinely under way is carried on; nothing is armed just because the phone started.
        if (prefs.rideActive && located) RideService.start(context, demo = false)
    }
}
