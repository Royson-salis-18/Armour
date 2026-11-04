package com.armor.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import com.armor.app.data.Settings

/** One thing that must be true before a ride can start. */
internal class GateItem(val key: String, val title: String, val fix: String, val ok: Boolean)

/**
 * The safety protocols a ride needs before it can be switched on. A ride that cannot text for help, cannot see where it
 * is, or has nobody to tell is not a protected ride, so it does not start. Shared by the start screen (to show the
 * list) and the ride service (which refuses to start when any of these fail).
 */
internal object RideGate {
    fun items(context: Context, settings: Settings, contactCount: Int): List<GateItem> {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val gpsOn = runCatching { context.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)
        return buildList {
            add(GateItem("loc", "Location permission", "Allow location so ARMOR can follow the ride.", granted(Manifest.permission.ACCESS_FINE_LOCATION)))
            add(GateItem("gps", "GPS switched on", "Turn on Location in your phone's quick settings.", gpsOn))
            add(GateItem("sms", "SOS texts allowed", "Allow SMS so help can be reached with no internet.", granted(Manifest.permission.SEND_SMS)))
            if (settings.autoCall) add(GateItem("call", "SOS calls allowed", "Allow calls, or turn off Call after an SOS in Settings.", granted(Manifest.permission.CALL_PHONE)))
            add(GateItem("notif", "Alerts allowed", "Allow notifications so a crash alarm can reach you.", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)))
            add(GateItem("who", "Someone to alert", "Add an emergency contact in Settings, or join a ride group.", contactCount > 0 || settings.emergencyNumber.isNotBlank() || settings.syncConfigured))
        }
    }

    fun missing(context: Context, settings: Settings, contactCount: Int) = items(context, settings, contactCount).filter { !it.ok }

    /** Permissions the system dialog can grant right now. */
    fun requestable(context: Context): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
}
