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

/** HELP is a non-emergency request (fuel, puncture, a hand): it never escalates to SOS on its own. */
/** TEAM_STOP is the lead calling the whole group to stop (fuel, break...); [GroupAlert.detail] holds the [StopReason] name. */
/**
 * HAZARD, LOW_BATTERY and REGROUP are informational: they show on the map and in the feed but never take over the
 * screen. For HAZARD the [GroupAlert.detail] is a [HazardType] name, for LOW_BATTERY the battery percent, for REGROUP
 * an optional place label.
 */
enum class AlertKind { STATIONARY, CRASH, SOS, HELP, TEAM_STOP, HAZARD, LOW_BATTERY, REGROUP;
    val informational: Boolean get() = this == HAZARD || this == LOW_BATTERY || this == REGROUP
}

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
    val detail: String? = null,
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

/** Why the lead is calling a stop. Each maps to the rider-status that pauses the "are you okay?" check while stopped. */
enum class StopReason(val label: String, val status: QuickStatus) {
    FUEL("Fuel", QuickStatus.FUEL),
    BREAK("Break", QuickStatus.BREAK),
    FOOD("Food", QuickStatus.BREAK),
    MECHANICAL("Mechanical", QuickStatus.MECHANICAL),
    WEATHER("Weather", QuickStatus.BREAK),
    OTHER("Other", QuickStatus.TRAFFIC),
}

data class ChatMessage(val id: String, val riderId: String, val riderName: String, val text: String, val timeMs: Long)

/** Things a rider can flag on the road for everyone behind them. */
enum class HazardType(val label: String) {
    POTHOLE("Pothole"),
    OIL("Oil or gravel"),
    ACCIDENT("Accident"),
    ANIMAL("Animal on road"),
    POLICE("Police check"),
    ROADWORK("Roadwork"),
}

/** What first responders need to know; shown on the SOS screen and sent as a second text with an SOS. */
data class MedicalInfo(
    val bloodGroup: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val medications: String = "",
) {
    val isEmpty: Boolean get() = listOf(bloodGroup, allergies, conditions, medications).all { it.isBlank() }
}
