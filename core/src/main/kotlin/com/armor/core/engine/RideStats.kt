package com.armor.core.engine

import com.armor.core.geo.Geo
import com.armor.core.model.LocationFix

/** Totals for the ride so far, shown on the ride screen. */
data class RideSummary(
    val distanceM: Double,
    val elapsedMs: Long,
    val movingMs: Long,
    val maxSpeedMps: Double,
) {
    val avgMovingSpeedMps: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0

    companion object { val EMPTY = RideSummary(0.0, 0L, 0L, 0.0) }
}

/**
 * Accumulates distance, moving time and top speed from GPS fixes. GPS jitter would otherwise add phantom distance while
 * stopped, so poor fixes are ignored and a step only counts once it is clearly more than the position noise.
 */
class RideStats(private val maxAccuracyM: Double = 30.0, private val movingMps: Double = 1.0, private val maxPlausibleMps: Double = 90.0) {
    private var startMs: Long? = null
    private var prev: LocationFix? = null
    private var distanceM = 0.0
    private var movingMs = 0L
    private var maxSpeed = 0.0

    fun onFix(fix: LocationFix) {
        if (fix.accuracyM > maxAccuracyM) return
        if (startMs == null) startMs = fix.timeMs
        val p = prev
        if (p == null) { prev = fix; return }
        val dt = fix.timeMs - p.timeMs
        if (dt <= 0) return
        val step = Geo.distanceM(p.position, fix.position)
        val noise = maxOf(p.accuracyM, fix.accuracyM) * 0.5
        val implied = step / (dt / 1000.0)
        if (implied <= maxPlausibleMps && step > noise && fix.speedMps >= movingMps) {
            distanceM += step
            movingMs += dt
        }
        if (fix.speedMps <= maxPlausibleMps) maxSpeed = maxOf(maxSpeed, fix.speedMps)
        prev = fix
    }

    fun summary(nowMs: Long): RideSummary {
        val s = startMs ?: return RideSummary.EMPTY
        return RideSummary(distanceM, (nowMs - s).coerceAtLeast(0), movingMs, maxSpeed)
    }
}
