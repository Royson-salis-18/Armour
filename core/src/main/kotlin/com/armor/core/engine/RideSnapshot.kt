package com.armor.core.engine

import com.armor.core.comms.VoiceRoute
import com.armor.core.group.Cluster
import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.StopReason
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
) {
    /**
     * True while this alert should take over the screen. Most alerts stop needing attention once *anyone* has answered
     * them; a team stop is addressed to everyone, so it stays until this rider has confirmed.
     */
    val needsMe: Boolean
        get() = !acknowledgedByMe && !alert.resolved &&
            (alert.kind == com.armor.core.model.AlertKind.TEAM_STOP || alert.ackedById == null)
}

/** The lead's call for everyone to stop, with who has and hasn't yet. */
data class TeamStopView(
    val reason: StopReason,
    val calledByName: String,
    val calledByMe: Boolean,
    val sinceMs: Long,
    val stopped: List<String>,
    val waiting: List<String>,
)

/** This rider's open non-emergency request to the group; [ackedByName] is who said they are coming. */
data class HelpRequest(val ackedByName: String?)

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
    val stats: RideSummary = RideSummary.EMPTY,
    val help: HelpRequest? = null,
    val leadId: String? = null,
    val teamStop: TeamStopView? = null,
    val messages: List<ChatMessage> = emptyList(),
) {
    val self: RiderView? get() = riders.firstOrNull { it.isSelf }
    val isLead: Boolean get() = leadId != null && leadId == self?.id

    /** True whenever the rider must be interrupted: a prompt, a countdown, an SOS, or a group member needing help. */
    val needsAttention: Boolean
        get() = (safety !is SafetyState.Normal && safety !is SafetyState.Acknowledged) ||
            incomingAlerts.any { it.needsMe }
}
