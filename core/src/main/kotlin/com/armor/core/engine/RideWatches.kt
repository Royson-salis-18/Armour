package com.armor.core.engine

import com.armor.core.geo.Geo
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix

/**
 * Riding without a proper break: after [limitMs] of riding (moving faster than [movingMps]) with no stop of at least
 * [breakMs], [onFix] returns true once, and then again after every further [limitMs].
 */
class FatigueWatch(
    private val limitMs: Long = 2 * 60 * 60_000L,
    private val breakMs: Long = 10 * 60_000L,
    private val movingMps: Double = 3.0,
) {
    private var ridingSinceMs: Long? = null
    private var stoppedSinceMs: Long? = null
    private var lastRemindAtRiddenMs = 0L
    private var riddenMs = 0L
    private var lastT: Long? = null

    fun onFix(fix: LocationFix): Boolean {
        val prev = lastT
        lastT = fix.timeMs
        if (fix.speedMps >= movingMps) {
            stoppedSinceMs = null
            if (ridingSinceMs == null) ridingSinceMs = fix.timeMs
            if (prev != null) riddenMs += (fix.timeMs - prev).coerceIn(0, 60_000)
        } else {
            val since = stoppedSinceMs ?: fix.timeMs.also { stoppedSinceMs = it }
            if (fix.timeMs - since >= breakMs) { riddenMs = 0; lastRemindAtRiddenMs = 0; ridingSinceMs = null }
        }
        if (riddenMs - lastRemindAtRiddenMs >= limitMs) { lastRemindAtRiddenMs = riddenMs; return true }
        return false
    }

    val riddenSinceBreakMs: Long get() = riddenMs
}

/**
 * Low battery: a phone that dies takes crash detection and location sharing with it, so the group should know.
 * [onLevel] returns RAISE once when the level falls to [lowPercent] or below while not charging, and CLEAR once it
 * is charging or back above [clearPercent].
 */
class BatteryWatch(private val lowPercent: Int = 15, private val clearPercent: Int = 25) {
    enum class Change { RAISE, CLEAR }
    var low = false
        private set

    fun onLevel(percent: Int, charging: Boolean): Change? = when {
        !low && !charging && percent <= lowPercent -> { low = true; Change.RAISE }
        low && (charging || percent >= clearPercent) -> { low = false; Change.CLEAR }
        else -> null
    }
}

/**
 * Safe arrival: the rider (or lead) sets a destination and an expected arrival time. Reaching within [arriveM] of the
 * destination reports ARRIVED once; still not there [graceMs] after the expected time reports LATE once, so the host
 * can ask the rider and tell their family.
 */
class ArrivalWatch(private val arriveM: Double = 250.0, private val graceMs: Long = 20 * 60_000L) {
    enum class Event { ARRIVED, LATE }
    data class Plan(val destination: LatLon, val label: String, val expectedAtMs: Long)

    var plan: Plan? = null
        private set
    var arrived = false
        private set
    private var lateReported = false

    fun set(destination: LatLon, label: String, expectedAtMs: Long) {
        plan = Plan(destination, label, expectedAtMs); arrived = false; lateReported = false
    }

    fun clear() { plan = null; arrived = false; lateReported = false }

    fun check(position: LatLon?, nowMs: Long): Event? {
        val p = plan ?: return null
        if (arrived) return null
        if (position != null && Geo.distanceM(position, p.destination) <= arriveM) { arrived = true; return Event.ARRIVED }
        if (!lateReported && nowMs > p.expectedAtMs + graceMs) { lateReported = true; return Event.LATE }
        return null
    }
}
