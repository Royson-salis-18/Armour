package com.armor.app.net

import com.armor.core.model.GroupAlert
import com.armor.core.model.RiderReport

data class GroupData(val riders: List<RiderReport>, val alerts: List<GroupAlert>)

/** A remote ride group: a shared place where riders publish themselves and raise/acknowledge alerts. */
interface GroupTransport {
    suspend fun publishSelf(report: RiderReport): Boolean

    /** Everything currently in the group, or null if it could not be reached. */
    suspend fun fetch(): GroupData?

    /** Creates or replaces an alert owned by the rider who raised it. */
    suspend fun upsertAlert(alert: GroupAlert): Boolean

    suspend fun resolveAlert(alertId: String): Boolean

    suspend fun acknowledge(alertId: String, byId: String, byName: String): Boolean

    /** Appends audit-trail entries to the group's remote log. */
    suspend fun pushEvents(riderId: String, events: List<RemoteEvent>): Boolean
}

data class RemoteEvent(val key: String, val timeMs: Long, val type: String, val detail: String)
