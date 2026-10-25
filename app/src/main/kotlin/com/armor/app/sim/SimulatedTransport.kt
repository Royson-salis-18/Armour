package com.armor.app.sim

import com.armor.app.net.GroupData
import com.armor.app.net.GroupTransport
import com.armor.app.net.RemoteEvent
import com.armor.core.model.GroupAlert
import com.armor.core.model.RiderReport

/** Plays the part of the remote group in demo mode: instant, always online, nothing leaves the phone. */
class SimulatedTransport(private val demo: DemoRide, private val clock: () -> Long) : GroupTransport {
    override suspend fun publishSelf(report: RiderReport) = true

    override suspend fun fetch() = GroupData(demo.peerReports(clock()), demo.alerts())

    override suspend fun upsertAlert(alert: GroupAlert): Boolean {
        demo.acceptAlert(alert, clock())
        return true
    }

    override suspend fun resolveAlert(alertId: String): Boolean {
        demo.resolveAlert(alertId)
        return true
    }

    override suspend fun acknowledge(alertId: String, byId: String, byName: String): Boolean {
        demo.acknowledge(alertId, byId, byName)
        return true
    }

    override suspend fun pushEvents(riderId: String, events: List<RemoteEvent>) = true
}
