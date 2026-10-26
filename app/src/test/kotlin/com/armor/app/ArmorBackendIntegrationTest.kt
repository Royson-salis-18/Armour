package com.armor.app

import com.armor.app.net.ArmorBackendTransport
import com.armor.core.model.AlertKind
import com.armor.core.model.ChatMessage
import com.armor.core.model.StopReason
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against a real ARMOR server (the FastAPI app in the ARMOR repo). Skipped unless `ARMOR_BACKEND_URL` is set, e.g.
 * `ARMOR_BACKEND_URL=http://127.0.0.1:8000`. It creates its own ride, so it can be run repeatedly.
 */
@EnabledIfEnvironmentVariable(named = "ARMOR_BACKEND_URL", matches = ".+")
class ArmorBackendIntegrationTest {
    private val url = System.getenv("ARMOR_BACKEND_URL") ?: ""

    private fun report(id: String, name: String, lat: Double, safety: SafetyCode = SafetyCode.NORMAL, status: QuickStatus? = null) =
        RiderReport(id, name, LocationFix(System.currentTimeMillis(), LatLon(lat, 77.5), 12.0, null, 6.0), status, safety)

    @Test fun `two riders create join share positions and a help alert end to end`() = runBlocking {
        val admin = assertNotNull(ArmorBackendTransport.createRide(url, "Sunday run", "Asha"))
        assertTrue(admin.isAdmin)
        assertEquals(6, admin.code.length)
        val member = assertNotNull(ArmorBackendTransport.joinRide(url, admin.code.lowercase(), "Kabir"))
        assertEquals(admin.rideId, member.rideId)

        val a = ArmorBackendTransport(url, admin.rideId)
        val b = ArmorBackendTransport(url, member.rideId)
        assertTrue(a.startRide())

        assertTrue(a.publishSelf(report(admin.riderId, "Asha", 12.9700)))
        assertTrue(b.publishSelf(report(member.riderId, "Kabir", 12.9710, SafetyCode.CHECKING, QuickStatus.FUEL)))

        val seen = assertNotNull(a.fetch())
        assertEquals(setOf("Asha", "Kabir"), seen.riders.map { it.name }.toSet())
        val kabir = seen.riders.single { it.id == member.riderId }
        assertEquals(SafetyCode.CHECKING, kabir.safety)
        assertEquals(QuickStatus.FUEL, kabir.status)
        assertEquals(12.9710, kabir.fix.position.lat, 1e-9)
        assertEquals(12.0, kabir.fix.speedMps, 0.01)
        assertTrue(kotlin.math.abs(System.currentTimeMillis() - kabir.fix.timeMs) < 60_000, "timestamp should parse to roughly now")

        val alert = GroupAlert("help-1", member.riderId, "Kabir", AlertKind.HELP, LatLon(12.9710, 77.5), System.currentTimeMillis())
        assertTrue(b.upsertAlert(alert))
        val open = assertNotNull(a.fetch()).alerts.single()
        assertEquals(AlertKind.HELP, open.kind)
        assertEquals(member.riderId, open.riderId)
        assertEquals(null, open.ackedById)

        assertTrue(a.acknowledge("help-1", admin.riderId, "Asha"))
        val acked = assertNotNull(b.fetch()).alerts.single()
        assertEquals("Asha", acked.ackedByName)

        assertTrue(b.resolveAlert("help-1"))
        assertTrue(assertNotNull(a.fetch()).alerts.single().resolved)

        assertTrue(a.endRide())
    }

    @Test fun `lead, team stop with its reason, and chat travel through the server`() = runBlocking {
        val lead = assertNotNull(ArmorBackendTransport.createRide(url, "Coast road", "Arjun"))
        val rider = assertNotNull(ArmorBackendTransport.joinRide(url, lead.code, "Meera"))
        val a = ArmorBackendTransport(url, lead.rideId)
        val b = ArmorBackendTransport(url, rider.rideId)
        assertTrue(a.publishSelf(report(lead.riderId, "Arjun", 12.9)))

        // The ride's creator is the lead, as the server records it.
        assertEquals(lead.riderId, assertNotNull(b.fetch()).leadId)

        // Lead calls a team stop; the reason survives the trip and the rider sees it.
        val stop = GroupAlert("stop-1", lead.riderId, "Arjun", AlertKind.TEAM_STOP, LatLon(12.9, 77.5), System.currentTimeMillis(), detail = StopReason.FUEL.name)
        assertTrue(a.upsertAlert(stop))
        val seen = assertNotNull(b.fetch()).alerts.single { it.id == "stop-1" }
        assertEquals(AlertKind.TEAM_STOP, seen.kind)
        assertEquals(StopReason.FUEL.name, seen.detail)

        // Chat both ways, oldest first, ids preserved so the engine can de-duplicate.
        val now = System.currentTimeMillis()
        assertTrue(b.sendMessage(ChatMessage("m1", rider.riderId, "Meera", "Stopping now, tank is low", now)))
        assertTrue(a.sendMessage(ChatMessage("m2", lead.riderId, "Arjun", "Copy. Chai after fuel", now + 1)))
        val msgs = assertNotNull(a.fetch()).messages.sortedBy { it.timeMs }
        assertEquals(listOf("m1", "m2"), msgs.map { it.id })
        assertEquals("Stopping now, tank is low", msgs.first().text)
        assertEquals("Meera", msgs.first().riderName)

        // The lead releases the group.
        assertTrue(a.resolveAlert("stop-1"))
        assertTrue(assertNotNull(b.fetch()).alerts.single { it.id == "stop-1" }.resolved)
    }

    @Test fun `joining with a wrong code fails cleanly`() = runBlocking {
        assertEquals(null, ArmorBackendTransport.joinRide(url, "ZZZZZZ", "Nobody"))
    }

    @Test fun `an unreachable server returns null rather than throwing`() = runBlocking {
        assertEquals(null, ArmorBackendTransport("http://127.0.0.1:9", "x", timeoutMs = 500).fetch())
    }
}
