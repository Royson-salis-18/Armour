package com.armor.core.model

data class LatLon(val lat: Double, val lon: Double)

/**
 * A GPS fix. [timeMs] is the wall-clock time (epoch ms) at which the fix was obtained, in the
 * same time base the engine is ticked with; it is also what peers use to judge staleness.
 */
data class LocationFix(
    val timeMs: Long,
    val position: LatLon,
    val speedMps: Double,
    val bearingDeg: Double?,
    val accuracyM: Double,
)

/** Rider-declared stop reasons. An intentional stop pauses the "are you OK?" check for a grace period. */
enum class QuickStatus(val label: String, val intentionalStop: Boolean) {
    FUEL("Fuel stop", true),
    BREAK("Break", true),
    MECHANICAL("Mechanical issue", true),
    TRAFFIC("Traffic / waiting", true),
    PHOTO("Photo stop", true),
}

/** Coarse safety state that is shared with the group (full detail stays on the rider's phone). */
enum class SafetyCode { NORMAL, CHECKING, ALERTED, CRASH, SOS }

/** What a rider publishes to the group about themselves. */
data class RiderReport(
    val id: String,
    val name: String,
    val fix: LocationFix,
    val status: QuickStatus?,
    val safety: SafetyCode,
)

enum class AlertKind { STATIONARY, CRASH, SOS }

/**
 * A group-visible alert raised by [riderId]. Other riders acknowledge it; the first
 * acknowledgement stops the raising rider's SOS escalation timer.
 */
data class GroupAlert(
    val id: String,
    val riderId: String,
    val riderName: String,
    val kind: AlertKind,
    val position: LatLon?,
    val raisedAtMs: Long,
    val ackedById: String? = null,
    val ackedByName: String? = null,
    val resolved: Boolean = false,
)

/** Where the rider stands relative to the group, as seen from the rider's own phone. */
enum class OutlierAssessment {
    /** Fresh peer data exists and this rider is part of the main group. */
    IN_CLUSTER,

    /** Fresh peer data exists and this rider is separated from the main group. */
    OUTLIER,

    /** No fresh peer data (solo ride, or offline): group position cannot be judged. */
    UNKNOWN,
}

enum class SosReason { MANUAL, STATIONARY_UNACKNOWLEDGED, CRASH_NO_RESPONSE }
