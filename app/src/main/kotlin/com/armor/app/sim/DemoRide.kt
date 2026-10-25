package com.armor.app.sim

import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.AlertKind
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class DemoAction { STOP_ME, CRASH_ME, STOP_PEER, RESUME_ALL, TOGGLE_PEER_ACK }

/**
 * A scripted group ride so the whole system can be shown on one phone with no GPS, no other riders and no network.
 * It produces exactly what the real sources would — location fixes, accelerometer/gyroscope samples, peer
 * reports and alerts — so the real [com.armor.core.engine.RideEngine] runs unchanged.
 *
 * Everything here is deterministic. Methods are synchronized because the service thread advances the ride while
 * the simulated transport reads it from an I/O thread.
 */
class DemoRide(private val selfId: String, private val selfName: String) {
    private class Sim(val id: String, val name: String, var s: Double, val lane: Double, val phase: Double) {
        var speed = CRUISE_MPS
        var stopped = false
        var hardStop = false
    }

    private val frame = LocalFrame(START)
    private val me = Sim(selfId, selfName, 0.0, 0.0, 0.0)
    private val peers = listOf(
        Sim("arjun", "Arjun", 110.0, 2.5, 1.0),
        Sim("meera", "Meera", 55.0, -2.5, 2.0),
        Sim("kabir", "Kabir", -45.0, 2.5, 3.0),
        Sim("isha", "Isha", -100.0, -2.5, 4.0),
    )
    private val kabir = peers[2]

    private var lastMs = -1L
    private var lastMotionMs = -1L
    private var crashStartMs: Long? = null
    private var peersAcknowledge = false
    private var kabirStoppedAtMs: Long? = null
    private var kabirAlert: GroupAlert? = null
    private val ownAlerts = LinkedHashMap<String, GroupAlert>()
    private val ownAlertSeenAtMs = HashMap<String, Long>()

    val peerAcknowledges: Boolean @Synchronized get() = peersAcknowledge

    @Synchronized
    fun advance(nowMs: Long) {
        if (lastMs < 0) { lastMs = nowMs; lastMotionMs = nowMs; return }
        val dt = ((nowMs - lastMs) / 1000.0).coerceIn(0.0, 2.0)
        lastMs = nowMs
        for (r in listOf(me) + peers) {
            val target = if (r.stopped) 0.0 else CRUISE_MPS + 1.2 * sin(nowMs / 7_000.0 + r.phase)
            val limit = (if (r.hardStop) 25.0 else 5.0) * dt
            r.speed += (target - r.speed).coerceIn(-limit, limit)
            r.s += r.speed * dt
        }
        simulatePeerSide(nowMs)
    }

    @Synchronized
    fun apply(action: DemoAction, nowMs: Long) {
        when (action) {
            DemoAction.STOP_ME -> me.stopped = true
            DemoAction.CRASH_ME -> { me.stopped = true; me.hardStop = true; crashStartMs = nowMs }
            DemoAction.STOP_PEER -> if (!kabir.stopped) { kabir.stopped = true; kabirStoppedAtMs = nowMs }
            DemoAction.RESUME_ALL -> {
                (peers + me).forEach { it.stopped = false; it.hardStop = false }
                crashStartMs = null
                kabirStoppedAtMs = null
                kabirAlert = kabirAlert?.copy(resolved = true)
            }
            DemoAction.TOGGLE_PEER_ACK -> peersAcknowledge = !peersAcknowledge
        }
    }

    @Synchronized
    fun selfFix(nowMs: Long) = fixOf(me, nowMs, accuracyM = 5.0)

    @Synchronized
    fun peerReports(nowMs: Long): List<RiderReport> = peers.map { p ->
        val safety = when {
            p === kabir && kabirAlert?.kind == AlertKind.SOS -> SafetyCode.SOS
            p === kabir && kabirAlert != null && !kabirAlert!!.resolved -> SafetyCode.ALERTED
            else -> SafetyCode.NORMAL
        }
        RiderReport(p.id, p.name, fixOf(p, nowMs, accuracyM = 6.0), null, safety)
    }

    @Synchronized
    fun alerts(): List<GroupAlert> = ownAlerts.values.toList() + listOfNotNull(kabirAlert)

    /** The engine raised or upgraded one of its own alerts. */
    @Synchronized
    fun acceptAlert(alert: GroupAlert, nowMs: Long) {
        val previous = ownAlerts[alert.id]
        ownAlerts[alert.id] = alert.copy(ackedById = previous?.ackedById, ackedByName = previous?.ackedByName, resolved = previous?.resolved == true)
        ownAlertSeenAtMs.putIfAbsent(alert.id, nowMs)
    }

    @Synchronized
    fun resolveAlert(alertId: String) {
        ownAlerts[alertId]?.let { ownAlerts[alertId] = it.copy(resolved = true) }
    }

    /** You acknowledged someone else's alert. */
    @Synchronized
    fun acknowledge(alertId: String, byId: String, byName: String) {
        kabirAlert?.takeIf { it.id == alertId }?.let { kabirAlert = it.copy(ackedById = byId, ackedByName = byName) }
    }

    /**
     * Emits the phone's accelerometer/gyroscope samples for the time since the previous call at 50 Hz: a quiet,
     * upright phone, or — after [DemoAction.CRASH_ME] — a hard impact, tumble, and a phone lying on its side.
     */
    @Synchronized
    fun motion(
        nowMs: Long,
        accel: (t: Long, x: Double, y: Double, z: Double) -> Unit,
        gyro: (t: Long, x: Double, y: Double, z: Double) -> Unit,
    ) {
        if (lastMotionMs < 0) lastMotionMs = nowMs
        var t = lastMotionMs - lastMotionMs % SAMPLE_MS + SAMPLE_MS
        while (t <= nowMs) {
            val crash = crashStartMs
            val dt = if (crash != null) t - crash else -1L
            val noise = 0.04 * sin(t / 37.0)
            when {
                dt in 0 until 20 -> accel(t, 0.0, 0.0, 6 * G)
                dt in 20 until 40 -> accel(t, 4.8 * G, 0.0, 0.0)
                dt >= 600 -> accel(t, G, noise, noise)
                else -> accel(t, noise, noise, G)
            }
            gyro(t, 0.0, 0.0, if (dt in 0 until 800) 5.0 else 0.0)
            t += SAMPLE_MS
        }
        lastMotionMs = nowMs
    }

    /** Kabir's own phone, played by the simulator: after being stopped a while it alerts the group, then escalates. */
    private fun simulatePeerSide(nowMs: Long) {
        val stoppedAt = kabirStoppedAtMs
        if (stoppedAt != null) {
            val stoppedFor = nowMs - stoppedAt
            val alert = kabirAlert
            if (alert == null && stoppedFor > PEER_ALERT_AFTER_MS) {
                kabirAlert = GroupAlert("kabir-$nowMs", "kabir", "Kabir", AlertKind.STATIONARY, position(kabir), nowMs)
            } else if (alert != null && alert.kind == AlertKind.STATIONARY && alert.ackedById == null && !alert.resolved &&
                nowMs - alert.raisedAtMs > PEER_ESCALATE_AFTER_MS
            ) {
                kabirAlert = alert.copy(kind = AlertKind.SOS)
            }
        }
        if (peersAcknowledge) {
            for ((id, alert) in ownAlerts.toList()) {
                val seen = ownAlertSeenAtMs[id] ?: continue
                if (alert.ackedById == null && !alert.resolved && alert.riderId == selfId && nowMs - seen > PEER_ACK_AFTER_MS) {
                    ownAlerts[id] = alert.copy(ackedById = "meera", ackedByName = "Meera")
                }
            }
        }
    }

    // ---- geometry ---------------------------------------------------------------------------

    private fun fixOf(r: Sim, nowMs: Long, accuracyM: Double) =
        LocationFix(nowMs, position(r), r.speed.coerceAtLeast(0.0), Math.toDegrees(heading(r.s)).let { (it + 360.0) % 360.0 }, accuracyM)

    private fun position(r: Sim): LatLon {
        val p = route(r.s)
        val h = heading(r.s)
        return frame.toLatLon(Point(p.x + r.lane * cos(h), p.y - r.lane * sin(h)))
    }

    private companion object {
        val START = LatLon(12.9716, 77.5946)
        const val CRUISE_MPS = 17.0
        const val G = 9.80665
        const val SAMPLE_MS = 20L
        const val PEER_ALERT_AFTER_MS = 10_000L
        const val PEER_ESCALATE_AFTER_MS = 25_000L
        const val PEER_ACK_AFTER_MS = 5_000L

        // A gently winding road, tabulated once: position at every STEP_M of distance along it.
        const val STEP_M = 5.0
        const val MIN_S = -600.0
        const val POINTS = 24_000
        val routeX = DoubleArray(POINTS)
        val routeY = DoubleArray(POINTS)

        init {
            var x = 0.0
            var y = 0.0
            for (i in 0 until POINTS) {
                routeX[i] = x; routeY[i] = y
                val h = heading(MIN_S + i * STEP_M)
                x += sin(h) * STEP_M
                y += cos(h) * STEP_M
            }
            val i0 = (-MIN_S / STEP_M).toInt()
            val ox = routeX[i0]
            val oy = routeY[i0]
            for (i in 0 until POINTS) { routeX[i] -= ox; routeY[i] -= oy }
        }

        fun heading(s: Double): Double = Math.toRadians(25.0 + 18.0 * sin(s / 1_500.0))

        fun route(s: Double): Point {
            val f = ((s - MIN_S) / STEP_M).coerceIn(0.0, POINTS - 2.0)
            val i = floor(f).toInt()
            val k = f - i
            return Point(routeX[i] + (routeX[i + 1] - routeX[i]) * k, routeY[i] + (routeY[i + 1] - routeY[i]) * k)
        }
    }
}
