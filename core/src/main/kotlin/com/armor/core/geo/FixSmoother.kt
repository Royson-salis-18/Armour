package com.armor.core.geo

import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import kotlin.math.cos
import kotlin.math.max

/**
 * Steadies raw GPS before anything else sees it. Standing still, a phone's fixes wander 5-30 m around the true spot
 * (worse indoors, under trees or next to buildings) and report a small non-zero speed; drawn as they come, the rider's
 * dot "moves" while parked and the trail scribbles.
 *
 * Three rules:
 *  - Parked: while the rider is not really moving, the position is held at a slowly refined anchor and speed is 0.
 *    A fix only breaks the hold when it is clearly outside its own error circle, or the rider is really moving.
 *  - Moving: a one-state Kalman filter per axis blends each fix with the prediction, trusting accurate fixes more.
 *  - Junk: a fix far worse than the current estimate, arriving soon after a good one, is ignored.
 */
class FixSmoother(
    /** Below this reported speed (m/s, about 5 km/h) the rider counts as not moving. */
    private val stillSpeedMps: Double = 1.4,
    /** Fixes worse than this are dropped while a good recent fix exists. */
    private val junkAccuracyM: Double = 60.0,
) {
    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1.0 // m², < 0 until the first fix
    private var lastMs = 0L
    private var anchor: LatLon? = null
    private var anchorAccuracy = 0.0
    private var lastGoodMs = 0L

    fun reset() { variance = -1.0; anchor = null }

    fun smooth(fix: LocationFix): LocationFix? {
        val acc = max(fix.accuracyM, 3.0)
        if (variance < 0) return start(fix, acc)

        // Junk: much worse than what we have, and a good fix was seen in the last 30 s.
        if (acc > junkAccuracyM && acc > 3 * kotlin.math.sqrt(variance) && fix.timeMs - lastGoodMs < 30_000) return null
        if (acc <= junkAccuracyM) lastGoodMs = fix.timeMs

        val dt = max(0.0, (fix.timeMs - lastMs) / 1000.0)
        lastMs = fix.timeMs
        val moving = fix.speedMps >= stillSpeedMps

        val a = anchor
        if (a != null) {
            val drift = Geo.distanceM(a, fix.position)
            // Still parked: the fix is inside the noise around the anchor. Refine the anchor with good fixes only.
            if (!moving && drift < max(15.0, 1.5 * max(acc, anchorAccuracy))) {
                if (acc < anchorAccuracy * 1.2) {
                    // Creep toward better fixes, at most a metre and a half per fix, so the dot never visibly jumps.
                    val w = (anchorAccuracy * anchorAccuracy) / (anchorAccuracy * anchorAccuracy + acc * acc) * 0.3
                    val step = drift * w
                    val f = if (step > 1.5) w * 1.5 / step else w
                    anchor = LatLon(a.lat + (fix.position.lat - a.lat) * f, a.lon + (fix.position.lon - a.lon) * f)
                    anchorAccuracy = max(3.0, (anchorAccuracy + acc) / 2)
                }
                val held = anchor!!
                lat = held.lat; lon = held.lon; variance = anchorAccuracy * anchorAccuracy
                return fix.copy(position = held, speedMps = 0.0, accuracyM = anchorAccuracy)
            }
            anchor = null // really moving again
        }

        // Kalman step: uncertainty grows with time and speed, then the fix pulls the estimate in.
        val q = max(fix.speedMps, 1.0).let { it * it }
        variance += dt * q
        val k = variance / (variance + acc * acc)
        val mPerDegLat = Math.toRadians(1.0) * Geo.EARTH_RADIUS_M
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(lat))
        // Work in metres so the gain means the same thing north-south and east-west.
        val dy = (fix.position.lat - lat) * mPerDegLat
        val dx = (fix.position.lon - lon) * mPerDegLon
        lat += k * dy / mPerDegLat
        lon += k * dx / mPerDegLon
        variance *= (1 - k)

        if (!moving) { anchor = LatLon(lat, lon); anchorAccuracy = acc }
        return fix.copy(position = LatLon(lat, lon), speedMps = if (moving) fix.speedMps else 0.0, accuracyM = kotlin.math.sqrt(variance))
    }

    private fun start(fix: LocationFix, acc: Double): LocationFix {
        lat = fix.position.lat; lon = fix.position.lon; variance = acc * acc; lastMs = fix.timeMs; lastGoodMs = fix.timeMs
        if (fix.speedMps < stillSpeedMps) { anchor = fix.position; anchorAccuracy = acc }
        return if (fix.speedMps < stillSpeedMps) fix.copy(speedMps = 0.0) else fix
    }
}
