package com.armor.app.net

import com.armor.core.model.AlertKind
import com.armor.core.model.GroupAlert
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import com.armor.core.model.QuickStatus
import com.armor.core.model.RiderReport
import com.armor.core.model.SafetyCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to a Firebase Realtime Database through its REST API (plain HTTPS + JSON), so the app needs no
 * Firebase SDK, no `google-services.json` and no Play Services. Layout under the database root:
 *
 * ```
 * groups/{code}/riders/{riderId}      latest report of each rider
 * groups/{code}/alerts/{alertId}      written only by the rider who raised it
 * groups/{code}/acks/{alertId}        written only by the rider who is coming to help
 * groups/{code}/resolved/{alertId}    true once the raiser is OK again
 * events/{code}/{riderId}/{eventKey}  synced audit trail
 * ```
 *
 * Splitting alerts/acks/resolved into separate nodes means each writer only ever replaces its own data
 * with a plain PUT, so an acknowledgement can never be overwritten by the raiser updating the alert.
 */
class FirebaseRestTransport(
    databaseUrl: String,
    private val groupCode: String,
    private val timeoutMs: Int = 4_000,
) : GroupTransport {
    private val root = databaseUrl.trim().trimEnd('/')
    private val group get() = "$root/groups/$groupCode"

    override suspend fun publishSelf(report: RiderReport): Boolean =
        put("$group/riders/${report.id}.json", report.toJson().toString())

    override suspend fun fetch(): GroupData? {
        val body = get("$group.json") ?: return null
        return try {
            parseGroup(body)
        } catch (e: JSONException) {
            null
        }
    }

    override suspend fun upsertAlert(alert: GroupAlert): Boolean =
        put("$group/alerts/${alert.id}.json", alert.toJson().toString())

    override suspend fun resolveAlert(alertId: String): Boolean = put("$group/resolved/$alertId.json", "true")

    override suspend fun acknowledge(alertId: String, byId: String, byName: String): Boolean =
        put("$group/acks/$alertId.json", JSONObject().put("id", byId).put("name", byName).toString())

    override suspend fun pushEvents(riderId: String, events: List<RemoteEvent>): Boolean {
        for (e in events) {
            val json = JSONObject().put("t", e.timeMs).put("type", e.type).put("detail", e.detail)
            if (!put("$root/events/$groupCode/$riderId/${e.key}.json", json.toString())) return false
        }
        return true
    }

    // ---- HTTP -------------------------------------------------------------------------------

    private suspend fun get(url: String): String? = request("GET", url, null)

    private suspend fun put(url: String, body: String): Boolean = request("PUT", url, body) != null

    private suspend fun request(method: String, url: String, body: String?): String? = withContext(Dispatchers.IO) {
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
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ---- JSON -------------------------------------------------------------------------------

    private fun parseGroup(body: String): GroupData {
        if (body.isBlank() || body.trim() == "null") return GroupData(emptyList(), emptyList())
        val root = JSONObject(body)
        val riders = root.optJSONObject("riders")?.let { o -> o.keys().asSequence().mapNotNull { o.optJSONObject(it)?.toReport() }.toList() }.orEmpty()
        val acks = root.optJSONObject("acks")
        val resolved = root.optJSONObject("resolved")
        val alerts = root.optJSONObject("alerts")?.let { o ->
            o.keys().asSequence().mapNotNull { id ->
                val ack = acks?.optJSONObject(id)
                o.optJSONObject(id)?.toAlert(
                    id = id,
                    ackedById = ack?.optString("id")?.takeIf { it.isNotEmpty() },
                    ackedByName = ack?.optString("name")?.takeIf { it.isNotEmpty() },
                    resolved = resolved?.optBoolean(id, false) == true,
                )
            }.toList()
        }.orEmpty()
        return GroupData(riders, alerts)
    }

    private fun RiderReport.toJson() = JSONObject()
        .put("id", id).put("name", name)
        .put("t", fix.timeMs).put("lat", fix.position.lat).put("lon", fix.position.lon)
        .put("spd", fix.speedMps).put("acc", fix.accuracyM)
        .apply {
            fix.bearingDeg?.let { put("brg", it) }
            status?.let { put("status", it.name) }
            put("safety", safety.name)
        }

    private fun JSONObject.toReport(): RiderReport? = try {
        RiderReport(
            id = getString("id"),
            name = optString("name", "Rider"),
            fix = LocationFix(
                timeMs = getLong("t"),
                position = LatLon(getDouble("lat"), getDouble("lon")),
                speedMps = optDouble("spd", 0.0),
                bearingDeg = if (has("brg")) getDouble("brg") else null,
                accuracyM = optDouble("acc", 50.0),
            ),
            status = optString("status").let { s -> QuickStatus.entries.firstOrNull { it.name == s } },
            safety = optString("safety").let { s -> SafetyCode.entries.firstOrNull { it.name == s } ?: SafetyCode.NORMAL },
        )
    } catch (e: JSONException) {
        null
    }

    private fun GroupAlert.toJson() = JSONObject()
        .put("riderId", riderId).put("riderName", riderName).put("kind", kind.name).put("t", raisedAtMs)
        .apply {
            position?.let { put("lat", it.lat); put("lon", it.lon) }
        }

    private fun JSONObject.toAlert(id: String, ackedById: String?, ackedByName: String?, resolved: Boolean): GroupAlert? = try {
        GroupAlert(
            id = id,
            riderId = getString("riderId"),
            riderName = optString("riderName", "Rider"),
            kind = AlertKind.entries.firstOrNull { it.name == optString("kind") } ?: AlertKind.STATIONARY,
            position = if (has("lat") && has("lon")) LatLon(getDouble("lat"), getDouble("lon")) else null,
            raisedAtMs = getLong("t"),
            ackedById = ackedById,
            ackedByName = ackedByName,
            resolved = resolved,
        )
    } catch (e: JSONException) {
        null
    }
}
