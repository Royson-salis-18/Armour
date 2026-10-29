package com.armor.app.net

import com.armor.core.model.AlertKind
import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.HazardType
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import com.armor.core.model.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The ARMOR ride server (the same one the web app at [SERVER] runs on), so riders on the Android app and on the web
 * share one ride code. Rides live under /api/rides/CODE; every write is an action posted with the rider's id and token.
 *
 * The server owns alert ids, so the alerts this phone raises are mapped between its own ids and the server's.
 */
class ArmorCloudTransport(
    private val code: String,
    private val riderId: String,
    private val token: String,
    private val timeoutMs: Int = 8_000,
) : GroupTransport {
    private val base = "$SERVER/api/rides/$code"
    private val localToServer = HashMap<String, String>()
    private val serverToLocal = HashMap<String, String>()
    private var lastState: String? = null

    override suspend fun publishSelf(report: RiderReport): Boolean {
        val f = report.fix
        val loc = auth().put("lat", f.position.lat).put("lon", f.position.lon).put("acc", f.accuracyM).put("speed", f.speedMps.coerceIn(0.0, 150.0))
        f.bearingDeg?.let { loc.put("heading", ((it % 360) + 360) % 360) }
        val ok = post("location", loc) != null
        // A declared stop (fuel, break...) is the server's STOPPED state; clearing it means riding again.
        val state = report.status?.let { "STOPPED|${it.label}" } ?: "OK|"
        if (state != lastState) {
            val (s, reason) = state.split("|", limit = 2)
            if (post("state", auth().put("state", s).put("reason", reason)) != null) lastState = state
        }
        return ok
    }

    override suspend fun fetch(): GroupData? {
        val text = http("GET", base, null) ?: return null
        return try { parse(JSONObject(text)) } catch (e: JSONException) { null }
    }

    override suspend fun upsertAlert(alert: GroupAlert): Boolean {
        val body = auth()
        alert.position?.let { body.put("lat", it.lat).put("lon", it.lon) }
        return when (alert.kind) {
            AlertKind.TEAM_STOP -> {
                val reason = StopReason.entries.firstOrNull { it.name == alert.detail }?.label ?: alert.detail ?: "Stop"
                post("teamStop", body.put("reason", reason)) != null
            }
            AlertKind.REGROUP -> alert.position != null && post("regroup", body.put("label", alert.detail ?: "Regroup point")) != null
            AlertKind.HAZARD -> post("alert", body.put("kind", "HAZARD").put("hazard", alert.detail ?: "OTHER")) != null
            else -> {
                val kind = alert.kind.name
                val snap = post("alert", body.put("kind", kind).put("detail", alert.detail ?: "")) ?: return false
                // Learn the server's id for this alert, so resolving and the group's acknowledgements line up.
                try {
                    val arr = JSONObject(snap).getJSONArray("alerts")
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        if (it.optString("riderId") == riderId && it.optString("kind") == kind && it.isNull("resolvedAt")) {
                            localToServer[alert.id] = it.getString("id"); serverToLocal[it.getString("id")] = alert.id
                        }
                    }
                } catch (_: JSONException) {}
                true
            }
        }
    }

    override suspend fun resolveAlert(alertId: String): Boolean {
        if (alertId.startsWith(TEAM_STOP_PREFIX)) return post("resume", auth().put("cancel", true)) != null
        if (alertId.startsWith(REGROUP_PREFIX)) return post("regroup", auth().put("clear", true)) != null
        val id = localToServer[alertId] ?: alertId
        return post("resolve", auth().put("alertId", id)) != null
    }

    override suspend fun sendMessage(message: ChatMessage): Boolean = post("message", auth().put("text", message.text)) != null

    override suspend fun acknowledge(alertId: String, byId: String, byName: String): Boolean {
        if (alertId.startsWith(TEAM_STOP_PREFIX)) return post("confirmStop", auth().put("stopId", alertId.removePrefix(TEAM_STOP_PREFIX))) != null
        return post("ack", auth().put("alertId", localToServer[alertId] ?: alertId)) != null
    }

    /** The server keeps no per-rider audit log; the full trail stays on the phone. */
    override suspend fun pushEvents(riderId: String, events: List<RemoteEvent>): Boolean = true

    suspend fun start(): Boolean = post("start", auth()) != null
    suspend fun end(): Boolean = post("end", auth()) != null
    suspend fun leave(): Boolean = post("leave", auth()) != null

    // ---- parsing ----------------------------------------------------------------------------

    private fun parse(o: JSONObject): GroupData {
        // Server clock to phone clock, so "last seen" ages are right even if the phone's clock is off.
        val skew = System.currentTimeMillis() - o.optLong("serverTime", System.currentTimeMillis())
        fun t(ms: Long) = ms + skew

        val alerts = ArrayList<GroupAlert>()
        val alertsJson = o.optJSONArray("alerts")
        val emergency = HashMap<String, SafetyCode>()
        for (i in 0 until (alertsJson?.length() ?: 0)) {
            val a = alertsJson!!.getJSONObject(i)
            val kind = when (a.optString("kind")) {
                "SOS" -> AlertKind.SOS
                "CRASH" -> AlertKind.CRASH
                "HELP" -> AlertKind.HELP
                "STATIONARY" -> AlertKind.STATIONARY
                "LOW_BATTERY" -> AlertKind.LOW_BATTERY
                else -> null // stop requests and catch-ups are web-only plans
            } ?: continue
            val resolved = !a.isNull("resolvedAt")
            if (!resolved && (kind == AlertKind.SOS || kind == AlertKind.CRASH)) emergency[a.getString("riderId")] = if (kind == AlertKind.SOS) SafetyCode.SOS else SafetyCode.CRASH
            val ack = a.optJSONArray("acks")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("riderId") != a.getString("riderId") } }
            val pos = if (a.has("lat") && !a.isNull("lat")) LatLon(a.getDouble("lat"), a.getDouble("lon")) else null
            alerts += GroupAlert(
                id = serverToLocal[a.getString("id")] ?: a.getString("id"), riderId = a.getString("riderId"), riderName = a.optString("name", "Rider"),
                kind = kind, position = pos, raisedAtMs = t(a.optLong("at")), ackedById = ack?.optString("riderId"), ackedByName = ack?.optString("name"),
                resolved = resolved, detail = a.optString("detail").takeIf { it.isNotEmpty() },
            )
        }
        o.optJSONArray("hazards")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { h ->
                val type = when (h.optString("type")) { "GRAVEL", "WATER" -> HazardType.OIL; else -> HazardType.entries.firstOrNull { it.name == h.optString("type") } ?: HazardType.ROADWORK }
                alerts += GroupAlert(h.getString("id"), h.optString("riderId"), h.optString("by", "Rider"), AlertKind.HAZARD, LatLon(h.getDouble("lat"), h.getDouble("lon")), t(h.optLong("at")), detail = type.name)
            }
        }
        o.optJSONObject("teamStop")?.let { s ->
            val reason = StopReason.entries.firstOrNull { it.label.equals(s.optString("reason"), true) } ?: StopReason.OTHER
            alerts += GroupAlert(TEAM_STOP_PREFIX + s.getString("id"), s.optString("byId"), s.optString("by", "Lead"), AlertKind.TEAM_STOP, null, t(s.optLong("at")), detail = reason.name)
        }
        o.optJSONObject("regroup")?.let { g ->
            alerts += GroupAlert(REGROUP_PREFIX + g.optLong("at"), o.optString("leadId"), g.optString("by", "Lead"), AlertKind.REGROUP, LatLon(g.getDouble("lat"), g.getDouble("lon")), t(g.optLong("at")), detail = g.optString("label").takeIf { it.isNotEmpty() })
        }

        val riders = ArrayList<RiderReport>()
        val crew = ArrayList<GroupPhone>()
        o.optJSONArray("riders")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { r ->
                val id = r.getString("id")
                val name = r.optString("name", "Rider")
                r.optString("phone").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { crew += GroupPhone(id, name, it) }
                if (r.isNull("lat") || r.isNull("lon")) return@let
                val stopped = r.optString("state") == "STOPPED" || r.optString("state") == "OFF_BIKE"
                riders += RiderReport(
                    id = id, name = name,
                    fix = LocationFix(t(r.optLong("fixAt")), LatLon(r.getDouble("lat"), r.getDouble("lon")), r.optDouble("speed", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                        r.optDouble("heading").takeIf { !it.isNaN() }, r.optDouble("acc", 15.0).takeIf { !it.isNaN() } ?: 15.0),
                    status = if (stopped) com.armor.core.model.QuickStatus.BREAK else null,
                    safety = emergency[id] ?: SafetyCode.NORMAL,
                )
            }
        }

        val messages = ArrayList<ChatMessage>()
        o.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { m ->
                messages += ChatMessage(m.getString("id"), m.optString("riderId").takeIf { it.isNotEmpty() && it != "null" } ?: "system", m.optString("name", "ARMOR"), m.optString("text"), t(m.optLong("at")))
            }
        }
        return GroupData(riders, alerts, messages, o.optString("leadId").takeIf { it.isNotEmpty() }, crew)
    }

    // ---- HTTP -------------------------------------------------------------------------------

    private fun auth() = JSONObject().put("riderId", riderId).put("token", token)

    private suspend fun post(action: String, body: JSONObject) = http("POST", "$base/$action", body.toString())

    private suspend fun http(method: String, url: String, body: String?): String? = request(method, url, body, timeoutMs)

    companion object {
        /** Where ARMOR rides live. The web app is served from the same address, so invite links open there too. */
        val SERVER get() = server
        /** Overridable only so tests can point at a local server. */
        @Volatile internal var server = "https://armor-ride.onrender.com"
        private const val TEAM_STOP_PREFIX = "stop:"
        private const val REGROUP_PREFIX = "regroup:"

        /** The link that opens a ride on any phone: the web app, or the code to type into the Android app. */
        fun inviteLink(code: String) = "$SERVER/r/$code"

        /** Creates a ride; the creator leads it. The free server may take up to a minute to wake, so this waits. */
        suspend fun createRide(rideName: String, riderName: String, phone: String): CloudSession? =
            session("$SERVER/api/rides", JSONObject().put("name", rideName).put("riderName", riderName).put("phone", phone), lead = true)

        suspend fun joinRide(code: String, riderName: String, phone: String): CloudSession? =
            session("$SERVER/api/rides/${code.trim().uppercase()}/join", JSONObject().put("riderName", riderName).put("phone", phone), lead = false)

        private suspend fun session(url: String, body: JSONObject, lead: Boolean): CloudSession? {
            val text = request("POST", url, body.toString(), 75_000) ?: return null
            return try {
                val o = JSONObject(text)
                val ride = o.getJSONObject("ride")
                CloudSession(o.getString("code"), ride.optString("name"), o.getString("riderId"), o.getString("token"), lead || ride.optString("leadId") == o.getString("riderId"))
            } catch (e: JSONException) {
                null
            }
        }

        private suspend fun request(method: String, url: String, body: String?, timeoutMs: Int): String? = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}

/** This phone's place in a ride on the ARMOR server. */
data class CloudSession(val code: String, val rideName: String, val riderId: String, val token: String, val isLead: Boolean)
