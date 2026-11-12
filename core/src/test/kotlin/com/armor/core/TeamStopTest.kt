package com.armor.core

import com.armor.core.engine.EngineEffect
import com.armor.core.model.AlertKind
import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.StopReason
import com.armor.core.safety.SafetyState
import com.armor.core.support.RideSim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamStopTest {
    private fun sim() = RideSim().apply { addPeer("arjun", 20.0, 0.0); run(3) }

    @Test fun `only the lead can call a team stop`() {
        val s = sim()
        s.engine.callTeamStop(StopReason.FUEL, s.now)
        s.run(1)
        assertTrue(s.effectsOf<EngineEffect.PublishAlert>().isEmpty())
        s.engine.setLead("me")
        s.engine.callTeamStop(StopReason.FUEL, s.now)
        s.run(1)
        val a = s.effectsOf<EngineEffect.PublishAlert>().single().alert
        assertEquals(AlertKind.TEAM_STOP, a.kind)
        assertEquals(StopReason.FUEL.name, a.detail)
    }

    @Test fun `the lead sees the call and their own check is paused`() {
        val s = sim()
        s.engine.setLead("me")
        s.engine.callTeamStop(StopReason.BREAK, s.now)
        s.run(1)
        val t = assertNotNull(s.snapshot!!.teamStop)
        assertTrue(t.calledByMe)
        assertEquals(StopReason.BREAK, t.reason)
        assertTrue(s.snapshot!!.isLead)
        assertEquals(com.armor.core.model.QuickStatus.BREAK, s.snapshot!!.quickStatus)
    }

    @Test fun `resuming resolves the alert and clears the lead's stop status`() {
        val s = sim()
        s.engine.setLead("me")
        s.engine.callTeamStop(StopReason.FOOD, s.now)
        s.run(1)
        val id = s.effectsOf<EngineEffect.PublishAlert>().single().alert.id
        s.engine.resumeTeam(s.now)
        s.run(1)
        assertEquals(id, s.effectsOf<EngineEffect.ResolveAlert>().single().alertId)
        assertNull(s.snapshot!!.teamStop)
        assertNull(s.snapshot!!.quickStatus)
    }

    private fun incoming(s: RideSim, acked: Boolean = false) = GroupAlert(
        "stop-arjun-1", "arjun", "Arjun", AlertKind.TEAM_STOP, null, s.now - 1000,
        ackedById = if (acked) "meera" else null, ackedByName = if (acked) "Meera" else null, detail = StopReason.FUEL.name,
    )

    @Test fun `a member must confirm even after someone else already has`() {
        val s = sim()
        s.engine.setLead("arjun")
        s.engine.onGroupAlerts(listOf(incoming(s, acked = true)), s.now)
        s.run(1)
        assertTrue(s.snapshot!!.needsAttention, "a team stop stays on screen until this rider confirms")
        s.engine.confirmTeamStop("stop-arjun-1", s.now)
        s.run(1)
        assertFalse(s.snapshot!!.needsAttention)
        assertEquals(com.armor.core.model.QuickStatus.FUEL, s.snapshot!!.quickStatus)
    }

    @Test fun `confirming a team stop means no are-you-okay check however long they sit`() {
        val s = RideSim().apply { addPeer("arjun", 400.0, 0.0); run(2) }
        s.engine.setLead("arjun")
        s.engine.onGroupAlerts(listOf(incoming(s)), s.now)
        s.engine.confirmTeamStop("stop-arjun-1", s.now)
        s.run(300)
        assertFalse(s.engine.state is SafetyState.Verifying || s.engine.state is SafetyState.GroupAlerted, "state was ${s.engine.state}")
    }

    @Test fun `when the lead releases the group the member's stop status clears`() {
        val s = sim()
        s.engine.setLead("arjun")
        s.engine.onGroupAlerts(listOf(incoming(s)), s.now)
        s.engine.confirmTeamStop("stop-arjun-1", s.now)
        s.run(1)
        s.engine.onGroupAlerts(listOf(incoming(s).copy(resolved = true)), s.now)
        s.run(1)
        assertNull(s.snapshot!!.quickStatus)
        assertNull(s.snapshot!!.teamStop)
    }

    @Test fun `the lead sees who has stopped and who is still moving`() {
        val s = RideSim().apply { addPeer("arjun", 20.0, 0.0); addPeer("meera", 40.0, 15.0); run(3) }
        s.engine.setLead("me")
        s.engine.callTeamStop(StopReason.FUEL, s.now)
        s.run(1)
        val t = s.snapshot!!.teamStop!!
        assertTrue("arjun" in t.stopped, "stopped=${t.stopped} waiting=${t.waiting}")
        assertTrue("meera" in t.waiting)
    }

    @Test fun `chat messages send, de-duplicate and cap length`() {
        val s = sim()
        val sentAt = s.now
        s.engine.sendMessage("  fuel at the next pump  ", sentAt)
        s.run(1)
        assertEquals("fuel at the next pump", s.effectsOf<EngineEffect.PublishMessage>().single().message.text)
        s.engine.onMessages(listOf(ChatMessage("m-me-$sentAt", "me", "Me", "dup", sentAt)))
        s.engine.onMessages(listOf(ChatMessage("x1", "arjun", "Arjun", "ok", s.now + 1)))
        s.run(1)
        assertEquals(2, s.snapshot!!.messages.size)
        s.engine.sendMessage("   ", s.now)
        s.engine.sendMessage("a".repeat(900), s.now + 5)
        s.run(1)
        assertEquals(280, s.snapshot!!.messages.last().text.length)
    }
}

class IncomingMessageTest {
    private fun incoming(e: com.armor.core.engine.RideEngine) = e.drainEffects().filterIsInstance<com.armor.core.engine.EngineEffect.IncomingMessage>().map { it.message.text }

    @org.junit.jupiter.api.Test fun `history is silent, then others are announced, never yourself or routine system lines`() {
        val e = com.armor.core.engine.RideEngine("me", "Me")
        val m = { id: String, who: String, text: String -> com.armor.core.model.ChatMessage(id, who, who, text, id.hashCode().toLong()) }
        e.onMessages(listOf(m("h1", "arjun", "old news")))
        kotlin.test.assertEquals(emptyList<String>(), incoming(e), "the ride's back-history does not ping")
        e.onMessages(listOf(m("h1", "arjun", "old news"), m("n1", "arjun", "fuel at the next pump")))
        kotlin.test.assertEquals(listOf("fuel at the next pump"), incoming(e))
        e.onMessages(listOf(m("m-me-1", "me", "on my way")))
        kotlin.test.assertEquals(emptyList<String>(), incoming(e), "your own message is not an alert")
        e.onMessages(listOf(m("s1", "system", "Ben joined"), m("s2", "system", "Ben asks the lead to stop: fuel")))
        kotlin.test.assertEquals(listOf("Ben asks the lead to stop: fuel"), incoming(e), "only the system lines that need attention")
    }
}
