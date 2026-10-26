package com.armor.core.engine

import com.armor.core.comms.CommsPlanner
import com.armor.core.crash.CrashCandidate
import com.armor.core.crash.CrashClassifier
import com.armor.core.crash.CrashDetector
import com.armor.core.crash.CrashFeatures
import com.armor.core.crash.CrashFilter
import com.armor.core.crash.CrashVerdict
import com.armor.core.geo.Geo
import com.armor.core.group.ClusterAnalyzer
import com.armor.core.group.ClusterResult
import com.armor.core.model.AlertKind
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SosReason
import com.armor.core.motion.StationaryDetector
import com.armor.core.safety.EventType
import com.armor.core.safety.Observation
import com.armor.core.safety.SafetyEffect
import com.armor.core.safety.SafetyState
import com.armor.core.safety.SafetyStateMachine

/** Things the host (Android service, test harness) must do on the engine's behalf. */
sealed interface EngineEffect {
    data class PublishAlert(val alert: GroupAlert) : EngineEffect
    data class ResolveAlert(val alertId: String) : EngineEffect
    data class AcknowledgeAlert(val alertId: String, val byId: String, val byName: String) : EngineEffect
    data class DispatchSos(
        val reason: SosReason,
        val alertId: String,
        val position: LatLon?,
        val accuracyM: Double?,
        val fixAgeMs: Long?,
    ) : EngineEffect

    data class CancelSos(val alertId: String, val position: LatLon?) : EngineEffect
    data class Log(val type: EventType, val detail: String, val timeMs: Long) : EngineEffect
}

/**
 * One rider's safety brain. Platform-free: the Android service feeds it GPS fixes, sensor samples and
 * group data, calls [tick] about once a second, and carries out whatever [drainEffects] returns.
 * Everything is driven by the supplied clock, so a whole ride can be replayed deterministically.
 *
 * Not thread-safe: call every method from one thread.
 */
class RideEngine(
    val selfId: String,
    selfName: String,
    private val config: ArmorConfig = ArmorConfig(),
    classifier: CrashClassifier? = null,
) {
    var selfName: String = selfName
        private set

    private val stationary = StationaryDetector(config.stationary)
    private val crashDetector = CrashDetector(config.crash)
    private val crashFilter = CrashFilter(config.crashFilter, classifier)
    private val clusterAnalyzer = ClusterAnalyzer(config.cluster)
    private val machine = SafetyStateMachine(selfId, config.safety)

    private var selfFix: LocationFix? = null
    private val stats = RideStats()
    private val speedHistory = ArrayDeque<Pair<Long, Double>>()
    private val peers = LinkedHashMap<String, RiderReport>()
    private var groupAlerts: Map<String, GroupAlert> = emptyMap()
    private val dismissedAlertIds = HashSet<String>()
    private val acknowledgedByMe = HashSet<String>()
    private var pendingCrash: CrashCandidate? = null
    private var helpAlertId: String? = null
    private var quickStatus: QuickStatus? = null
    private var quickStatusSinceMs = 0L
    private var online = false
    private var intercomConnected = false
    private val effects = ArrayList<EngineEffect>()
    private var last: RideSnapshot? = null

    val state: SafetyState get() = machine.state
    val snapshot: RideSnapshot? get() = last

    // ---- inputs -----------------------------------------------------------------------------

    fun rename(name: String) { selfName = name }

    fun onLocation(fix: LocationFix) {
        selfFix = fix
        stats.onFix(fix)
        stationary.onFix(fix)
        speedHistory.addLast(fix.timeMs to fix.speedMps)
        while (speedHistory.isNotEmpty() && fix.timeMs - speedHistory.first().first > SPEED_HISTORY_MS) speedHistory.removeFirst()
    }

    fun onAccel(tMs: Long, x: Double, y: Double, z: Double) {
        crashDetector.onAccel(tMs, x, y, z)?.let(::onCandidate)
    }

    fun onGyro(tMs: Long, x: Double, y: Double, z: Double) = crashDetector.onGyro(tMs, x, y, z)

    /** Replaces what is known about the other riders (own report is ignored if echoed back). */
    fun onPeers(reports: Collection<RiderReport>) {
        peers.clear()
        reports.filter { it.id != selfId }.forEach { peers[it.id] = it }
    }

    fun onGroupAlerts(alerts: Collection<GroupAlert>, nowMs: Long) {
        groupAlerts = alerts.associateBy { it.id }
        val s = machine.state
        if (s is SafetyState.GroupAlerted) {
            val mine = groupAlerts[s.alertId]
            if (mine?.ackedById != null && mine.ackedById != selfId) {
                emit(machine.onGroupAcknowledged(nowMs, s.alertId, mine.ackedByName ?: "A rider"), nowMs)
            }
        }
    }

    fun onConnectivity(online: Boolean, intercomConnected: Boolean) {
        this.online = online
        this.intercomConnected = intercomConnected
    }

    fun setQuickStatus(status: QuickStatus?, nowMs: Long) {
        quickStatus = status
        quickStatusSinceMs = nowMs
        if (status != null) {
            effects += EngineEffect.Log(EventType.STATUS, "status set to ${status.label}", nowMs)
            if (status.intentionalStop && machine.state !is SafetyState.Normal && machine.state !is SafetyState.Sos && machine.state !is SafetyState.CrashCountdown) {
                emit(machine.onRiderResponded(nowMs), nowMs)
            }
        }
    }

    /**
     * Asks the group for a hand without declaring an emergency. Does nothing if help is already requested or an SOS is
     * active (the SOS already tells everyone). Stays up until cancelled; it never escalates to an SOS by itself.
     */
    fun requestHelp(nowMs: Long) {
        if (helpAlertId != null || machine.state is SafetyState.Sos) return
        val id = "help-$selfId-$nowMs"
        helpAlertId = id
        effects += EngineEffect.PublishAlert(GroupAlert(id, selfId, selfName, AlertKind.HELP, selfFix?.position, nowMs))
        effects += EngineEffect.Log(EventType.GROUP_ALERT, "you asked the group for help", nowMs)
    }

    fun cancelHelp(nowMs: Long) {
        val id = helpAlertId ?: return
        helpAlertId = null
        effects += EngineEffect.ResolveAlert(id)
        effects += EngineEffect.Log(EventType.GROUP_ALERT, "you cancelled your help request", nowMs)
    }

    fun onRiderOk(nowMs: Long) = emit(machine.onRiderResponded(nowMs), nowMs)

    fun onManualSos(nowMs: Long) = emit(machine.onManualSos(nowMs), nowMs)

    /** This rider tells the group they're going to help [alertId]. */
    fun acknowledgeIncoming(alertId: String, nowMs: Long) {
        if (acknowledgedByMe.add(alertId)) {
            effects += EngineEffect.AcknowledgeAlert(alertId, selfId, selfName)
            effects += EngineEffect.Log(EventType.GROUP_ACK, "you acknowledged an alert from ${groupAlerts[alertId]?.riderName ?: "a rider"}", nowMs)
        }
    }

    fun dismissIncoming(alertId: String) { dismissedAlertIds += alertId }

    // ---- clock ------------------------------------------------------------------------------

    fun tick(nowMs: Long): RideSnapshot {
        crashDetector.poll(nowMs)?.let(::onCandidate)
        assessPendingCrash(nowMs)

        if (quickStatus != null && nowMs - quickStatusSinceMs > config.safety.intentionalStopGraceMs) {
            effects += EngineEffect.Log(EventType.STATUS, "status ${quickStatus?.label} expired", nowMs)
            quickStatus = null
        }

        val cluster = analyze(nowMs)
        val assessment = cluster.assess(selfId)
        val obs = Observation(
            assessment = assessment,
            stationaryForMs = stationary.stationaryForMs(nowMs),
            moving = stationary.isMoving(nowMs),
            intentionalStop = quickStatus?.intentionalStop == true,
        )
        emit(machine.onTick(nowMs, obs), nowMs)

        return buildSnapshot(nowMs, cluster, assessment, obs.stationaryForMs).also { last = it }
    }

    fun drainEffects(): List<EngineEffect> = effects.toList().also { effects.clear() }

    /** What to publish to the group, or null before the first GPS fix. */
    fun selfReport(): RiderReport? {
        val fix = selfFix ?: return null
        return RiderReport(selfId, selfName, fix, quickStatus, machine.state.code)
    }

    // ---- internals --------------------------------------------------------------------------

    private fun onCandidate(c: CrashCandidate) {
        if (pendingCrash != null || machine.state is SafetyState.Sos || machine.state is SafetyState.CrashCountdown) return
        pendingCrash = c
        effects += EngineEffect.Log(
            EventType.SYSTEM,
            "impact %.1f g with %s; checking whether the bike stopped".format(c.peakG, describeRotation(c)),
            c.impactTimeMs,
        )
    }

    private fun describeRotation(c: CrashCandidate): String {
        val tilt = c.tiltDeg?.let { "%.0f° tilt change".format(it) }
        val rot = "%.0f° rotation".format(Math.toDegrees(c.rotationRad))
        return listOfNotNull(rot, tilt).joinToString(", ")
    }

    private fun assessPendingCrash(nowMs: Long) {
        val c = pendingCrash ?: return
        if (nowMs - c.impactTimeMs < config.crashAssessDelayMs) return
        pendingCrash = null

        val pre = speedHistory.filter { it.first in (c.impactTimeMs - PRE_IMPACT_WINDOW_MS)..c.impactTimeMs }
            .maxOfOrNull { it.second }
        val post = speedHistory.lastOrNull { it.first >= c.impactTimeMs + POST_IMPACT_SETTLE_MS }?.second
        val verdict = crashFilter.assess(CrashFeatures(c.peakG, c.rotationRad, c.tiltDeg, pre, post))
        when (verdict) {
            is CrashVerdict.Dismiss ->
                effects += EngineEffect.Log(EventType.CRASH_DISMISSED, "impact dismissed as a false alarm: ${verdict.reason}", nowMs)
            is CrashVerdict.Confirm ->
                emit(machine.onCrash(nowMs, "%.1f g impact; %s".format(c.peakG, verdict.reason)), nowMs)
        }
    }

    private fun analyze(nowMs: Long): ClusterResult {
        val reports = ArrayList<RiderReport>(peers.size + 1)
        selfReport()?.let { reports += it }
        reports += peers.values
        return clusterAnalyzer.analyze(reports, nowMs, config.peerStaleAfterMs)
    }

    private fun emit(fx: List<SafetyEffect>, nowMs: Long) {
        for (e in fx) when (e) {
            is SafetyEffect.RaiseAlert -> effects += EngineEffect.PublishAlert(
                GroupAlert(e.alertId, selfId, selfName, e.kind, selfFix?.position, e.raisedAtMs),
            )
            is SafetyEffect.ResolveAlert -> effects += EngineEffect.ResolveAlert(e.alertId)
            is SafetyEffect.DispatchSos -> effects += EngineEffect.DispatchSos(
                e.reason, e.alertId, selfFix?.position, selfFix?.accuracyM, selfFix?.let { nowMs - it.timeMs },
            )
            is SafetyEffect.CancelSos -> effects += EngineEffect.CancelSos(e.alertId, selfFix?.position)
            is SafetyEffect.Log -> effects += EngineEffect.Log(e.type, e.detail, nowMs)
        }
    }

    private fun buildSnapshot(nowMs: Long, cluster: ClusterResult, assessment: OutlierAssessment, stationaryForMs: Long): RideSnapshot {
        val me = selfFix?.position
        val views = ArrayList<RiderView>()
        selfReport()?.let { views += view(it, nowMs, me, cluster, isSelf = true) }
        peers.values.sortedBy { it.name.lowercase() }.forEach { views += view(it, nowMs, me, cluster, isSelf = false) }

        val incoming = groupAlerts.values
            .filter { it.riderId != selfId && !it.resolved && it.id !in dismissedAlertIds && nowMs - it.raisedAtMs <= config.alertMaxAgeMs }
            .sortedByDescending { it.raisedAtMs }
            .map {
                IncomingAlert(
                    alert = it,
                    acknowledgedByMe = it.id in acknowledgedByMe,
                    distanceM = if (me != null && it.position != null) Geo.distanceM(me, it.position) else null,
                    bearingDeg = if (me != null && it.position != null) Geo.bearingDeg(me, it.position) else null,
                )
            }

        return RideSnapshot(
            nowMs = nowMs,
            riders = views,
            clusters = cluster.clusters,
            mainClusterIndex = cluster.mainClusterIndex,
            linkDistanceM = cluster.linkDistanceM,
            assessment = assessment,
            safety = machine.state,
            stationaryForMs = stationaryForMs,
            gpsLost = stationary.gpsLost(nowMs),
            quickStatus = quickStatus,
            incomingAlerts = incoming,
            voice = CommsPlanner.select(online, intercomConnected),
            online = online,
            stats = stats.summary(nowMs),
            help = helpAlertId?.let { id -> HelpRequest(groupAlerts[id]?.ackedByName) },
        )
    }

    private fun view(r: RiderReport, nowMs: Long, me: LatLon?, cluster: ClusterResult, isSelf: Boolean): RiderView {
        val age = (nowMs - r.fix.timeMs).coerceAtLeast(0)
        return RiderView(
            id = r.id,
            name = r.name,
            position = r.fix.position,
            speedMps = r.fix.speedMps,
            headingDeg = r.fix.bearingDeg,
            accuracyM = r.fix.accuracyM,
            isSelf = isSelf,
            isStale = age > config.peerStaleAfterMs,
            isOutlier = cluster.isOutlier(r.id),
            status = r.status,
            safety = r.safety,
            ageMs = age,
            distanceFromSelfM = if (me != null && !isSelf) Geo.distanceM(me, r.fix.position) else null,
            bearingFromSelfDeg = if (me != null && !isSelf) Geo.bearingDeg(me, r.fix.position) else null,
        )
    }

    private companion object {
        const val SPEED_HISTORY_MS = 20_000L
        const val PRE_IMPACT_WINDOW_MS = 6_000L
        const val POST_IMPACT_SETTLE_MS = 2_000L
    }
}
