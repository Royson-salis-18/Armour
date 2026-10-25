package com.armor.core.safety

import com.armor.core.model.AlertKind
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.SafetyCode
import com.armor.core.model.SosReason

data class SafetyConfig(
    /** Stationary this long while separated from the group starts the "are you OK?" check. */
    val groupStationaryMs: Long = 90_000,
    /** Same, when group position is unknown (solo / offline). Longer, to ride out traffic lights. */
    val soloStationaryMs: Long = 300_000,
    val soloMonitoring: Boolean = true,
    /** Time the rider has to answer the check before the group is alerted. */
    val verifyTimeoutMs: Long = 30_000,
    /** Time the group has to acknowledge before SOS. */
    val groupAckTimeoutMs: Long = 60_000,
    /** Time the rider has to cancel after a crash before SOS. */
    val crashCountdownMs: Long = 30_000,
    /** After the rider says they're fine, don't ask again for this long. */
    val responseCooldownMs: Long = 300_000,
    /** A rider-declared stop (fuel, break...) pauses the check this long. */
    val intentionalStopGraceMs: Long = 900_000,
    /** An acknowledged alert is re-raised if the rider is still stuck after this long. */
    val ackExpiryMs: Long = 600_000,
    /** Sustained movement of this long counts as "the rider is on the move again". */
    val resumeMovingMs: Long = 8_000,
)

sealed interface SafetyState {
    val code: SafetyCode

    data object Normal : SafetyState {
        override val code get() = SafetyCode.NORMAL
    }

    /** Rider is prompted to confirm they are OK. */
    data class Verifying(val sinceMs: Long, val deadlineMs: Long) : SafetyState {
        override val code get() = SafetyCode.CHECKING
    }

    /** Rider didn't answer; the group has been alerted and has until [deadlineMs] to acknowledge. */
    data class GroupAlerted(val sinceMs: Long, val deadlineMs: Long, val alertId: String) : SafetyState {
        override val code get() = SafetyCode.ALERTED
    }

    /** A group member acknowledged; escalation is paused while they come to help. */
    data class Acknowledged(val sinceMs: Long, val expiresMs: Long, val byName: String, val alertId: String) : SafetyState {
        override val code get() = SafetyCode.ALERTED
    }

    /** Crash detected; the rider can cancel until [deadlineMs]. */
    data class CrashCountdown(val sinceMs: Long, val deadlineMs: Long, val priorAlertId: String?) : SafetyState {
        override val code get() = SafetyCode.CRASH
    }

    data class Sos(val sinceMs: Long, val reason: SosReason, val alertId: String) : SafetyState {
        override val code get() = SafetyCode.SOS
    }
}

/** What the world looks like at one tick, as far as the state machine cares. */
data class Observation(
    val assessment: OutlierAssessment,
    /** 0 when moving or when GPS is lost. */
    val stationaryForMs: Long,
    val moving: Boolean,
    /** Rider announced a stop (and the grace period hasn't run out). */
    val intentionalStop: Boolean,
)

enum class EventType {
    RIDE, STATIONARY_CHECK, RIDER_OK, RIDER_RESUMED, GROUP_ALERT, GROUP_ACK, GROUP_REACHED_RIDER,
    CRASH, CRASH_DISMISSED, CRASH_CANCELLED, SOS_SENT, SOS_CANCELLED, STATUS, SYSTEM,
}

/** Side effects the host must carry out. The machine itself performs none. */
sealed interface SafetyEffect {
    data class RaiseAlert(val alertId: String, val kind: AlertKind, val raisedAtMs: Long) : SafetyEffect
    data class ResolveAlert(val alertId: String) : SafetyEffect
    data class DispatchSos(val reason: SosReason, val alertId: String) : SafetyEffect
    data class CancelSos(val alertId: String) : SafetyEffect

    /** Audit-trail entry explaining *why* a transition happened. */
    data class Log(val type: EventType, val detail: String) : SafetyEffect
}

/**
 * The deterministic core of ARMOR's emergency handling. All escalation decisions are made here, by
 * fixed rules and timers — no learned model participates.
 *
 * ```
 * Normal ──stationary & separated──▶ Verifying ──no answer──▶ GroupAlerted ──no ack──▶ Sos
 *   │                                   │ ok/moves/regroups       │ ack          ▲
 *   │                                   ▼                         ▼              │
 *   │                                Normal                  Acknowledged        │
 *   └──crash──▶ CrashCountdown ──────────────no cancel────────────────────────────┘
 * any state ──manual SOS──▶ Sos            Sos ──rider OK──▶ Normal
 * ```
 *
 * Not thread-safe; drive it from one thread with a monotonic [nowMs].
 */
class SafetyStateMachine(
    private val riderId: String,
    private val config: SafetyConfig = SafetyConfig(),
) {
    var state: SafetyState = SafetyState.Normal
        private set

    private var suppressUntilMs = 0L
    private var movingSinceMs: Long? = null

    fun onTick(nowMs: Long, obs: Observation): List<SafetyEffect> {
        movingSinceMs = if (obs.moving) movingSinceMs ?: nowMs else null
        val resumed = movingSinceMs?.let { nowMs - it >= config.resumeMovingMs } == true

        return when (val s = state) {
            SafetyState.Normal -> checkForStop(nowMs, obs)

            is SafetyState.Verifying -> when {
                resumed -> backToNormal(nowMs, "rider is moving again", EventType.RIDER_RESUMED, alertId = null)
                obs.assessment == OutlierAssessment.IN_CLUSTER -> backToNormal(nowMs, "rider is back with the group", EventType.GROUP_REACHED_RIDER, alertId = null)
                obs.intentionalStop -> backToNormal(nowMs, "rider declared a stop", EventType.RIDER_OK, alertId = null)
                nowMs >= s.deadlineMs -> alertGroup(nowMs, AlertKind.STATIONARY, "no answer to the check within ${config.verifyTimeoutMs / 1000}s")
                else -> emptyList()
            }

            is SafetyState.GroupAlerted -> when {
                resumed -> backToNormal(nowMs, "rider is moving again", EventType.RIDER_RESUMED, s.alertId)
                obs.assessment == OutlierAssessment.IN_CLUSTER -> backToNormal(nowMs, "the group reached the rider", EventType.GROUP_REACHED_RIDER, s.alertId)
                nowMs >= s.deadlineMs -> escalate(nowMs, SosReason.STATIONARY_UNACKNOWLEDGED, s.alertId, "no group member acknowledged within ${config.groupAckTimeoutMs / 1000}s")
                else -> emptyList()
            }

            is SafetyState.Acknowledged -> when {
                resumed -> backToNormal(nowMs, "rider is moving again", EventType.RIDER_RESUMED, s.alertId)
                obs.assessment == OutlierAssessment.IN_CLUSTER -> backToNormal(nowMs, "${s.byName} reached the rider", EventType.GROUP_REACHED_RIDER, s.alertId)
                nowMs >= s.expiresMs -> {
                    val fx = mutableListOf<SafetyEffect>(SafetyEffect.ResolveAlert(s.alertId))
                    fx += alertGroup(nowMs, AlertKind.STATIONARY, "acknowledged alert expired with the rider still stopped")
                    fx
                }
                else -> emptyList()
            }

            is SafetyState.CrashCountdown ->
                if (nowMs >= s.deadlineMs) {
                    escalate(nowMs, SosReason.CRASH_NO_RESPONSE, s.priorAlertId ?: newAlertId(nowMs), "no cancel within ${config.crashCountdownMs / 1000}s of the crash")
                } else emptyList()

            is SafetyState.Sos -> emptyList()
        }
    }

    /** The rider tapped "I'm OK" (or cancelled a countdown / SOS). */
    fun onRiderResponded(nowMs: Long): List<SafetyEffect> = when (val s = state) {
        SafetyState.Normal -> emptyList()
        is SafetyState.Verifying -> backToNormal(nowMs, "rider confirmed they are OK", EventType.RIDER_OK, null, cooldown = true)
        is SafetyState.GroupAlerted -> backToNormal(nowMs, "rider confirmed they are OK", EventType.RIDER_OK, s.alertId, cooldown = true)
        is SafetyState.Acknowledged -> backToNormal(nowMs, "rider confirmed they are OK", EventType.RIDER_OK, s.alertId, cooldown = true)
        is SafetyState.CrashCountdown -> backToNormal(nowMs, "rider cancelled the crash countdown", EventType.CRASH_CANCELLED, s.priorAlertId, cooldown = true)
        is SafetyState.Sos -> {
            state = SafetyState.Normal
            suppressUntilMs = nowMs + config.responseCooldownMs
            listOf(
                SafetyEffect.CancelSos(s.alertId),
                SafetyEffect.ResolveAlert(s.alertId),
                SafetyEffect.Log(EventType.SOS_CANCELLED, "rider cancelled the SOS"),
            )
        }
    }

    fun onGroupAcknowledged(nowMs: Long, alertId: String, byName: String): List<SafetyEffect> {
        val s = state as? SafetyState.GroupAlerted ?: return emptyList()
        if (s.alertId != alertId) return emptyList()
        state = SafetyState.Acknowledged(nowMs, nowMs + config.ackExpiryMs, byName, s.alertId)
        return listOf(SafetyEffect.Log(EventType.GROUP_ACK, "$byName acknowledged the alert; SOS escalation paused"))
    }

    /** A confirmed crash (after the false-alarm filter). Overrides any stationary-check state. */
    fun onCrash(nowMs: Long, detail: String): List<SafetyEffect> {
        val prior = when (val s = state) {
            SafetyState.Normal -> null
            is SafetyState.Verifying -> null
            is SafetyState.GroupAlerted -> s.alertId
            is SafetyState.Acknowledged -> s.alertId
            is SafetyState.CrashCountdown, is SafetyState.Sos -> return emptyList()
        }
        state = SafetyState.CrashCountdown(nowMs, nowMs + config.crashCountdownMs, prior)
        return listOf(SafetyEffect.Log(EventType.CRASH, "crash detected ($detail); SOS in ${config.crashCountdownMs / 1000}s unless cancelled"))
    }

    fun onManualSos(nowMs: Long): List<SafetyEffect> {
        val alertId = when (val s = state) {
            is SafetyState.Sos -> return emptyList()
            is SafetyState.GroupAlerted -> s.alertId
            is SafetyState.Acknowledged -> s.alertId
            is SafetyState.CrashCountdown -> s.priorAlertId ?: newAlertId(nowMs)
            else -> newAlertId(nowMs)
        }
        return escalate(nowMs, SosReason.MANUAL, alertId, "rider pressed SOS")
    }

    private fun checkForStop(nowMs: Long, obs: Observation): List<SafetyEffect> {
        if (nowMs < suppressUntilMs || obs.intentionalStop) return emptyList()
        val threshold = when (obs.assessment) {
            OutlierAssessment.OUTLIER -> config.groupStationaryMs
            OutlierAssessment.UNKNOWN -> if (config.soloMonitoring) config.soloStationaryMs else return emptyList()
            OutlierAssessment.IN_CLUSTER -> return emptyList()
        }
        if (obs.stationaryForMs < threshold) return emptyList()
        state = SafetyState.Verifying(nowMs, nowMs + config.verifyTimeoutMs)
        val why = if (obs.assessment == OutlierAssessment.OUTLIER) "separated from the group" else "group position unknown"
        return listOf(SafetyEffect.Log(EventType.STATIONARY_CHECK, "stationary ${obs.stationaryForMs / 1000}s and $why; asking the rider to confirm"))
    }

    private fun alertGroup(nowMs: Long, kind: AlertKind, why: String): List<SafetyEffect> {
        val id = newAlertId(nowMs)
        state = SafetyState.GroupAlerted(nowMs, nowMs + config.groupAckTimeoutMs, id)
        return listOf(
            SafetyEffect.RaiseAlert(id, kind, nowMs),
            SafetyEffect.Log(EventType.GROUP_ALERT, "$why; group alerted, SOS in ${config.groupAckTimeoutMs / 1000}s without acknowledgement"),
        )
    }

    private fun escalate(nowMs: Long, reason: SosReason, alertId: String, why: String): List<SafetyEffect> {
        state = SafetyState.Sos(nowMs, reason, alertId)
        return listOf(
            SafetyEffect.RaiseAlert(alertId, AlertKind.SOS, nowMs),
            SafetyEffect.DispatchSos(reason, alertId),
            SafetyEffect.Log(EventType.SOS_SENT, "SOS dispatched: $why"),
        )
    }

    private fun backToNormal(nowMs: Long, why: String, type: EventType, alertId: String?, cooldown: Boolean = false): List<SafetyEffect> {
        state = SafetyState.Normal
        if (cooldown) suppressUntilMs = nowMs + config.responseCooldownMs
        val fx = mutableListOf<SafetyEffect>()
        if (alertId != null) fx += SafetyEffect.ResolveAlert(alertId)
        fx += SafetyEffect.Log(type, why)
        return fx
    }

    private fun newAlertId(nowMs: Long) = "$riderId-$nowMs"
}
