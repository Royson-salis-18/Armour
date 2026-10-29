package com.armor.app.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import com.armor.app.ArmorApp
import com.armor.app.requireService
import com.armor.app.alert.AlertController
import com.armor.app.alert.Notifications
import com.armor.app.alert.SmsSender
import com.armor.app.alert.SosCaller
import com.armor.app.data.ContactEntity
import com.armor.app.data.EventRecorder
import com.armor.app.data.Settings
import com.armor.app.net.ConnectivityMonitor
import com.armor.app.net.ArmorBackendTransport
import com.armor.app.net.FirebaseRestTransport
import com.armor.app.net.GroupTransport
import com.armor.app.net.RemoteEvent
import com.armor.app.sensors.BatteryMonitor
import com.armor.app.sensors.BluetoothAudioMonitor
import com.armor.app.sensors.GpsLocationSource
import com.armor.app.sensors.PhoneMotionSource
import com.armor.app.sensors.RideClock
import com.armor.app.sim.DemoRide
import com.armor.app.sim.SimulatedTransport
import com.armor.core.engine.ArmorConfig
import com.armor.core.engine.EngineEffect
import com.armor.core.engine.RideEngine
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.OutlierAssessment
import com.armor.core.safety.EventType
import com.armor.core.safety.SafetyState
import com.armor.core.sos.SosMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import com.armor.app.alert.Haptics
import com.armor.core.model.AlertKind

/**
 * Keeps the ride alive in the foreground and wires the platform to the pure-Kotlin [RideEngine]: GPS, motion
 * sensors, connectivity and the group transport go in; alerts, SMS, notifications and the audit log come out.
 *
 * Every engine call happens on one dedicated thread (`handler`), which is what makes the engine's lack of
 * locking safe. Network and database work runs on [scope] and posts results back to `handler`.
 */
class RideService : Service() {
    private class Outbox(val key: String, val queuedAtMs: Long, val send: suspend (GroupTransport) -> Boolean)

    private class ActiveSos(val text: String, val recipients: List<ContactEntity>) {
        val delivered = HashSet<String>()
        val failedFinal = HashSet<String>()
        var reason: String? = null
    }

    private val app get() = application as ArmorApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var settings: Settings
    private lateinit var clock: RideClock
    private lateinit var engine: RideEngine
    private lateinit var alerts: AlertController
    private lateinit var recorder: EventRecorder
    private lateinit var sms: SmsSender
    private lateinit var caller: SosCaller
    private var callLine = ""
    private var lastSnap: RideSnapshot? = null

    private var started = false
    private var demo: DemoRide? = null
    private var transport: GroupTransport? = null
    private var batteryMonitor: BatteryMonitor? = null
    private var gps: GpsLocationSource? = null
    private var motion: PhoneMotionSource? = null
    private var connectivity: ConnectivityMonitor? = null
    private var bluetooth: BluetoothAudioMonitor? = null

    private var networkOnline = false
    private var bluetoothNames: List<String> = emptyList()
    private var activeSos: ActiveSos? = null
    private var tickCount = 0
    private var lastRideText = ""

    private val outbox = LinkedHashMap<String, Outbox>()
    private val flushing = AtomicBoolean(false)
    private val polling = AtomicBoolean(false)
    private val syncing = AtomicBoolean(false)

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!started) begin(demoRequested = intent.getBooleanExtra(EXTRA_DEMO, false), resumed = false)
            // Android restarted us after killing the process: carry on if a real ride was in progress.
            null -> if (!started) {
                if ((app.prefs.rideActive || app.prefs.current.alwaysOn) && hasLocationPermission()) begin(demoRequested = false, resumed = true) else stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (started) release()
        super.onDestroy()
    }

    // ---- lifecycle --------------------------------------------------------------------------

    private fun begin(demoRequested: Boolean, resumed: Boolean) {
        started = true
        try {
            val first = Notifications.ride(this, "Starting…")
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifications.ID_RIDE, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(Notifications.ID_RIDE, first)
        } catch (e: SecurityException) {
            started = false
            stopSelf()
            return
        }

        settings = app.prefs.current
        if (!demoRequested) app.prefs.rideActive = true

        thread = HandlerThread("armor-ride", Process.THREAD_PRIORITY_FOREGROUND).also { it.start() }
        handler = Handler(thread.looper)
        ArmorRuntime.attach { command -> handler.post { handle(command) } }
        ArmorRuntime.running.value = true
        ArmorRuntime.demoMode.value = demoRequested

        handler.post { initOnHandlerThread(demoRequested, resumed) }
    }

    private fun initOnHandlerThread(demoRequested: Boolean, resumed: Boolean) {
        clock = RideClock()
        sms = SmsSender(this)
        caller = SosCaller(this, handler) { msg ->
            callLine = msg
            recorder.record(EventType.SYSTEM.name, "SOS call: $msg", clock.now())
            activeSos?.let { refreshSosStatus(it) }
        }
        recorder = EventRecorder(app.db.events(), app.appScope)
        alerts = AlertController(this, handler)

        val name = settings.riderName.ifBlank { "Rider" }
        val base = ArmorConfig().withImpactThreshold(settings.sensitivity.impactG)
        val configured = base.copy(safety = base.safety.copy(soloMonitoring = settings.soloMonitoring))
        engine = RideEngine(settings.riderId, name, if (demoRequested) configured.forDemo() else configured)

        recorder.record(EventType.RIDE.name, if (resumed) "ride resumed after Android restarted the service" else if (demoRequested) "demo ride started" else "ride started", clock.now())

        if (demoRequested) {
            val d = DemoRide(settings.riderId, name)
            demo = d
            transport = SimulatedTransport(d, clock::now)
            networkOnline = true
        } else {
            gps = GpsLocationSource(this, clock, thread.looper, ::onFix, ::onProblem).also { it.start() }
            motion = PhoneMotionSource(this, clock, handler, engine::onAccel, engine::onGyro).also { m ->
                m.start()
                if (!m.hasAccelerometer) onProblem("No accelerometer: crash detection is unavailable on this phone.")
                else if (!m.hasGyroscope) recorder.record(EventType.SYSTEM.name, "no gyroscope: crash detection uses impact and tilt only", clock.now())
            }
            connectivity = ConnectivityMonitor(this, handler) { online -> networkOnline = online; pushConnectivity() }.also { it.start() }
            transport = when {
                settings.backendConfigured -> ArmorBackendTransport(settings.backendUrl, settings.rideId)
                settings.syncConfigured -> FirebaseRestTransport(settings.firebaseUrl, settings.groupCode)
                else -> null
            }
        }
        bluetooth = BluetoothAudioMonitor(this, handler) { names -> bluetoothNames = names; ArmorRuntime.bluetoothAudio.value = names; pushConnectivity() }.also { it.start() }
        pushConnectivity()
        checkReadiness()
        batteryMonitor = BatteryMonitor(this) { pct, charging -> handler.post { if (started) engine.onBattery(pct, charging, clock.now()) } }.also { it.start() }

        handler.post(tickRunnable)
    }

    private fun endRide() {
        if (!started) return
        recorder.record(EventType.RIDE.name, "ride ended", clock.now())
        ArmorRuntime.snapshot.value?.stats?.takeIf { it.elapsedMs >= 30_000 }?.let { ArmorRuntime.lastSummary.value = it }
        app.prefs.rideActive = false
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        started = false
        if (::caller.isInitialized) caller.stop()
        handler.removeCallbacksAndMessages(null)
        gps?.stop(); motion?.stop(); connectivity?.stop(); bluetooth?.stop(); batteryMonitor?.stop()
        alerts.release()
        requireService<NotificationManager>().cancel(Notifications.ID_ALERT)
        ArmorRuntime.detach()
        scope.cancel()
        thread.quitSafely()
    }

    // ---- inputs -----------------------------------------------------------------------------

    private fun onFix(fix: com.armor.core.model.LocationFix) = engine.onLocation(fix)

    private fun onProblem(message: String) {
        ArmorRuntime.warning.value = message
        recorder.record(EventType.SYSTEM.name, message, clock.now())
    }

    private fun pushConnectivity() {
        val intercom = settings.usesIntercom && bluetoothNames.isNotEmpty()
        engine.onConnectivity(networkOnline, intercom)
    }

    private fun checkReadiness() {
        scope.launch {
            val warnings = buildList {
                if (demo == null && app.db.contacts().snapshot().isEmpty()) add("No emergency contacts saved: an SOS will only reach your group.")
                if (demo == null && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) add("SMS permission is off: SOS cannot be texted.")
            }
            if (warnings.isNotEmpty()) ArmorRuntime.warning.value = warnings.joinToString("\n")
        }
    }

    private fun handle(command: ArmorRuntime.Command) {
        if (!started) return
        val now = clock.now()
        when (command) {
            ArmorRuntime.Command.RiderOk -> { ArmorRuntime.silentSos.value = false; engine.onRiderOk(now) }
            ArmorRuntime.Command.ManualSos -> engine.onManualSos(now)
            ArmorRuntime.Command.RequestHelp -> engine.requestHelp(now)
            is ArmorRuntime.Command.CallTeamStop -> engine.callTeamStop(command.reason, now)
            ArmorRuntime.Command.ResumeTeam -> engine.resumeTeam(now)
            is ArmorRuntime.Command.ConfirmTeamStop -> engine.confirmTeamStop(command.alertId, now)
            is ArmorRuntime.Command.SendMessage -> engine.sendMessage(command.text, now)
            is ArmorRuntime.Command.ReportHazard -> engine.reportHazard(command.type, now)
            is ArmorRuntime.Command.SetRegroup -> engine.snapshot?.self?.position?.let { engine.setRegroupPoint(it, command.label, now) }
            ArmorRuntime.Command.ClearRegroup -> engine.clearRegroupPoint()
            is ArmorRuntime.Command.PauseCrashDetection -> engine.setCrashDetectionPaused(command.paused, now)
            ArmorRuntime.Command.SilentSos -> { ArmorRuntime.silentSos.value = true; engine.onManualSos(now) }
            ArmorRuntime.Command.CancelHelp -> engine.cancelHelp(now)
            is ArmorRuntime.Command.SetStatus -> engine.setQuickStatus(command.status, now)
            is ArmorRuntime.Command.Acknowledge -> engine.acknowledgeIncoming(command.alertId, now)
            is ArmorRuntime.Command.Dismiss -> engine.dismissIncoming(command.alertId)
            is ArmorRuntime.Command.Demo -> demo?.apply(command.action, now)
            is ArmorRuntime.Command.SmsResult -> onSmsResult(command)
            ArmorRuntime.Command.Stop -> { endRide(); return }
        }
        step()
    }

    // ---- the loop ---------------------------------------------------------------------------

    private fun tick() {
        tickCount++
        step()
        if (tickCount % 2 == 0) { publishSelf(); flushOutbox() }
        if (tickCount % 3 == 0) poll()
        if (tickCount % 30 == 0) syncEvents()
    }

    private fun step() {
        val now = clock.now()
        demo?.let { d ->
            d.advance(now)
            d.motion(now, engine::onAccel, engine::onGyro)
            engine.onLocation(d.selfFix(now))
        }
        val snapshot = engine.tick(now)
        handleEffects(engine.drainEffects())
        ArmorRuntime.snapshot.value = snapshot
        alerts.update(snapshot)
        updateNotifications(snapshot)
    }

    private fun handleEffects(effects: List<EngineEffect>) {
        for (e in effects) when (e) {
            is EngineEffect.PublishAlert -> enqueue("alert:${e.alert.id}") { it.upsertAlert(e.alert) }
            is EngineEffect.PublishMessage -> enqueue("msg:${e.message.id}") { it.sendMessage(e.message) }
            is EngineEffect.ResolveAlert -> enqueue("resolve:${e.alertId}") { it.resolveAlert(e.alertId) }
            is EngineEffect.AcknowledgeAlert -> enqueue("ack:${e.alertId}") { it.acknowledge(e.alertId, e.byId, e.byName) }
            is EngineEffect.DispatchSos -> dispatchSos(e)
            is EngineEffect.CancelSos -> cancelSos()
            is EngineEffect.Log -> recorder.record(e.type.name, e.detail, e.timeMs)
            is EngineEffect.Reminder -> requireService<NotificationManager>().notify(Notifications.ID_REMINDER, Notifications.alert(this, "ARMOR", e.text, canAnswer = false))
            is EngineEffect.ArrivalUpdate -> textContacts(
                if (e.arrived) SosMessages.arrived(settings.riderName.ifBlank { "A rider" }, e.label)
                else SosMessages.late(settings.riderName.ifBlank { "A rider" }, e.label, e.position),
            )
        }
        if (effects.any { it is EngineEffect.PublishMessage || it is EngineEffect.PublishAlert || it is EngineEffect.ResolveAlert || it is EngineEffect.AcknowledgeAlert }) flushOutbox()
    }

    // ---- group sync -------------------------------------------------------------------------

    private fun enqueue(key: String, send: suspend (GroupTransport) -> Boolean) {
        if (transport == null) return
        outbox[key] = Outbox(key, clock.now(), send)
    }

    /** Sends queued alert traffic in order, stopping at the first failure so nothing is reordered while offline. */
    private fun flushOutbox() {
        val t = transport ?: return
        val now = clock.now()
        outbox.values.removeAll { now - it.queuedAtMs > OUTBOX_MAX_AGE_MS }
        if (outbox.isEmpty() || !flushing.compareAndSet(false, true)) return
        val pending = outbox.values.toList()
        scope.launch {
            val sentKeys = ArrayList<String>()
            for (item in pending) { if (item.send(t)) sentKeys += item.key else break }
            handler.post {
                // A newer item queued under the same key while we were sending must not be dropped.
                sentKeys.forEach { k -> pending.firstOrNull { it.key == k }?.let { sent -> if (outbox[k] === sent) outbox.remove(k) } }
                flushing.set(false)
            }
        }
    }

    private fun publishSelf() {
        val t = transport ?: return
        val report = engine.selfReport() ?: return
        scope.launch { t.publishSelf(report) }
    }

    private fun poll() {
        val t = transport ?: return
        if (!polling.compareAndSet(false, true)) return
        scope.launch {
            val data = t.fetch()
            handler.post {
                polling.set(false)
                if (data != null && started) {
                    engine.onPeers(data.riders)
                    // Remember everyone's number: an SOS has to reach them later with no internet.
                    if (data.crew.isNotEmpty() && data.crew != app.prefs.crew) app.prefs.crew = data.crew
                    data.leadId?.let { engine.setLead(it) }
                    engine.onMessages(data.messages)
                    engine.onGroupAlerts(data.alerts, clock.now())
                }
            }
        }
    }

    private fun syncEvents() {
        val t = transport ?: return
        if (!networkOnline || !syncing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val dao = app.db.events()
                val batch = dao.unsynced(50)
                if (batch.isNotEmpty() && t.pushEvents(settings.riderId, batch.map { RemoteEvent("${it.timeMs}-${it.id}", it.timeMs, it.type, it.detail) })) {
                    dao.markSynced(batch.map { it.id })
                }
            } finally {
                syncing.set(false)
            }
        }
    }

    // ---- SOS --------------------------------------------------------------------------------

    private fun dispatchSos(e: EngineEffect.DispatchSos) {
        val name = settings.riderName.ifBlank { "A rider" }
        val text = SosMessages.sos(name, e.reason, e.position, e.accuracyM, e.fixAgeMs?.div(1000))
        if (demo != null) {
            ArmorRuntime.sosStatus.value = "Demo ride: this is what would be texted:\n$text"
            return
        }
        ArmorRuntime.sosStatus.value = "Texting your emergency contacts…"
        scope.launch {
            val contacts = app.db.contacts().snapshot()
            handler.post { if (started) sendSos(contacts, text) }
        }
    }

    private fun sendSos(contacts: List<ContactEntity>, text: String) {
        // Everyone who should hear about it: the emergency contacts, then the riders in the group (their numbers were saved
        // while the ride synced, so this works with no internet). Each number is used once.
        val crew = app.prefs.crew.filter { it.id != settings.riderId }
        val seen = HashSet<String>()
        fun key(phone: String) = phone.filter { it.isDigit() }.takeLast(10)
        val recipients = buildList {
            contacts.forEach { if (seen.add(key(it.phone))) add(it) }
            crew.forEachIndexed { i, r -> if (seen.add(key(r.phone))) add(ContactEntity(-(i + 1).toLong(), "${r.name} (riding with you)", r.phone)) }
        }
        if (recipients.isEmpty()) {
            ArmorRuntime.sosStatus.value = "No emergency contacts are saved and nobody in your group has shared a number, so no text was sent. Call emergency services now."
            recorder.record(EventType.SYSTEM.name, "SOS could not be texted: no emergency contacts or group numbers", clock.now())
            return
        }
        val sos = ActiveSos(text, recipients).also { activeSos = it }
        callLine = ""
        for (c in recipients) {
            if (!sms.send(c.phone, text, attempt = 1)) {
                sos.failedFinal += c.phone
                sos.reason = sms.lastError
                recorder.record(EventType.SYSTEM.name, "SMS to ${c.name} was refused by the phone: ${sms.lastError}", clock.now())
            }
        }
        refreshSosStatus(sos)
        // Medical details for first responders, as a separate untracked text so the SOS itself stays one SMS.
        SosMessages.medical(settings.riderName.ifBlank { "A rider" }, settings.medical)?.let { m -> recipients.forEach { sms.sendPlain(it.phone, m) } }

        // Then ring them, nearest rider first (they can be there soonest), then the emergency contacts.
        // A silent SOS never rings: a ringing phone would give the rider away.
        if (settings.autoCall && !ArmorRuntime.silentSos.value && caller.canCall()) {
            val distance = lastSnap?.riders?.associate { it.id to (it.distanceFromSelfM ?: Double.MAX_VALUE) } ?: emptyMap()
            val order = buildList {
                crew.sortedBy { distance[it.id] ?: Double.MAX_VALUE }.forEach { add(it.phone) }
                contacts.forEach { add(it.phone) }
                if (settings.callEmergency && settings.emergencyNumber.isNotBlank()) add(settings.emergencyNumber)
            }
            caller.start(order.distinctBy { key(it) }, stopOnAnswer = !settings.callAll)
        }
    }

    /** A plain, untracked text to every emergency contact (safe arrival, running late). Never in demo mode. */
    private fun textContacts(text: String) {
        if (demo != null) { recorder.record(EventType.SYSTEM.name, "demo: would text contacts: $text", clock.now()); return }
        scope.launch {
            val contacts = app.db.contacts().snapshot()
            handler.post { contacts.forEach { sms.sendPlain(it.phone, text) } }
        }
    }

    private fun onSmsResult(r: ArmorRuntime.Command.SmsResult) {
        val sos = activeSos ?: return
        if (r.ok) {
            sos.delivered += r.phone
            recorder.record(EventType.SYSTEM.name, "SOS text handed to the network for ${r.phone}", clock.now())
        } else if (r.attempt < MAX_SMS_ATTEMPTS && engine.state is SafetyState.Sos) {
            sos.reason = r.reason
            recorder.record(EventType.SYSTEM.name, "SOS text to ${r.phone} failed (${r.reason}, attempt ${r.attempt}); retrying in 20 s", clock.now())
            handler.postDelayed({
                if (started && activeSos === sos && engine.state is SafetyState.Sos && !sms.send(r.phone, sos.text, r.attempt + 1)) sos.failedFinal += r.phone
            }, SMS_RETRY_MS)
        } else {
            sos.failedFinal += r.phone
            sos.reason = r.reason
            recorder.record(EventType.SYSTEM.name, "SOS text to ${r.phone} failed after ${r.attempt} attempts: ${r.reason}", clock.now())
        }
        refreshSosStatus(sos)
    }

    private fun refreshSosStatus(sos: ActiveSos) {
        val total = sos.recipients.size
        val texts = when {
            sos.delivered.size == total -> "SOS text sent to all $total people (emergency contacts and your group)."
            sos.delivered.size + sos.failedFinal.size == total ->
                "SOS text reached ${sos.delivered.size} of $total people" + (sos.reason?.let { " ($it)" } ?: "") + ". Call emergency services now."
            else -> "SOS text sent to ${sos.delivered.size} of $total people; still trying…"
        }
        ArmorRuntime.sosStatus.value = if (callLine.isEmpty()) texts else "$texts\n$callLine"
    }

    private fun cancelSos() {
        val sos = activeSos
        activeSos = null
        callLine = ""
        caller.stop()
        ArmorRuntime.sosStatus.value = null
        if (sos == null || demo != null) return
        val text = SosMessages.cancelled(settings.riderName.ifBlank { "A rider" })
        sos.recipients.forEach { sms.send(it.phone, text, attempt = 1) }
    }

    // ---- notifications ----------------------------------------------------------------------

    private var hapTeamStop: String? = null
    private var hapMessages = 0
    private val hapHazards = HashSet<String>()
    private val hapHelp = HashSet<String>()

    /** Buzz the phone for what just happened in the group: a team stop, a message, a hazard, someone asking for a hand. */
    private fun hapticsFor(s: RideSnapshot) {
        val ts = s.teamStop
        if (ts != null && !ts.calledByMe && ts.alertId != hapTeamStop) Haptics.teamStop()
        hapTeamStop = ts?.alertId

        val theirs = s.messages.count { it.riderId != settings.riderId }
        if (theirs > hapMessages) Haptics.message()
        hapMessages = theirs

        if (s.hazards.filter { it.riderId != settings.riderId && hapHazards.add(it.id) }.isNotEmpty()) Haptics.hazard()
        if (s.incomingAlerts.filter { it.alert.kind == AlertKind.HELP && hapHelp.add(it.alert.id) }.isNotEmpty()) Haptics.warning()
    }

    private fun updateNotifications(s: RideSnapshot) {
        hapticsFor(s)
        lastSnap = s
        val nm = requireService<NotificationManager>()

        val text = when {
            s.gpsLost -> "Waiting for GPS…"
            s.assessment == OutlierAssessment.IN_CLUSTER -> "With the group · ${s.riders.size} riders"
            s.assessment == OutlierAssessment.OUTLIER -> "Separated from the group"
            else -> if (s.riders.size > 1) "Group out of reach" else "Riding solo"
        }
        if (text != lastRideText) {
            lastRideText = text
            nm.notify(Notifications.ID_RIDE, Notifications.ride(this, text))
        }

        val state = s.safety
        val incoming = s.incomingAlerts.firstOrNull { it.needsMe }
        val (title, body, canAnswer) = when (state) {
            is SafetyState.Verifying -> Triple("Are you OK?", "Tap I'm OK, or the group will be alerted in ${secondsLeft(state.deadlineMs, s.nowMs)} s", true)
            is SafetyState.GroupAlerted -> Triple("Group alerted", "SOS in ${secondsLeft(state.deadlineMs, s.nowMs)} s unless someone responds", true)
            is SafetyState.CrashCountdown -> Triple("Crash detected", "SOS in ${secondsLeft(state.deadlineMs, s.nowMs)} s unless you cancel", true)
            is SafetyState.Sos -> Triple("SOS sent", "Your emergency contacts were texted your location", true)
            else -> if (incoming != null) Triple("${incoming.alert.riderName} may need help", "Open ARMOR to respond", false) else Triple("", "", false)
        }
        if (title.isEmpty()) nm.cancel(Notifications.ID_ALERT) else nm.notify(Notifications.ID_ALERT, Notifications.alert(this, title, body, canAnswer))
    }

    private fun secondsLeft(deadlineMs: Long, nowMs: Long) = ((deadlineMs - nowMs + 999) / 1000).coerceAtLeast(0)

    private fun hasLocationPermission() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TICK_MS = 1_000L
        private const val OUTBOX_MAX_AGE_MS = 15 * 60_000L
        private const val SMS_RETRY_MS = 20_000L
        private const val MAX_SMS_ATTEMPTS = 3
        const val ACTION_START = "com.armor.app.action.START"
        const val EXTRA_DEMO = "demo"

        fun start(context: Context, demo: Boolean) {
            context.startForegroundService(
                Intent(context, RideService::class.java).setAction(ACTION_START).putExtra(EXTRA_DEMO, demo),
            )
        }
    }
}
