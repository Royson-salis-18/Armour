package com.armor.core.motion

import com.armor.core.geo.Geo
import com.armor.core.model.LocationFix
import kotlin.math.max

data class StationaryConfig(
    /** Floor for how far a fix may wander from the anchor and still count as "same spot". */
    val minRadiusM: Double = 20.0,
    /** The radius also grows with reported GPS inaccuracy so noisy fixes don't read as movement. */
    val accuracyFactor: Double = 1.5,
    /** At or above this ground speed the rider is moving regardless of position jitter. */
    val movingSpeedMps: Double = 2.0,
    /** No fix for this long means GPS is lost and stationarity is unknown (not "stationary"). */
    val gpsStaleMs: Long = 10_000,
)

/**
 * Decides whether the rider is stopped, using an anchor-and-radius rule rather than raw speed,
 * which is unreliable at walking pace. A stop is measured from the moment the anchor was set.
 * Leaving the radius only counts as movement on two consecutive fixes, so one bad fix is ignored.
 */
class StationaryDetector(private val config: StationaryConfig = StationaryConfig()) {
    private var anchor: LocationFix? = null
    private var lastFix: LocationFix? = null
    private var moving = false
    private var pendingBreak = false

    fun onFix(fix: LocationFix) {
        lastFix = fix
        val a = anchor
        if (a == null) {
            anchor = fix
            moving = fix.speedMps >= config.movingSpeedMps
            return
        }
        val radius = max(config.minRadiusM, config.accuracyFactor * max(fix.accuracyM, a.accuracyM))
        val dist = Geo.distanceM(a.position, fix.position)
        when {
            fix.speedMps >= config.movingSpeedMps -> {
                anchor = fix; moving = true; pendingBreak = false
            }
            dist > radius -> {
                if (pendingBreak) { anchor = fix; moving = true; pendingBreak = false } else pendingBreak = true
            }
            else -> {
                pendingBreak = false; moving = false
            }
        }
    }

    fun gpsLost(nowMs: Long): Boolean {
        val f = lastFix ?: return true
        return nowMs - f.timeMs > config.gpsStaleMs
    }

    /** True only with a fresh fix that shows movement. */
    fun isMoving(nowMs: Long): Boolean = !gpsLost(nowMs) && moving

    /** How long the rider has been in the same spot; 0 if moving or if GPS is lost. */
    fun stationaryForMs(nowMs: Long): Long {
        val a = anchor ?: return 0
        if (gpsLost(nowMs) || moving) return 0
        return (nowMs - a.timeMs).coerceAtLeast(0)
    }

    fun reset() {
        anchor = null; lastFix = null; moving = false; pendingBreak = false
    }
}
