package com.armor.app

import com.armor.app.net.ArmorCloudTransport
import com.armor.core.model.AlertKind
import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import com.armor.core.model.StopReason
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against the ARMOR ride server in web/ (`node web/server.js`). Skipped unless `ARMOR_WEB_URL` is set, e.g.
 * `ARMOR_WEB_URL=http://127.0.0.1:8099`. Each test makes its own ride.
 */
@EnabledIfEnvironmentVariable(named = "ARMOR_WEB_URL", matches = ".+")
class ArmorCloudIntegrationTest {
    init { ArmorCloudTransport.server = System.getenv("ARMOR_WEB_URL") ?: "" }

    private fun report(id: String, name: String, lat: Double, status: QuickStatus? = null) =
        RiderReport(id, name, LocationFix(System.currentTimeMillis(), LatLon(lat, 77.5), 12.0, 90.0, 6.0), status, SafetyCode.NORMAL)

    @Test fun `create join share positions phones chat and an SOS that the other rider acknowledges`() = runBlocking {
        val lead = assertNotNull(ArmorCloudTransport.createRide("Sunday run", "Asha", "+919800000001"))
        assertTrue(lead.isLead)
        val member = assertNotNull(ArmorCloudTransport.joinRide(lead.code.lowercase(), "Kabir", "9800000002"))
        assertEquals(lead.code, member.code)
        // The lead's phone keeps its own rider id; the server's id for it is translated on the way in.
        val a = ArmorCloudTransport(lead.code, lead.riderId, lead.token, localId = "asha-phone")
        val b = ArmorCloudTransport(member.code, member.riderId, member.token)
        assertTrue(a.start())

        assertTrue(a.publishSelf(report("asha-phone", "Asha", 12.9700)))
        assertTrue(b.publishSelf(report(member.riderId, "Kabir", 12.9710, QuickStatus.FUEL)))
        val seen = assertNotNull(a.fetch())
        assertEquals(2, seen.riders.size)
        val kabir = seen.riders.first { it.id == member.riderId }
        assertEquals(12.9710, kabir.fix.position.lat, 1e-9)
        assertEquals(12.0, kabir.fix.speedMps, 1e-9)
        assertTrue(kabir.status != null, "a declared stop shows as stopped")
        assertEquals(setOf("+919800000001", "9800000002"), seen.crew.map { it.phone }.toSet())
        assertEquals("asha-phone", seen.leadId)
        assertTrue(seen.riders.any { it.id == "asha-phone" }, "the lead sees itself under its own id, not as a stranger")
        // Kabir's phone sees the lead under the server id, which is how it addresses everyone else.
        assertEquals(lead.riderId, assertNotNull(b.fetch()).leadId)

        assertTrue(b.sendMessage(ChatMessage("m1", member.riderId, "Kabir", "fuel at the next pump", 0)))
        assertTrue(assertNotNull(a.fetch()).messages.any { it.text == "fuel at the next pump" && it.riderId == member.riderId })

        // Kabir's SOS: Asha sees it, acknowledges, Kabir sees who is coming, then resolves it with his own id.
        val sos = GroupAlert("local-sos-1", member.riderId, "Kabir", AlertKind.SOS, LatLon(12.9710, 77.5), System.currentTimeMillis())
        assertTrue(b.upsertAlert(sos))
        val onA = assertNotNull(a.fetch()).alerts.first { it.kind == AlertKind.SOS }
        assertEquals(SafetyCode.SOS, assertNotNull(a.fetch()).riders.first { it.id == member.riderId }.safety)
        assertTrue(a.acknowledge(onA.id, "asha-phone", "Asha"))
        val onB = assertNotNull(b.fetch()).alerts.first { it.kind == AlertKind.SOS }
        assertEquals("local-sos-1", onB.id, "the raiser sees its own id")
        assertEquals("Asha", onB.ackedByName)
        assertTrue(b.resolveAlert("local-sos-1"))
        assertTrue(assertNotNull(a.fetch()).alerts.first { it.kind == AlertKind.SOS }.resolved)

        // Team stop from the lead, confirmed by the rider, then lifted.
        assertTrue(a.upsertAlert(GroupAlert("ts", "asha-phone", "Asha", AlertKind.TEAM_STOP, null, 0, detail = StopReason.FUEL.name)))
        val stop = assertNotNull(b.fetch()).alerts.first { it.kind == AlertKind.TEAM_STOP }
        assertEquals(StopReason.FUEL.name, stop.detail)
        assertTrue(b.acknowledge(stop.id, member.riderId, "Kabir"))
        assertTrue(a.resolveAlert(stop.id))
        assertTrue(assertNotNull(b.fetch()).alerts.none { it.kind == AlertKind.TEAM_STOP })
        assertTrue(a.end())
    }
}
