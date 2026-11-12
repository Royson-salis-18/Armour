package com.armor.app

import com.armor.app.net.ArmorCloudTransport
import com.armor.core.model.AlertKind
import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.HazardType
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A group of riders on the real ARMOR server (`ARMOR_WEB_URL`): the messy cases that a two-rider happy path misses.
 * Everyone here goes through the same [ArmorCloudTransport] the phones use.
 */
@EnabledIfEnvironmentVariable(named = "ARMOR_WEB_URL", matches = ".+")
class GroupCommsTest {
    init { ArmorCloudTransport.server = System.getenv("ARMOR_WEB_URL") ?: "" }

    private class Rider(val name: String, val t: ArmorCloudTransport, val id: String, val token: String)

    private fun fix(lat: Double, lon: Double = 77.5, speed: Double = 10.0) =
        LocationFix(System.currentTimeMillis(), LatLon(lat, lon), speed, 45.0, 5.0)

    private suspend fun group(vararg names: String): Pair<String, List<Rider>> {
        val lead = assertNotNull(ArmorCloudTransport.createRide("Test run", names[0], "+9198000000${names.indexOf(names[0])}1"))
        val riders = arrayListOf(Rider(names[0], ArmorCloudTransport(lead.code, lead.riderId, lead.token), lead.riderId, lead.token))
        for (n in names.drop(1)) {
            val s = assertNotNull(ArmorCloudTransport.joinRide(lead.code, n, "98000${n.length}${n.hashCode().toString().takeLast(5)}"))
            riders += Rider(n, ArmorCloudTransport(s.code, s.riderId, s.token), s.riderId, s.token)
        }
        return lead.code to riders
    }

    @Test fun `six riders publish at once and every one sees all six at the right place`() = runBlocking {
        val (_, rs) = group("Asha", "Ben", "Chitra", "Dev", "Esha", "Farid")
        rs.mapIndexed { i, r -> async { assertTrue(r.t.publishSelf(RiderReport(r.id, r.name, fix(12.90 + i * 0.001), null, SafetyCode.NORMAL))) } }.awaitAll()
        for (viewer in rs) {
            val seen = assertNotNull(viewer.t.fetch())
            assertEquals(6, seen.riders.size, "${viewer.name} sees everyone")
            rs.forEachIndexed { i, r -> assertEquals(12.90 + i * 0.001, seen.riders.first { it.id == r.id }.fix.position.lat, 1e-9) }
            assertEquals(6, seen.crew.size, "and has everyone's number for an SOS")
        }
    }

    @Test fun `chat reaches everybody in order and a late joiner gets the history`() = runBlocking {
        val (code, rs) = group("Asha", "Ben", "Chitra")
        listOf("one", "two", "three", "four").forEachIndexed { i, text -> assertTrue(rs[i % 3].t.sendMessage(ChatMessage("m$i", rs[i % 3].id, rs[i % 3].name, text, 0))) }
        for (r in rs) {
            val texts = assertNotNull(r.t.fetch()).messages.map { it.text }.filter { it in setOf("one", "two", "three", "four") }
            assertEquals(listOf("one", "two", "three", "four"), texts, "${r.name} sees chat in the order it was sent")
        }
        val late = assertNotNull(ArmorCloudTransport.joinRide(code, "Late", ""))
        val lt = ArmorCloudTransport(late.code, late.riderId, late.token)
        assertTrue(assertNotNull(lt.fetch()).messages.any { it.text == "one" }, "a late joiner can catch up on what was said")
    }

    @Test fun `help request is seen by all, answered by one, and closed by the asker`() = runBlocking {
        val (_, rs) = group("Asha", "Ben", "Chitra")
        val (a, b, c) = rs
        assertTrue(c.t.upsertAlert(GroupAlert("h1", c.id, "Chitra", AlertKind.HELP, LatLon(12.9, 77.5), 0, detail = "puncture")))
        for (v in listOf(a, b)) assertTrue(assertNotNull(v.t.fetch()).alerts.any { it.kind == AlertKind.HELP && it.riderName == "Chitra" && it.detail == "puncture" })
        val helpOnB = assertNotNull(b.t.fetch()).alerts.first { it.kind == AlertKind.HELP }
        assertTrue(b.t.acknowledge(helpOnB.id, b.id, "Ben"))
        val onC = assertNotNull(c.t.fetch()).alerts.first { it.kind == AlertKind.HELP }
        assertEquals("Ben", onC.ackedByName, "the asker sees who is coming")
        assertEquals("h1", onC.id)
        assertTrue(c.t.resolveAlert("h1"))
        assertTrue(assertNotNull(a.t.fetch()).alerts.first { it.kind == AlertKind.HELP }.resolved)
    }

    @Test fun `a hazard shows for everyone with its place and type`() = runBlocking {
        val (_, rs) = group("Asha", "Ben")
        assertTrue(rs[1].t.upsertAlert(GroupAlert("hz", rs[1].id, "Ben", AlertKind.HAZARD, LatLon(12.95, 77.55), 0, detail = HazardType.OIL.name)))
        val h = assertNotNull(rs[0].t.fetch()).alerts.first { it.kind == AlertKind.HAZARD }
        assertEquals(12.95, h.position!!.lat, 1e-9)
        assertEquals(HazardType.OIL.name, h.detail)
    }

    @Test fun `a crash alert marks that rider as in trouble on every other phone`() = runBlocking {
        val (_, rs) = group("Asha", "Ben", "Chitra")
        for (r in rs) r.t.publishSelf(RiderReport(r.id, r.name, fix(12.9), null, SafetyCode.NORMAL))
        assertTrue(rs[2].t.upsertAlert(GroupAlert("c1", rs[2].id, "Chitra", AlertKind.CRASH, LatLon(12.9, 77.5), 0)))
        for (v in listOf(rs[0], rs[1])) {
            val seen = assertNotNull(v.t.fetch())
            assertEquals(SafetyCode.CRASH, seen.riders.first { it.id == rs[2].id }.safety)
            assertTrue(seen.alerts.any { it.kind == AlertKind.CRASH && !it.resolved })
        }
        assertTrue(rs[2].t.resolveAlert("c1"))
        assertEquals(SafetyCode.NORMAL, assertNotNull(rs[0].t.fetch()).riders.first { it.id == rs[2].id }.safety, "I'm okay clears it for everyone")
    }

    @Test fun `regroup point is set by the lead only and clears`() = runBlocking {
        val (_, rs) = group("Asha", "Ben")
        assertTrue(rs[0].t.upsertAlert(GroupAlert("rg", rs[0].id, "Asha", AlertKind.REGROUP, LatLon(12.97, 77.6), 0, detail = "Petrol bunk")))
        val g = assertNotNull(rs[1].t.fetch()).alerts.first { it.kind == AlertKind.REGROUP }
        assertEquals("Petrol bunk", g.detail)
        assertFalse(rs[1].t.upsertAlert(GroupAlert("rg2", rs[1].id, "Ben", AlertKind.REGROUP, LatLon(0.0, 0.0), 0)), "a non-lead can't move the meeting point")
        assertTrue(rs[0].t.resolveAlert(g.id))
        assertTrue(assertNotNull(rs[1].t.fetch()).alerts.none { it.kind == AlertKind.REGROUP })
    }

    @Test fun `a rider who leaves disappears from the others and the lead passes on`() = runBlocking {
        val (_, rs) = group("Asha", "Ben", "Chitra")
        for (r in rs) r.t.publishSelf(RiderReport(r.id, r.name, fix(12.9), null, SafetyCode.NORMAL))
        assertTrue(rs[0].t.leave())
        val seen = assertNotNull(rs[1].t.fetch())
        assertEquals(2, seen.riders.size)
        assertTrue(seen.riders.none { it.id == rs[0].id })
        assertTrue(seen.leadId != rs[0].id && seen.leadId != null, "someone else leads now")
    }

    @Test fun `rejoining with saved credentials keeps the same rider and a stranger cannot read`() = runBlocking {
        val (code, rs) = group("Asha", "Ben")
        assertTrue(rs[1].t.leave())
        val back = assertNotNull(ArmorCloudTransport.joinRide(code, "Ben", ""))
        assertTrue(back.riderId != rs[1].id, "joining by name alone is a new rider (the app rejoins with its token instead)")
        val stranger = ArmorCloudTransport(code, "nope", "wrong")
        assertNull(stranger.fetch(), "no credentials, no ride")
        assertFalse(stranger.sendMessage(ChatMessage("x", "nope", "X", "hi", 0)))
    }

    @Test fun `an ended ride cannot be joined and ending needs the lead`() = runBlocking {
        val (code, rs) = group("Asha", "Ben")
        assertFalse(rs[1].t.end(), "only the lead ends it")
        assertTrue(rs[0].t.end())
        assertNull(ArmorCloudTransport.joinRide(code, "Late", ""))
    }
}
