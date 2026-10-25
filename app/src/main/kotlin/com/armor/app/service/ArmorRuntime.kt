package com.armor.app.service

import com.armor.app.sim.DemoAction
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.QuickStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The seam between the UI and the ride service, which share a process. The service publishes state here and
 * registers a sink that receives the UI's commands; the UI never touches the engine directly.
 */
object ArmorRuntime {
    sealed interface Command {
        data object RiderOk : Command
        data object ManualSos : Command
        data class SetStatus(val status: QuickStatus?) : Command
        data class Acknowledge(val alertId: String) : Command
        data class Dismiss(val alertId: String) : Command
        data class Demo(val action: DemoAction) : Command
        data class SmsResult(val phone: String, val ok: Boolean, val attempt: Int) : Command
        data object Stop : Command
    }

    val snapshot = MutableStateFlow<RideSnapshot?>(null)
    val running = MutableStateFlow(false)
    val demoMode = MutableStateFlow(false)
    val bluetoothAudio = MutableStateFlow<List<String>>(emptyList())

    /** Human-readable progress of the SOS message delivery, shown on the SOS screen. */
    val sosStatus = MutableStateFlow<String?>(null)

    /** Set when GPS/permission trouble means the ride is not fully protected. */
    val warning = MutableStateFlow<String?>(null)

    @Volatile
    private var sink: ((Command) -> Unit)? = null

    fun attach(sink: (Command) -> Unit) { this.sink = sink }

    fun detach() {
        sink = null
        snapshot.value = null
        running.value = false
        demoMode.value = false
        sosStatus.value = null
        warning.value = null
        bluetoothAudio.value = emptyList()
    }

    fun send(command: Command) { sink?.invoke(command) }
}
