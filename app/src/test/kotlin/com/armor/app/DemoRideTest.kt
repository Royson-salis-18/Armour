package com.armor.app

import com.armor.app.sim.DemoAction
import com.armor.app.sim.DemoRide
import com.armor.core.engine.ArmorConfig
import com.armor.core.engine.EngineEffect
import com.armor.core.engine.RideEngine
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.AlertKind
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.SosReason
import com.armor.core.safety.SafetyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the demo simulator through the real engine the same way RideService does, so the scripted demo can't
 * silently drift away from what the safety logic actually does.
 */
class DemoRideTest {
    private class Driver {
        val demo = DemoRide("me", "Me")
        val engine = RideEngine("me", "Me", ArmorConfig().forDemo())
        var now = 1_000_000L
        lateinit var snapshot: RideSnapshot
        val effects = mutableListOf<EngineEffect>()

        init { step() }

        fun step() {
            now += 1_000
            demo.advance(now)
            demo.motion(now, engine::onAccel, engine::onGyro)
            engine.onLocation(demo.selfFix(now))
            engine.onPeers(demo.peerReports(now))
            engine.onGroupAlerts(demo.alerts(), now)
            snapshot = engine.tick(now)
            for (e in engine.drainEffects()) {
                effects += e
                when (e) {
                    is EngineEffect.PublishAlert -> demo.acceptAlert(e.alert, now)
                    is EngineEffect.ResolveAlert -> demo.resolveAlert(e.alertId)
                    is EngineEffect.AcknowledgeAlert -> demo.acknowledge(e.alertId, e.byId, e.byName)
                    else -> Unit
                }
            }
        }

        fun run(seconds: Int) = repeat(seconds) { step() }
        fun until(maxSeconds: Int, cond: () -> Boolean): Boolean {
            repeat(maxSeconds) { if (cond()) return true; step() }
            return cond()
        }
        inline fun <reified T : EngineEffect> effectsOf() = effects.filterIsInstance<T>()
    }

    @Test fun `the demo group rides together and is seen as one cluster`() {
        val d = Driver().also { it.run(40) }
        assertEquals(5, d.snapshot.riders.size)
        assertEquals(1, d.snapshot.clusters.size)
        assertEquals(OutlierAssessment.IN_CLUSTER, d.snapshot.assessment)
        assertTrue(d.snapshot.riders.none { it.isOutlier })
        assertIs<SafetyState.Normal>(d.snapshot.safety)
        assertTrue(d.snapshot.self!!.speedMps in 12.0..22.0, "cruising speed ${d.snapshot.self!!.speedMps}")
    }

    @Test fun `stopping the bike runs check then group alert then SOS`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.STOP_ME, d.now)

        assertTrue(d.until(120) { d.snapshot.safety is SafetyState.Verifying }, "never asked")
        assertTrue(d.until(30) { d.snapshot.safety is SafetyState.GroupAlerted })
        assertTrue(d.until(40) { d.snapshot.safety is SafetyState.Sos })
        assertEquals(SosReason.STATIONARY_UNACKNOWLEDGED, d.effectsOf<EngineEffect.DispatchSos>().single().reason)
        assertTrue(d.snapshot.self!!.isOutlier)
    }

    @Test fun `peers acknowledging stops the escalation`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.TOGGLE_PEER_ACK, d.now)
        d.demo.apply(DemoAction.STOP_ME, d.now)

        assertTrue(d.until(150) { d.snapshot.safety is SafetyState.Acknowledged }, "no acknowledgement seen")
        d.run(90)
        assertIs<SafetyState.Acknowledged>(d.snapshot.safety)
        assertTrue(d.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }

    @Test fun `riding on again after being asked cancels the check`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.STOP_ME, d.now)
        assertTrue(d.until(120) { d.snapshot.safety is SafetyState.Verifying })
        d.demo.apply(DemoAction.RESUME_ALL, d.now)
        assertTrue(d.until(40) { d.snapshot.safety is SafetyState.Normal })
        assertTrue(d.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }

    @Test fun `a simulated crash is detected from sensor samples and ends in SOS if not cancelled`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.CRASH_ME, d.now)

        assertTrue(d.until(15) { d.snapshot.safety is SafetyState.CrashCountdown }, "crash never detected")
        assertTrue(d.effectsOf<EngineEffect.DispatchSos>().isEmpty(), "SOS must wait for the countdown")
        assertTrue(d.until(30) { d.snapshot.safety is SafetyState.Sos })
        assertEquals(SosReason.CRASH_NO_RESPONSE, d.effectsOf<EngineEffect.DispatchSos>().single().reason)
    }

    @Test fun `cancelling the crash countdown sends nothing`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.CRASH_ME, d.now)
        assertTrue(d.until(15) { d.snapshot.safety is SafetyState.CrashCountdown })
        d.engine.onRiderOk(d.now)
        d.run(40)
        assertTrue(d.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }

    @Test fun `when Kabir stops his alert reaches the rider and acknowledging it is reported back`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.STOP_PEER, d.now)

        assertTrue(d.until(90) { d.snapshot.needsAttention }, "no incoming alert")
        val incoming = d.snapshot.incomingAlerts.first()
        assertEquals("Kabir", incoming.alert.riderName)
        assertEquals(AlertKind.STATIONARY, incoming.alert.kind)
        assertNotNull(incoming.distanceM)

        d.engine.acknowledgeIncoming(incoming.alert.id, d.now)
        d.step()
        d.step()
        assertEquals("me", d.demo.alerts().single { it.id == incoming.alert.id }.ackedById)
        assertTrue(d.snapshot.incomingAlerts.single().acknowledgedByMe)
    }

    @Test fun `an unacknowledged alert from Kabir escalates to SOS`() {
        val d = Driver().also { it.run(20) }
        d.demo.apply(DemoAction.STOP_PEER, d.now)
        assertTrue(d.until(120) { d.snapshot.incomingAlerts.any { it.alert.kind == AlertKind.SOS } })
        assertEquals("kabir", d.snapshot.riders.first { it.id == "kabir" }.id)
    }

    @Test fun `the whole demo is deterministic`() {
        fun run() = Driver().also { it.run(30); it.demo.apply(DemoAction.CRASH_ME, it.now); it.run(45) }.effects.toList()
        assertEquals(run(), run())
    }
}
