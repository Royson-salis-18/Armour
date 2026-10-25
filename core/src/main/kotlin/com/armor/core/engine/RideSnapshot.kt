package com.armor.core.engine

import com.armor.core.comms.VoiceRoute
import com.armor.core.group.Cluster
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.QuickStatus
import com.armor.core.model.SafetyCode
import com.armor.core.safety.SafetyState

data class RiderView(
    val id: String,
    val name: String,
    val position: LatLon,
    val speedMps: Double,
    val headingDeg: Double?,
    val accuracyM: Double,
    val isSelf: Boolean,
    /** Not heard from recently; position is the last known one. */
    val isStale: Boolean,
    val isOutlier: Boolean,
    val status: QuickStatus?,
    val safety: SafetyCode,
    val ageMs: Long,
    val distanceFromSelfM: Double?,
    val bearingFromSelfDeg: Double?,
)

data class IncomingAlert(
    val alert: GroupAlert,
    val acknowledgedByMe: Boolean,
    val distanceM: Double?,
    val bearingDeg: Double?,
)

/** Everything the UI and the alert layer need, rebuilt on each tick. */
data class RideSnapshot(
    val nowMs: Long,
    val riders: List<RiderView>,
    val clusters: List<Cluster>,
    val mainClusterIndex: Int?,
    val linkDistanceM: Double,
    val assessment: OutlierAssessment,
    val safety: SafetyState,
    val stationaryForMs: Long,
    val gpsLost: Boolean,
    val quickStatus: QuickStatus?,
    val incomingAlerts: List<IncomingAlert>,
    val voice: VoiceRoute,
    val online: Boolean,
) {
    val self: RiderView? get() = riders.firstOrNull { it.isSelf }

    /** True whenever the rider must be interrupted: a prompt, a countdown, an SOS, or a group member needing help. */
    val needsAttention: Boolean
        get() = (safety !is SafetyState.Normal && safety !is SafetyState.Acknowledged) ||
            incomingAlerts.any { !it.acknowledgedByMe && it.alert.ackedById == null }
}
