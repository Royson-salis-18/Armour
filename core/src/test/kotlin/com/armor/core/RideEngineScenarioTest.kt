package com.armor.core

import com.armor.core.engine.ArmorConfig
import com.armor.core.engine.EngineEffect
import com.armor.core.model.AlertKind
import com.armor.core.model.GroupAlert
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.QuickStatus
import com.armor.core.model.SafetyCode
import com.armor.core.model.SosReason
import com.armor.core.safety.EventType
import com.armor.core.safety.SafetyState
import com.armor.core.support.RideSim
import com.armor.core.support.at
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Whole rides replayed second by second through the real engine — no mocks. */
class RideEngineScenarioTest {

    /** Three peers cruising north at 72 km/h; "me" is stopped at the start line and falls behind. */
    private fun groupLeavingMe() = RideSim().apply {
        addPeer("arjun", 60.0, 20.0)
        addPeer("meera", 30.0, 20.0)
        addPeer("kabir", 0.0, 20.0)
    }

    private fun RideSim.alertState() = engine.state as? SafetyState.GroupAlerted
    private val RideSim.logTypes get() = effectsOf<EngineEffect.Log>().map { it.type }

    // ---- stationary → group → SOS ----------------------------------------------------------

    @Test fun `stopped and left behind runs the full escalation ladder in order`() {
        val sim = groupLeavingMe()

        assertTrue(sim.runUntil(150) { sim.engine.state is SafetyState.Verifying }, "never asked the rider")
        val askedAt = sim.now
        assertEquals(OutlierAssessment.OUTLIER, sim.snapshot!!.assessment)
        assertTrue(sim.snapshot!!.self!!.isOutlier)

        assertTrue(sim.runUntil(40) { sim.engine.state is SafetyState.GroupAlerted })
        assertEquals(30_000, sim.now - askedAt, "group alerted 30 s after the check")
        val alert = sim.effectsOf<EngineEffect.PublishAlert>().single().alert
        assertEquals(AlertKind.STATIONARY, alert.kind)
        assertEquals("me", alert.riderId)
        assertEquals(SafetyCode.ALERTED, sim.engine.selfReport()!!.safety)

        assertTrue(sim.runUntil(70) { sim.engine.state is SafetyState.Sos })
        val sos = sim.effectsOf<EngineEffect.DispatchSos>().single()
        assertEquals(SosReason.STATIONARY_UNACKNOWLEDGED, sos.reason)
        assertEquals(alert.id, sos.alertId)
        assertNotNull(sos.position)
        assertEquals(SafetyCode.SOS, sim.engine.selfReport()!!.safety)

        val order = sim.logTypes.filter { it in setOf(EventType.STATIONARY_CHECK, EventType.GROUP_ALERT, EventType.SOS_SENT) }
        assertEquals(listOf(EventType.STATIONARY_CHECK, EventType.GROUP_ALERT, EventType.SOS_SENT), order)
    }

    @Test fun `a group acknowledgement stops the escalation`() {
        val sim = groupLeavingMe()
        assertTrue(sim.runUntil(300) { sim.alertState() != null })
        val alert = sim.effectsOf<EngineEffect.PublishAlert>().single().alert

        sim.engine.onGroupAlerts(listOf(alert.copy(ackedById = "meera", ackedByName = "Meera")), sim.now)
        assertIs<SafetyState.Acknowledged>(sim.engine.state)

        sim.run(300)
        assertIs<SafetyState.Acknowledged>(sim.engine.state)
        assertTrue(sim.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }

    @Test fun `the rider saying I'm OK clears the check and does not re-ask for five minutes`() {
        val sim = groupLeavingMe()
        assertTrue(sim.runUntil(300) { sim.engine.state is SafetyState.Verifying })
        sim.engine.onRiderOk(sim.now)
        assertIs<SafetyState.Normal>(sim.engine.state)

        sim.run(290)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(sim.runUntil(60) { sim.engine.state is SafetyState.Verifying }, "asks again once the cooldown ends")
    }

    @Test fun `riders coming back to the stopped rider resolve the alert`() {
        val sim = groupLeavingMe()
        assertTrue(sim.runUntil(300) { sim.alertState() != null })
        val alertId = sim.effectsOf<EngineEffect.PublishAlert>().single().alert.id

        sim.others.forEachIndexed { i, r -> r.north = sim.me.north + 15.0 * (i + 1); r.speed = 0.0 }
        sim.run(3)

        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(sim.effectsOf<EngineEffect.ResolveAlert>().any { it.alertId == alertId })
        assertTrue(sim.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }

    @Test fun `riding off after being asked cancels the check`() {
        val sim = groupLeavingMe()
        assertTrue(sim.runUntil(300) { sim.engine.state is SafetyState.Verifying })
        sim.me.speed = 15.0
        sim.run(15)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(EventType.RIDER_RESUMED in sim.logTypes)
    }

    // ---- when it should stay quiet ---------------------------------------------------------

    @Test fun `a whole group stopped together at a fuel stop is never checked`() {
        val sim = RideSim().apply {
            addPeer("arjun", 25.0, 0.0); addPeer("meera", -20.0, 0.0); addPeer("kabir", 10.0, 0.0)
        }
        sim.run(1_800)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertEquals(OutlierAssessment.IN_CLUSTER, sim.snapshot!!.assessment)
    }

    @Test fun `declaring a fuel stop silences the check for the grace period then it resumes`() {
        val sim = groupLeavingMe()
        sim.run(5)
        sim.engine.setQuickStatus(QuickStatus.FUEL, sim.now)
        assertEquals(QuickStatus.FUEL, sim.engine.selfReport()!!.status)

        sim.run(800)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(sim.runUntil(300) { sim.engine.state is SafetyState.Verifying }, "grace period over, still stopped")
        assertEquals(null, sim.engine.selfReport()!!.status, "expired status is cleared")
    }

    @Test fun `declaring a stop while being checked answers the check`() {
        val sim = groupLeavingMe()
        assertTrue(sim.runUntil(300) { sim.engine.state is SafetyState.Verifying })
        sim.engine.setQuickStatus(QuickStatus.MECHANICAL, sim.now)
        assertIs<SafetyState.Normal>(sim.engine.state)
    }

    @Test fun `riding solo waits five minutes before asking`() {
        val sim = RideSim()
        sim.run(295)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertEquals(OutlierAssessment.UNKNOWN, sim.snapshot!!.assessment)
        assertTrue(sim.runUntil(20) { sim.engine.state is SafetyState.Verifying })
    }

    @Test fun `losing contact with the group falls back to solo timing instead of staying silent forever`() {
        val sim = RideSim().apply { addPeer("arjun", 20.0, 0.0) }
        sim.run(30)
        assertEquals(OutlierAssessment.IN_CLUSTER, sim.snapshot!!.assessment)
        sim.peersReporting = false
        sim.run(100)
        assertEquals(OutlierAssessment.UNKNOWN, sim.snapshot!!.assessment)
        assertTrue(sim.runUntil(400) { sim.engine.state is SafetyState.Verifying })
    }

    @Test fun `stale GPS never counts as being stationary`() {
        val sim = RideSim()
        sim.run(20)
        // GPS goes silent (tunnel): tick without feeding fixes.
        var now = sim.now
        repeat(600) { now += 1_000; sim.engine.tick(now) }
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(sim.engine.snapshot!!.gpsLost)
    }

    // ---- crash pipeline --------------------------------------------------------------------

    private fun ridingWithGroup() = RideSim().apply {
        me.speed = 20.0
        addPeer("arjun", 60.0, 20.0); addPeer("meera", 30.0, 20.0)
        run(15)
    }

    @Test fun `crash with the bike stopping dead starts a countdown then sends SOS`() {
        val sim = ridingWithGroup()
        sim.feedCrashSignature()
        sim.me.speed = 0.0
        assertTrue(sim.runUntil(12) { sim.engine.state is SafetyState.CrashCountdown }, "countdown never started")
        assertEquals(SafetyCode.CRASH, sim.engine.selfReport()!!.safety)
        assertTrue(sim.effectsOf<EngineEffect.DispatchSos>().isEmpty(), "must not send SOS before the countdown ends")

        assertTrue(sim.runUntil(40) { sim.engine.state is SafetyState.Sos })
        assertEquals(SosReason.CRASH_NO_RESPONSE, sim.effectsOf<EngineEffect.DispatchSos>().single().reason)
    }

    @Test fun `rider cancelling the crash countdown sends nothing`() {
        val sim = ridingWithGroup()
        sim.feedCrashSignature()
        sim.me.speed = 0.0
        assertTrue(sim.runUntil(12) { sim.engine.state is SafetyState.CrashCountdown })
        sim.engine.onRiderOk(sim.now)
        sim.run(60)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(sim.effectsOf<EngineEffect.DispatchSos>().isEmpty())
        assertTrue(EventType.CRASH_CANCELLED in sim.logTypes)
    }

    @Test fun `a bump while still riding at speed is dismissed as a false alarm`() {
        val sim = ridingWithGroup()
        sim.feedCrashSignature()
        sim.run(12)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(EventType.CRASH_DISMISSED in sim.logTypes)
        assertTrue(sim.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }

    @Test fun `impact without a tumble is not a crash`() {
        val sim = ridingWithGroup()
        sim.feedCrashSignature(rotate = false)
        sim.me.speed = 0.0
        sim.run(12)
        assertIs<SafetyState.Normal>(sim.engine.state)
        assertTrue(EventType.CRASH !in sim.logTypes)
    }

    @Test fun `an ambiguous impact while parked still gets the cancellable countdown`() {
        val sim = RideSim().apply { addPeer("arjun", 20.0, 0.0); run(15) }
        sim.feedCrashSignature()
        assertTrue(sim.runUntil(12) { sim.engine.state is SafetyState.CrashCountdown })
    }

    // ---- manual SOS & group-side behaviour -------------------------------------------------

    @Test fun `manual SOS reports the last known position and can be cancelled`() {
        val sim = groupLeavingMe()
        sim.run(5)
        sim.engine.onManualSos(sim.now)
        val sos = sim.engine.drainEffects().filterIsInstance<EngineEffect.DispatchSos>().single()
        assertEquals(SosReason.MANUAL, sos.reason)
        assertEquals(at(0.0, 0.0).lat, sos.position!!.lat, 1e-6)
        assertEquals(0L, sos.fixAgeMs)

        sim.engine.onRiderOk(sim.now)
        val fx = sim.engine.drainEffects()
        assertTrue(fx.any { it is EngineEffect.CancelSos })
        assertIs<SafetyState.Normal>(sim.engine.state)
    }

    @Test fun `incoming alerts from other riders can be acknowledged and dismissed`() {
        val sim = groupLeavingMe()
        sim.run(5)
        val alert = GroupAlert("kabir-1", "kabir", "Kabir", AlertKind.STATIONARY, at(0.0, 200.0), sim.now)
        sim.engine.onGroupAlerts(listOf(alert, alert.copy(id = "me-1", riderId = "me", riderName = "Me")), sim.now)
        sim.run(1)

        val incoming = sim.snapshot!!.incomingAlerts.single()
        assertEquals("kabir-1", incoming.alert.id)
        assertTrue(!incoming.acknowledgedByMe)
        assertTrue(sim.snapshot!!.needsAttention)
        assertNotNull(incoming.distanceM)

        sim.engine.acknowledgeIncoming("kabir-1", sim.now)
        sim.engine.acknowledgeIncoming("kabir-1", sim.now) // idempotent
        val acks = sim.engine.drainEffects().filterIsInstance<EngineEffect.AcknowledgeAlert>()
        assertEquals(1, acks.size)
        assertEquals("me", acks.single().byId)
        sim.run(1)
        assertTrue(sim.snapshot!!.incomingAlerts.single().acknowledgedByMe)

        sim.engine.dismissIncoming("kabir-1")
        sim.run(1)
        assertTrue(sim.snapshot!!.incomingAlerts.isEmpty())
    }

    @Test fun `resolved and expired alerts are not shown`() {
        val sim = groupLeavingMe()
        sim.run(5)
        val fresh = GroupAlert("k-1", "kabir", "Kabir", AlertKind.SOS, at(0.0, 50.0), sim.now)
        sim.engine.onGroupAlerts(
            listOf(fresh.copy(resolved = true), fresh.copy(id = "k-2", raisedAtMs = sim.now - 3_600_000)),
            sim.now,
        )
        sim.run(1)
        assertTrue(sim.snapshot!!.incomingAlerts.isEmpty())
    }

    @Test fun `voice route follows connectivity`() {
        val sim = RideSim()
        sim.engine.onConnectivity(online = false, intercomConnected = true)
        sim.run(1)
        assertEquals(com.armor.core.comms.VoiceRoute.HELMET_INTERCOM, sim.snapshot!!.voice)
        sim.engine.onConnectivity(online = true, intercomConnected = true)
        sim.run(1)
        assertEquals(com.armor.core.comms.VoiceRoute.MOBILE_NETWORK, sim.snapshot!!.voice)
    }

    @Test fun `no self report before the first fix`() {
        assertEquals(null, RideSim().engine.selfReport())
    }

    @Test fun `the same ride replayed twice produces identical effects`() {
        fun ride() = groupLeavingMe().also { it.run(250) }.effects.toList()
        assertEquals(ride(), ride())
    }

    @Test fun `demo timers compress the whole ladder under a minute`() {
        val sim = RideSim(ArmorConfig().forDemo()).apply { addPeer("arjun", 30.0, 20.0); addPeer("meera", 0.0, 20.0) }
        assertTrue(sim.runUntil(60) { sim.engine.state is SafetyState.Sos })
    }
}
