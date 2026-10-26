package com.armor.core

import com.armor.core.engine.EngineEffect
import com.armor.core.model.AlertKind
import com.armor.core.support.RideSim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpRequestTest {
    private fun sim() = RideSim().apply { addPeer("arjun", 20.0, 0.0); run(5) }

    @Test fun `asking for help publishes a HELP alert and shows as open`() {
        val sim = sim()
        sim.engine.requestHelp(sim.now)
        sim.run(1)
        val alert = sim.effectsOf<EngineEffect.PublishAlert>().single().alert
        assertEquals(AlertKind.HELP, alert.kind)
        assertEquals("me", alert.riderId)
        assertTrue(sim.snapshot!!.help != null)
    }

    @Test fun `asking twice does not publish twice`() {
        val sim = sim()
        sim.engine.requestHelp(sim.now)
        sim.engine.requestHelp(sim.now + 1000)
        sim.run(1)
        assertEquals(1, sim.effectsOf<EngineEffect.PublishAlert>().size)
    }

    @Test fun `cancelling resolves the alert and clears the request`() {
        val sim = sim()
        sim.engine.requestHelp(sim.now)
        sim.run(1)
        val id = sim.effectsOf<EngineEffect.PublishAlert>().single().alert.id
        sim.engine.cancelHelp(sim.now)
        sim.run(1)
        assertEquals(id, sim.effectsOf<EngineEffect.ResolveAlert>().single().alertId)
        assertNull(sim.snapshot!!.help)
    }

    @Test fun `a help request never escalates to an sos by itself`() {
        val sim = sim()
        sim.engine.requestHelp(sim.now)
        sim.run(600)
        assertTrue(sim.effectsOf<EngineEffect.DispatchSos>().isEmpty())
    }
}
