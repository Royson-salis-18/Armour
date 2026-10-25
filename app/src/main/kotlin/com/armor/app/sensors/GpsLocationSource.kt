package com.armor.app.sensors

import com.armor.app.requireService
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.armor.core.geo.Geo
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix

/**
 * Raw GPS via the platform LocationManager: works with no internet and no Play Services. Fixes are stamped with
 * the ride clock at the moment they arrive.
 */
class GpsLocationSource(
    context: Context,
    private val clock: RideClock,
    private val looper: Looper,
    private val onFix: (LocationFix) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private val manager = context.requireService<LocationManager>()
    private var previous: LocationFix? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = clock.now()
            val pos = LatLon(location.latitude, location.longitude)
            val speed = when {
                location.hasSpeed() -> location.speed.toDouble()
                else -> previous?.let { p ->
                    val dt = (now - p.timeMs) / 1000.0
                    if (dt > 0.2) Geo.distanceM(p.position, pos) / dt else 0.0
                } ?: 0.0
            }
            val fix = LocationFix(
                timeMs = now,
                position = pos,
                speedMps = speed,
                bearingDeg = if (location.hasBearing()) location.bearing.toDouble() else null,
                accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else 50.0,
            )
            previous = fix
            onFix(fix)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) onProblem("GPS is switched off. Turn on Location to keep tracking.")
        }

        @Deprecated("Required on API < 29")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun start() {
        try {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) onProblem("GPS is switched off. Turn on Location to keep tracking.")
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener, looper)
        } catch (e: SecurityException) {
            onProblem("Location permission is missing.")
        } catch (e: IllegalArgumentException) {
            onProblem("This device has no GPS.")
        }
    }

    fun stop() = manager.removeUpdates(listener)

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
