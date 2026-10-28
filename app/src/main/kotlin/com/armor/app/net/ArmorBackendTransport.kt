package com.armor.app.net

import com.armor.core.model.AlertKind
import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** What the ARMOR server returns when a ride is created or joined. */
data class RideSession(val rideId: String, val code: String, val rideName: String, val riderId: String, val isAdmin: Boolean)

/**
 * Client for the ARMOR FastAPI server (rides, riders, locations, events). Rides are created/joined with a short code;
 * the server has no push channel, so everything is polled.
 *
 * The server has no concept of alerts, so they are carried as `events`: an `alert` event holds the alert, an
 * `acknowledgement` event says who is coming, and a `status_update` event with `resolvedAlertId` closes it. The newest
 * event for an alert id wins when the group is rebuilt.
 */
class ArmorBackendTransport(
    baseUrl: String,
    private val rideId: String,
    private val timeoutMs: Int = 4_000,
) : GroupTransport {
    private val api = baseUrl.trim().trimEnd('/') + "/api"
    private var lastStatus: String? = null

    override suspend fun publishSelf(report: RiderReport): Boolean {
        val loc = JSONObject()
            .put("ride_id", rideId).put("rider_id", report.id)
            .put("latitude", report.fix.position.lat).put("longitude", report.fix.position.lon)
            .put("speed", report.fix.speedMps * 3.6).put("accuracy", report.fix.accuracyM)
        val status = JSONObject().put("rider_id", report.id).put("status", report.safety.toServer())
        report.status?.let { status.put("manual_status", it.name) }
        val a = http("POST", "$api/locations", loc.toString()) != null
        // The server answers 404 when a status is set to the value it already has, so that counts as success, and it is
        // only sent when it changes anyway.
        val key = "${report.safety}|${report.status}"
        val b = lastStatus == key || http("POST", "$api/riders/${report.id}/status", status.toString(), tolerate = 404) != null
        if (b) lastStatus = key
        return a && b
    }

    override suspend fun fetch(): GroupData? {
        val ride = http("GET", "$api/rides/$rideId", null) ?: return null
        val locs = http("GET", "$api/locations/$rideId", null) ?: return null
        val events = http("GET", "$api/events/$rideId?limit=200", null) ?: return null
        return try {
            parse(JSONObject(ride), JSONArray(locs), JSONArray(events))
        } catch (e: JSONException) {
            null
        }
    }

    override suspend fun upsertAlert(alert: GroupAlert): Boolean {
        val data = JSONObject().put("alertId", alert.id).put("kind", alert.kind.name).put("riderName", alert.riderName).put("raisedAtMs", alert.raisedAtMs)
        alert.position?.let { data.put("lat", it.lat).put("lon", it.lon) }
        alert.detail?.let { data.put("detail", it) }
        return postEvent(alert.riderId, "alert", if (alert.kind == AlertKind.HELP || alert.kind == AlertKind.STATIONARY || alert.kind == AlertKind.TEAM_STOP) "warning" else "critical", data)
    }

    override suspend fun sendMessage(message: ChatMessage): Boolean =
        postEvent(message.riderId, "status_update", "info", JSONObject().put("message", JSONObject().put("id", message.id).put("name", message.riderName).put("text", message.text).put("t", message.timeMs)))

    override suspend fun resolveAlert(alertId: String): Boolean =
        postEvent(rider = null, "status_update", "info", JSONObject().put("resolvedAlertId", alertId))

    override suspend fun acknowledge(alertId: String, byId: String, byName: String): Boolean =
        postEvent(byId, "acknowledgement", "info", JSONObject().put("alertId", alertId).put("byId", byId).put("byName", byName))

    override suspend fun pushEvents(riderId: String, events: List<RemoteEvent>): Boolean {
        for (e in events) {
            val data = JSONObject().put("key", e.key).put("timeMs", e.timeMs).put("type", e.type).put("detail", e.detail)
            if (!postEvent(riderId, "status_update", "info", data)) return false
        }
        return true
    }

    private suspend fun postEvent(rider: String?, type: String, severity: String, data: JSONObject): Boolean {
        // The server insists on a rider id; resolve markers are attributed to the ride's creator-agnostic "system".
        val body = JSONObject().put("ride_id", rideId).put("rider_id", rider ?: "system").put("event_type", type).put("severity", severity).put("data", data)
        return http("POST", "$api/events", body.toString()) != null
    }

    // ---- parsing ----------------------------------------------------------------------------

    private fun parse(ride: JSONObject, locs: JSONArray, events: JSONArray): GroupData {
        val names = HashMap<String, String>()
        val crew = ArrayList<GroupPhone>()
        val statuses = HashMap<String, Pair<String, String?>>()
        ride.getJSONArray("riders").let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                names[it.getString("id")] = it.optString("name", "Rider")
                it.optString("phone").trim().takeIf { p -> p.isNotEmpty() && p != "null" }?.let { p -> crew += GroupPhone(it.getString("id"), it.optString("name", "Rider"), p) }
                statuses[it.getString("id")] = it.optString("status", "active") to it.optString("manual_status").takeIf { m -> m.isNotEmpty() && m != "null" }
            }
        }
        val riders = ArrayList<RiderReport>()
        for (i in 0 until locs.length()) {
            val l = locs.getJSONObject(i)
            val id = l.getString("rider_id")
            val (st, manual) = statuses[id] ?: ("active" to null)
            riders += RiderReport(
                id = id, name = names[id] ?: "Rider",
                fix = LocationFix(parseTime(l.getString("timestamp")), LatLon(l.getDouble("latitude"), l.getDouble("longitude")), l.optDouble("speed", 0.0) / 3.6, null, l.optDouble("accuracy", 10.0).takeIf { !it.isNaN() } ?: 10.0),
                status = manual?.let { m -> QuickStatus.entries.firstOrNull { it.name == m } },
                safety = st.toSafety(),
            )
        }

        val alerts = LinkedHashMap<String, GroupAlert>()
        val acks = HashMap<String, Pair<String, String>>()
        val resolved = HashSet<String>()
        val messages = ArrayList<ChatMessage>()
        for (i in events.length() - 1 downTo 0) { // server returns newest first; replay oldest → newest
            val e = events.getJSONObject(i)
            val d = e.optJSONObject("data") ?: continue
            when (e.optString("event_type")) {
                "alert" -> {
                    val id = d.optString("alertId")
                    if (id.isEmpty()) continue
                    val kind = AlertKind.entries.firstOrNull { it.name == d.optString("kind") } ?: AlertKind.STATIONARY
                    val pos = if (d.has("lat") && d.has("lon")) LatLon(d.getDouble("lat"), d.getDouble("lon")) else null
                    alerts[id] = GroupAlert(id, e.getString("rider_id"), d.optString("riderName", names[e.getString("rider_id")] ?: "Rider"), kind, pos, d.optLong("raisedAtMs"), detail = d.optString("detail").takeIf { it.isNotEmpty() })
                }
                "acknowledgement" -> d.optString("alertId").takeIf { it.isNotEmpty() }?.let { acks.putIfAbsent(it, d.optString("byId") to d.optString("byName")) }
                "status_update" -> {
                    d.optString("resolvedAlertId").takeIf { it.isNotEmpty() }?.let { resolved += it }
                    d.optJSONObject("message")?.let { m ->
                        val text = m.optString("text")
                        if (text.isNotEmpty()) messages += ChatMessage(m.optString("id", "m$i"), e.getString("rider_id"), m.optString("name", "Rider"), text, m.optLong("t"))
                    }
                }
            }
        }
        return GroupData(riders, alerts.values.map { a ->
            a.copy(ackedById = acks[a.id]?.first, ackedByName = acks[a.id]?.second, resolved = a.id in resolved)
        }, messages, ride.optJSONObject("ride")?.optString("admin_id")?.takeIf { it.isNotEmpty() }, crew)
    }

    // ---- HTTP -------------------------------------------------------------------------------

    private suspend fun http(method: String, url: String, body: String?, tolerate: Int? = null): String? = withContext(Dispatchers.IO) {
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
            val code = conn.responseCode
            when {
                code in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                code == tolerate -> ""
                else -> null
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        fun SafetyCode.toServer() = when (this) {
            SafetyCode.NORMAL -> "active"
            SafetyCode.CHECKING -> "stationary"
            SafetyCode.ALERTED -> "warning"
            SafetyCode.CRASH, SafetyCode.SOS -> "emergency"
        }

        fun String.toSafety() = when (this) {
            "stationary" -> SafetyCode.CHECKING
            "warning" -> SafetyCode.ALERTED
            "emergency" -> SafetyCode.SOS
            else -> SafetyCode.NORMAL
        }

        /** The server stamps naive UTC datetimes ("2026-10-26T10:00:00.123000"); treat missing offsets as UTC. */
        fun parseTime(s: String): Long = try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (e: java.time.format.DateTimeParseException) {
            LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
        }

        /** Creates a ride and becomes its admin. */
        suspend fun createRide(baseUrl: String, rideName: String, riderName: String, phone: String = ""): RideSession? =
            ArmorBackendTransport(baseUrl, "").session("rides", JSONObject().put("name", rideName).put("admin_name", riderName).put("admin_phone", phone), admin = true)

        /** Joins a ride by its code. */
        suspend fun joinRide(baseUrl: String, code: String, riderName: String, phone: String = ""): RideSession? =
            ArmorBackendTransport(baseUrl, "").session("rides/join", JSONObject().put("code", code.trim()).put("rider_name", riderName).put("rider_phone", phone), admin = false)
    }

    private suspend fun session(path: String, body: JSONObject, admin: Boolean): RideSession? {
        val text = http("POST", "$api/$path", body.toString()) ?: return null
        return try {
            val o = JSONObject(text)
            val ride = o.getJSONObject("ride")
            val me = o.optJSONObject(if (admin) "admin" else "rider") ?: return null
            RideSession(ride.getString("id"), ride.getString("code"), ride.optString("name"), me.getString("id"), admin)
        } catch (e: JSONException) {
            null
        }
    }

    suspend fun startRide(): Boolean = http("POST", "$api/rides/$rideId/start", "{}") != null
    suspend fun endRide(): Boolean = http("POST", "$api/rides/$rideId/end", "{}") != null
}
