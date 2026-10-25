package com.armor.app

import com.armor.app.net.FirebaseRestTransport
import com.armor.app.net.RemoteEvent
import com.armor.core.model.AlertKind
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the REST transport against a small in-process imitation of Firebase's path-based JSON store. */
class FirebaseRestTransportTest {
    private lateinit var server: HttpServer
    private val store = ConcurrentHashMap<String, String>() // "/groups/ABC123/riders/me" -> raw JSON
    private var forceStatus: Int? = null
    private lateinit var url: String

    @BeforeTest fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex -> handle(ex) }
        server.start()
        url = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest fun stop() = server.stop(0)

    private fun handle(ex: HttpExchange) {
        val status = forceStatus
        if (status != null) { ex.sendResponseHeaders(status, -1); ex.close(); return }
        val path = ex.requestURI.path.removeSuffix(".json")
        when (ex.requestMethod) {
            "PUT" -> { store[path] = ex.requestBody.readBytes().toString(Charsets.UTF_8); respond(ex, "{}") }
            "GET" -> {
                val children = store.filterKeys { it.startsWith("$path/") }
                if (children.isEmpty()) return respond(ex, "null")
                val root = JSONObject()
                for ((k, v) in children) {
                    var node = root
                    val parts = k.removePrefix("$path/").split('/')
                    parts.dropLast(1).forEach { p -> node = node.optJSONObject(p) ?: JSONObject().also { node.put(p, it) } }
                    node.put(parts.last(), org.json.JSONTokener(v).nextValue())
                }
                respond(ex, root.toString())
            }
            else -> { ex.sendResponseHeaders(405, -1); ex.close() }
        }
    }

    private fun respond(ex: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private val here = LatLon(12.9716, 77.5946)
    private fun report(id: String, status: QuickStatus? = null, bearing: Double? = 45.0) =
        RiderReport(id, "Rider $id", LocationFix(1_700_000_000_000, here, 12.5, bearing, 6.0), status, SafetyCode.NORMAL)

    @Test fun `a published report comes back intact for other riders`() = runBlocking {
        val tx = FirebaseRestTransport(url, "ABC123")
        assertTrue(tx.publishSelf(report("arjun", QuickStatus.FUEL)))
        assertTrue(tx.publishSelf(report("meera", bearing = null)))

        val data = assertNotNull(FirebaseRestTransport(url, "ABC123").fetch())
        assertEquals(2, data.riders.size)
        val arjun = data.riders.single { it.id == "arjun" }
        assertEquals("Rider arjun", arjun.name)
        assertEquals(QuickStatus.FUEL, arjun.status)
        assertEquals(12.5, arjun.fix.speedMps, 1e-9)
        assertEquals(45.0, arjun.fix.bearingDeg)
        assertEquals(here, arjun.fix.position)
        assertEquals(1_700_000_000_000, arjun.fix.timeMs)
        assertNull(data.riders.single { it.id == "meera" }.fix.bearingDeg)
        assertNull(data.riders.single { it.id == "meera" }.status)
    }

    @Test fun `groups are isolated by code`() = runBlocking {
        FirebaseRestTransport(url, "AAAAAA").publishSelf(report("a"))
        FirebaseRestTransport(url, "BBBBBB").publishSelf(report("b"))
        assertEquals(listOf("a"), FirebaseRestTransport(url, "AAAAAA").fetch()!!.riders.map { it.id })
    }

    @Test fun `an empty group is an empty result, not a failure`() = runBlocking {
        val data = FirebaseRestTransport(url, "NOBODY").fetch()
        assertNotNull(data)
        assertTrue(data.riders.isEmpty() && data.alerts.isEmpty())
    }

    @Test fun `alert acknowledgement and resolution survive the raiser re-publishing the alert`() = runBlocking {
        val raiser = FirebaseRestTransport(url, "ABC123")
        val helper = FirebaseRestTransport(url, "ABC123")
        val alert = GroupAlert("arjun-1", "arjun", "Arjun", AlertKind.STATIONARY, here, 1_700_000_000_000)

        assertTrue(raiser.upsertAlert(alert))
        assertTrue(helper.acknowledge("arjun-1", "meera", "Meera"))
        // The raiser escalates the same alert to SOS: this must not erase Meera's acknowledgement.
        assertTrue(raiser.upsertAlert(alert.copy(kind = AlertKind.SOS)))

        var seen = helper.fetch()!!.alerts.single()
        assertEquals(AlertKind.SOS, seen.kind)
        assertEquals("meera", seen.ackedById)
        assertEquals("Meera", seen.ackedByName)
        assertFalse(seen.resolved)

        assertTrue(raiser.resolveAlert("arjun-1"))
        seen = helper.fetch()!!.alerts.single()
        assertTrue(seen.resolved)
    }

    @Test fun `an alert raised before the first GPS fix has no position`() = runBlocking {
        val tx = FirebaseRestTransport(url, "ABC123")
        tx.upsertAlert(GroupAlert("x-1", "x", "X", AlertKind.SOS, null, 5))
        assertNull(tx.fetch()!!.alerts.single().position)
    }

    @Test fun `events are written under the group and rider`() = runBlocking {
        val tx = FirebaseRestTransport(url, "ABC123")
        assertTrue(tx.pushEvents("me", listOf(RemoteEvent("100-1", 100, "SOS_SENT", "why"), RemoteEvent("200-2", 200, "RIDER_OK", "fine"))))
        val saved = JSONObject(store.getValue("/events/ABC123/me/100-1"))
        assertEquals("SOS_SENT", saved.getString("type"))
        assertEquals(100, saved.getLong("t"))
        assertTrue(store.containsKey("/events/ABC123/me/200-2"))
    }

    @Test fun `server errors and unreachable servers fail quietly`() = runBlocking {
        forceStatus = 401
        val denied = FirebaseRestTransport(url, "ABC123")
        assertFalse(denied.publishSelf(report("a")))
        assertNull(denied.fetch())

        forceStatus = null
        val dead = FirebaseRestTransport("http://127.0.0.1:1", "ABC123", timeoutMs = 500)
        assertFalse(dead.publishSelf(report("a")))
        assertNull(dead.fetch())
        assertFalse(dead.upsertAlert(GroupAlert("a-1", "a", "A", AlertKind.SOS, here, 1)))
    }

    @Test fun `a trailing slash on the database url is tolerated`() = runBlocking {
        assertTrue(FirebaseRestTransport("$url/", "ABC123").publishSelf(report("a")))
        assertTrue(store.containsKey("/groups/ABC123/riders/a"))
    }

    @Test fun `corrupt records are skipped rather than failing the whole fetch`() = runBlocking {
        FirebaseRestTransport(url, "ABC123").publishSelf(report("good"))
        store["/groups/ABC123/riders/bad"] = """{"id":"bad"}"""
        val riders = FirebaseRestTransport(url, "ABC123").fetch()!!.riders
        assertEquals(listOf("good"), riders.map { it.id })
    }
}
