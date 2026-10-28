package com.armor.app.net

import com.armor.core.model.ChatMessage
import com.armor.core.model.GroupAlert
import com.armor.core.model.RiderReport

/** A rider in the group and the number to reach them on: who an SOS is texted and called to besides the emergency contacts. */
data class GroupPhone(val id: String, val name: String, val phone: String)

data class GroupData(
    val riders: List<RiderReport>,
    val alerts: List<GroupAlert>,
    val messages: List<ChatMessage> = emptyList(),
    /** Rider id of the group's lead, when the server knows it. */
    val leadId: String? = null,
    /** Everyone in the ride who has shared a phone number, including you. */
    val crew: List<GroupPhone> = emptyList(),
)

/** A remote ride group: a shared place where riders publish themselves and raise/acknowledge alerts. */
interface GroupTransport {
    suspend fun publishSelf(report: RiderReport): Boolean

    /** Everything currently in the group, or null if it could not be reached. */
    suspend fun fetch(): GroupData?

    /** Creates or replaces an alert owned by the rider who raised it. */
    suspend fun upsertAlert(alert: GroupAlert): Boolean

    suspend fun resolveAlert(alertId: String): Boolean

    /** Posts a chat message to the group. Transports without chat just report failure. */
    suspend fun sendMessage(message: ChatMessage): Boolean = false

    suspend fun acknowledge(alertId: String, byId: String, byName: String): Boolean

    /** Appends audit-trail entries to the group's remote log. */
    suspend fun pushEvents(riderId: String, events: List<RemoteEvent>): Boolean
}

data class RemoteEvent(val key: String, val timeMs: Long, val type: String, val detail: String)
