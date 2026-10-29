package com.armor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.ArmorApp
import com.armor.app.alert.Haptics
import com.armor.app.voice.VoiceAssistant
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.widthIn
import com.armor.app.data.Settings
import com.armor.app.service.ArmorRuntime
import com.armor.app.service.RideService
import com.armor.app.sim.DemoAction
import com.armor.core.engine.RideSnapshot
import com.armor.core.engine.RideSummary
import com.armor.core.engine.RiderView
import com.armor.core.geo.Geo
import com.armor.core.model.LatLon
import com.armor.core.model.OutlierAssessment
import com.armor.core.model.QuickStatus
import com.armor.core.safety.SafetyState
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RideScreen(settings: Settings) {
    val running by ArmorRuntime.running.collectAsState()
    val snapshot by ArmorRuntime.snapshot.collectAsState()
    val demo by ArmorRuntime.demoMode.collectAsState()
    val warning by ArmorRuntime.warning.collectAsState()
    val summary by ArmorRuntime.lastSummary.collectAsState()
    val s = snapshot
    // Crossfade on the screen kind only (not on every snapshot), so ticks never restart the transition.
    val kind = when {
        !running && summary != null -> 2
        !running || s == null -> 0
        else -> 1
    }
    androidx.compose.animation.Crossfade(kind, animationSpec = androidx.compose.animation.core.tween(250), label = "ride") { k ->
        when (k) {
            2 -> summary?.let { RideSummaryScreen(it) { ArmorRuntime.lastSummary.value = null } }
            0 -> PreRideHost(settings, starting = running)
            else -> s?.let { ActiveRide(it, demo, warning, settings) }
        }
    }
}

@Composable
private fun PreRideHost(settings: Settings, starting: Boolean) {
    val contactsFlow = remember { ArmorApp.instance.db.contacts().all() }
    val contacts by contactsFlow.collectAsState(initial = emptyList())
    PreRide(settings, contacts.size, starting)
}

@Composable
internal fun PreRide(settings: Settings, contactCount: Int, starting: Boolean) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }

    val needed = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            denied = false
            RideService.start(context, demo = false)
        } else {
            denied = true
        }
    }

    val locOk = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val smsOk = granted(Manifest.permission.SEND_SMS)
    Column(Modifier.fillMaxSize().warmGlow().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Wordmark()
        Txt("Advanced Rider Monitoring & Orientation Reasoning System", Type.Caption, ArmorColors.Dim, Modifier.padding(top = 6.dp))
        LogoHero(Modifier.fillMaxWidth().height(250.dp))
        Txt("RIDE TOGETHER.", Type.Hero)
        Txt("COME HOME TOGETHER.", Type.Hero, ArmorColors.Orange)
        Txt("ARMOR watches over your group and calls for help if you can't.", Type.Body, ArmorColors.Muted, Modifier.padding(top = 12.dp, bottom = 20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadyChip("Location", locOk, Modifier.weight(1f))
            ReadyChip("SOS text", smsOk, Modifier.weight(1f))
            ReadyChip(if (contactCount > 0) "$contactCount contacts" else "Contacts", contactCount > 0, Modifier.weight(1f))
        }
        // Setup is done once here, not mid-ride. This card disappears when everything is ready.
        val (ready, total) = protectionReady(context)
        if (ready < total) { Gap(12.dp); SetupCard(ready, total) { showSetup = true } }
        if (showSetup) ProtectionSetupDialog { showSetup = false }
        if (denied) Txt("Location permission is needed to track your ride. Allow it in system settings and try again.", Type.Label, ArmorColors.Red, Modifier.padding(top = 12.dp))
        if (settings.syncConfigured) Txt("Group ${settings.groupCode}", Type.Label, ArmorColors.Muted, Modifier.padding(top = 12.dp))

        Gap(22.dp)
        SlideToStart(if (starting) "STARTING\u2026" else "SLIDE TO RIDE") { if (!starting) launcher.launch(needed) }
    }
}

/** A compact readiness indicator: a lit orange tick when the requirement is met, an amber ring when it isn't. */
@Composable
private fun ReadyChip(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(ArmorColors.S1).border(1.dp, ArmorColors.Line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(if (ok) ArmorColors.Green else ArmorColors.Amber, 8.dp)
        Txt(label, Type.Label.copy(fontSize = 12.sp), if (ok) ArmorColors.OnBg else ArmorColors.Muted, maxLines = 1)
    }
}

@Composable
internal fun ActiveRide(s: RideSnapshot, demo: Boolean, warning: String?, settings: Settings) {
    // Your recent route, drawn on the map as a navigation-style line.
    val trail = remember { mutableStateListOf<LatLon>() }
    val here = s.self?.position
    LaunchedEffect(here) {
        if (here != null && (trail.isEmpty() || Geo.distanceM(trail.last(), here) > 4.0)) {
            trail.add(here)
            if (trail.size > 80) trail.removeAt(0)
        }
    }
    val look = linkLook(s)
    var chatOpen by remember { mutableStateOf(false) }
    var mapPicker by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    val camera = remember { MapCamera() }

    // "Armor, ..." voice assistant: contacts and riders it can call or text, wake word while this screen is open.
    val context = LocalContext.current
    val assistant = remember { VoiceAssistant(context) }
    val caption by assistant.caption.collectAsState()
    val listening by assistant.listening.collectAsState()
    val micLevel by assistant.level.collectAsState()
    val savedContacts by remember { ArmorApp.instance.db.contacts().all() }.collectAsState(initial = emptyList())
    LaunchedEffect(savedContacts, s.riders.size) {
        val crew = ArmorApp.instance.prefs.crew.filter { it.id != settings.riderId }
            .mapIndexed { i, r -> com.armor.app.data.ContactEntity(-(i + 1).toLong(), r.name, r.phone) }
        assistant.contacts = savedContacts + crew
    }
    var micGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> micGranted = ok; if (ok) assistant.listenForCommand() }
    LaunchedEffect(settings.voiceWake, micGranted) { assistant.setWakeMode(settings.voiceWake && micGranted) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(4_000); assistant.resume() } }
    DisposableEffect(Unit) { onDispose { assistant.release() } }
    val mapView = MapView.of(settings.mapView)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.58f
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Street map tiles (cached for offline); rider positions never wait on them.
                RiderMap(s, Modifier.fillMaxSize(), trail.toList(), view = mapView, camera = camera)
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.glass(RoundedCornerShape(50)).padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ArmorLogo(18.dp)
                            Txt("ARMOR", Type.Eyebrow.copy(fontSize = 12.sp, letterSpacing = 2.sp), Color.White)
                        }
                        Box(Modifier.weight(1f))
                        // One pill for the two things a rider wants to know at a glance: is GPS locked, is the group reachable.
                        Row(Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusDot(if (s.gpsLost) ArmorColors.Red else ArmorColors.Green, 7.dp)
                            Txt(if (s.gpsLost) "No GPS" else "GPS", Type.Label.copy(fontSize = 12.sp), Color.White)
                            Box(Modifier.size(1.dp, 12.dp).background(Color.White.copy(alpha = 0.2f)))
                            StatusDot(if (s.online) ArmorColors.Green else ArmorColors.Dim, 7.dp)
                            Txt(if (s.online) "Online" else "Offline", Type.Label.copy(fontSize = 12.sp), Color.White)
                        }
                    }
                    var hiddenWarning by remember { mutableStateOf<String?>(null) }
                    warning?.takeIf { it != hiddenWarning }?.let { w ->
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.padding(start = 8.dp).size(4.dp, 28.dp).clip(CircleShape).background(ArmorColors.Orange))
                            Txt(w, Type.Label.copy(fontSize = 13.sp), Color.White, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 3)
                            Box(Modifier.size(36.dp).clip(CircleShape).clickable { hiddenWarning = w }.semantics { contentDescription = "Dismiss" }, contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, null, tint = ArmorColors.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                // Which map view is showing; tap to change it. Only the Google map has views, the offline map has one.
                // The assistant: tap the mic (or say "Armor") and speak; what it heard and its answer show above it.
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    caption?.let {
                        Txt(it, Type.Label, Color.White, Modifier.widthIn(max = 260.dp).glass(RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp), maxLines = 3)
                    }
                    // While listening, a ring around the mic grows with your voice, so you can see it hears you.
                    val ring = if (listening) (6 + (micLevel.coerceIn(0f, 10f) * 1.6f)).dp else 0.dp
                    Box(
                        Modifier.size(58.dp + ring * 2).clip(CircleShape).background(if (listening) ArmorColors.Red.copy(alpha = 0.22f) else Color.Transparent).padding(ring)
                            .shadow(10.dp, CircleShape).clip(CircleShape)
                            .background(if (listening) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFF5A52), ArmorColors.Red)) else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFF8A3D), ArmorColors.Orange)))
                            .clickable { if (micGranted) assistant.listenForCommand() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }
                            .semantics { contentDescription = "Talk to Armor" },
                        contentAlignment = Alignment.Center,
                    ) { MicGlyph(Color.White) }
                }
                // Zoom buttons for gloved hands; pinch and drag work too.
                // Map controls, one style: zoom as a single stacked pill, recentre and map view as round buttons.
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).glass(RoundedCornerShape(24.dp))) {
                    ZoomButton("+") { camera.zoomBy(1.6f) }
                    Box(Modifier.padding(horizontal = 10.dp).size(28.dp, 1.dp).background(Color.White.copy(alpha = 0.14f)))
                    ZoomButton("\u2212") { camera.zoomBy(1f / 1.6f) }
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!camera.following) Row(
                        Modifier.glass(RoundedCornerShape(50)).clickable { Haptics.click(); camera.recenter() }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.LocationOn, null, tint = ArmorColors.Orange, modifier = Modifier.size(16.dp))
                        Txt("Recentre", Type.Label.copy(fontSize = 13.sp), Color.White)
                    }
                    Row(
                        Modifier.glass(RoundedCornerShape(50)).clickable { mapPicker = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Txt(mapView.label, Type.Label.copy(fontSize = 13.sp), Color.White)
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp, null, tint = ArmorColors.Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = maxSheet)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(ArmorColors.S1)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
            ) {
                // The whole header (handle and status) is the grip: drag it down or tap it to minimise the panel and give
                // the map the screen; drag up or tap again to bring the controls back.
                val grip = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> if (dy > 6f) minimized = true else if (dy < -6f) minimized = false }
                }.clickable { Haptics.tick(); minimized = !minimized }
                Box(
                    grip.fillMaxWidth().height(30.dp).semantics { contentDescription = if (minimized) "Show controls" else "Hide controls" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(ArmorColors.S3))
                }
                // Details scroll; the actions below them stay pinned so SOS, help and chat are always one reach away.
                if (minimized) {
                    Box(grip) { MiniStatus(s, look) }
                } else Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Box(grip) { StatusRow(s, look) }
                Gap(12.dp)
                StatsStrip(s)
                if (s.safety is SafetyState.Acknowledged) {
                    Gap(12.dp)
                    PrimaryButton("I'm okay", color = ArmorColors.Green, contentColor = Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
                }
                Gap(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { Haptics.tick(); details = !details }.padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt("Riders, lead & safety tools", Type.BodyStrong.copy(fontSize = 14.sp), ArmorColors.Muted, Modifier.weight(1f))
                    androidx.compose.material3.Icon(
                        if (details) androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp else androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (details) "Hide details" else "Show details", tint = ArmorColors.Muted,
                    )
                }
                if (details) {
                    Gap(10.dp)
                    LeadCard(s)
                    Gap(12.dp)
                    SafetyTools(s, settings)
                    if (s.riders.any { !it.isSelf }) { Gap(16.dp); ConvoyStrip(s) }
                    Gap(16.dp)
                    QuickStatusRow(s.quickStatus)
                }
                if (demo) {
                    Gap(14.dp)
                    DemoControls()
                }
                Gap(8.dp)
                }
                Gap(10.dp)
                if (!minimized) {
                    ActionBar(s, onChat = { chatOpen = true }, onEnd = { confirmEnd = true })
                    Gap(10.dp)
                }
                HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
            }
        }
        if (chatOpen) ChatScreen(s) { chatOpen = false }
        if (confirmEnd) AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this ride?") },
            text = { Text("Tracking and protection for this ride will stop.") },
            confirmButton = { TextButton({ confirmEnd = false; ArmorRuntime.send(ArmorRuntime.Command.Stop) }) { Text("End ride", color = ArmorColors.Red) } },
            dismissButton = { TextButton({ confirmEnd = false }) { Text("Keep riding") } },
            containerColor = ArmorColors.S2, titleContentColor = ArmorColors.OnBg, textContentColor = ArmorColors.Muted,
        )
        if (mapPicker) MapViewPicker(mapView, onPick = { v -> ArmorApp.instance.prefs.update { it.copy(mapView = v.key) }; mapPicker = false }, onDismiss = { mapPicker = false })
    }
}
private data class LinkLook(val title: String, val sub: String, val color: Color)

private fun linkLook(s: RideSnapshot): LinkLook {
    val others = s.riders.filter { !it.isSelf && !it.isStale }
    return when (val state = s.safety) {
        is SafetyState.Acknowledged -> LinkLook("${state.byName} is on the way", "Help is coming. Stay where you are.", ArmorColors.Amber)
        is SafetyState.Normal -> when (s.assessment) {
            OutlierAssessment.IN_CLUSTER -> LinkLook(
                "With the group",
                "${others.size + 1} riders within ${fmtDistance(others.maxOfOrNull { it.distanceFromSelfM ?: 0.0 } ?: 0.0)}",
                ArmorColors.Green,
            )
            OutlierAssessment.OUTLIER -> LinkLook(
                "Separated",
                (if (s.stationaryForMs >= 10_000) "Stopped ${fmtMmSs(s.stationaryForMs)} · " else "") +
                    "${fmtDistance(others.minOfOrNull { it.distanceFromSelfM ?: 0.0 } ?: 0.0)} from the group",
                ArmorColors.Red,
            )
            OutlierAssessment.UNKNOWN -> LinkLook(
                if (s.riders.size > 1) "Group out of reach" else "Riding solo",
                if (s.stationaryForMs >= 10_000) "Stopped ${fmtMmSs(s.stationaryForMs)}" else "No other riders in range",
                ArmorColors.Dim,
            )
        }
        else -> LinkLook("Safety check in progress", "Follow the prompt on screen", ArmorColors.Amber)
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable { Haptics.tick(); onClick() }.semantics { contentDescription = if (label == "+") "Zoom in" else "Zoom out" },
        contentAlignment = Alignment.Center,
    ) { Txt(label, Type.Title.copy(fontSize = 22.sp), Color.White) }
}

/** Frosted dark surface for everything that floats over the map, so all map controls read as one set. */
private fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.shadow(6.dp, shape).clip(shape).background(Color(0xE0121316)).border(1.dp, Color.White.copy(alpha = 0.08f), shape)

/** The panel when minimised: one line, so the map has almost the whole screen. */
@Composable
private fun MiniStatus(s: RideSnapshot, look: LinkLook) {
    val kmh = max(0.0, s.self?.speedMps ?: 0.0) * 3.6
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(look.color, 10.dp)
        Txt(look.title.uppercase(), Type.BodyStrong, modifier = Modifier.weight(1f), maxLines = 1)
        Txt("${kmh.roundToInt()} km/h", Type.BodyStrong, ArmorColors.Orange)
    }
}

@Composable
private fun StatusRow(s: RideSnapshot, look: LinkLook) {
    val kmh = max(0.0, s.self?.speedMps ?: 0.0) * 3.6
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(look.color, 10.dp)
                Txt(look.title.uppercase(), Type.Title, maxLines = 1)
            }
            Txt(look.sub, Type.Body.copy(fontSize = 14.sp), ArmorColors.Muted, Modifier.padding(top = 2.dp), maxLines = 2)
        }
        SpeedDial(kmh)
    }
}

/** Distance, time and top speed for the ride so far, in the cluster-style condensed numerals. */
@Composable
private fun StatsStrip(s: RideSnapshot) {
    val st = s.stats
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(CARD_FILL).border(1.dp, CARD_EDGE, shape).padding(vertical = 12.dp)) {
        listOf(
            Triple("DISTANCE", fmtDistance(st.distanceM), ""),
            Triple("TIME", fmtHms(st.elapsedMs), ""),
            Triple("TOP SPEED", (st.maxSpeedMps * 3.6).roundToInt().toString(), " km/h"),
        ).forEach { (label, value, unit) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(label, Type.Eyebrow.copy(fontSize = 11.sp, letterSpacing = 1.5.sp), ArmorColors.Dim)
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(value, Type.Title.copy(fontSize = 24.sp))
                    if (unit.isNotEmpty()) Txt(unit, Type.Caption, ArmorColors.Muted, Modifier.padding(start = 2.dp, bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickStatusRow(current: QuickStatus?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickStatus.entries.forEach { st ->
            val on = st == current
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { ArmorRuntime.send(ArmorRuntime.Command.SetStatus(if (on) null else st)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (on) ArmorColors.Orange.copy(alpha = 0.2f) else ArmorColors.S2)
                        .border(1.dp, if (on) ArmorColors.Orange else ArmorColors.Line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { StatusIcon(st, if (on) ArmorColors.Orange else ArmorColors.Muted) }
                Txt(st.shortLabel(), Type.Caption, if (on) ArmorColors.Orange else ArmorColors.Muted, Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Always-visible actions: group chat, non-emergency "I need a hand" (shows who is coming, tap again to cancel), end ride. */
@Composable
private fun ActionBar(s: RideSnapshot, onChat: () -> Unit, onEnd: () -> Unit) {
    val help = s.help
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BarButton("Chat", Modifier.weight(1f), onClick = onChat)
        BarButton(
            when {
                help == null -> "Need a hand"
                help.ackedByName != null -> "${help.ackedByName} coming"
                else -> "Cancel help"
            },
            Modifier.weight(1.4f), active = help != null,
        ) { ArmorRuntime.send(if (help != null) ArmorRuntime.Command.CancelHelp else ArmorRuntime.Command.RequestHelp) }
        BarButton("End", Modifier.weight(1f), onClick = onEnd)
    }
}

@Composable
private fun BarButton(text: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.height(52.dp).clip(shape).background(if (active) ArmorColors.Orange.copy(alpha = 0.14f) else ArmorColors.S2)
            .border(1.dp, if (active) ArmorColors.Orange else ArmorColors.Line, shape)
            .clickable { Haptics.click(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Txt(text, Type.BodyStrong, if (active) ArmorColors.Orange else ArmorColors.OnBg, maxLines = 1) }
}

@Composable
private fun DemoControls() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ArmorColors.S2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("Demo controls", Type.Label, ArmorColors.Muted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Pill("Stop my bike", color = ArmorColors.S3) { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_ME)) } }
            item { Pill("Crash", color = ArmorColors.S3) { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.CRASH_ME)) } }
            item { Pill("Kabir stops", color = ArmorColors.S3) { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_PEER)) } }
            item { Pill("Everyone rides", color = ArmorColors.S3) { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.RESUME_ALL)) } }
            item { Pill("Peers acknowledge", color = ArmorColors.S3) { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.TOGGLE_PEER_ACK)) } }
        }
    }
}

/** Shown after a ride ends: the totals, big, and one button to leave. */
@Composable
internal fun RideSummaryScreen(sum: RideSummary, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().warmGlow().statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArmorLogo(20.dp)
            Txt("RIDE COMPLETE", Type.Eyebrow, ArmorColors.Orange)
        }
        Gap(8.dp)
        Txt("YOU MADE", Type.Hero)
        Txt("IT HOME.", Type.Hero, ArmorColors.Orange)
        Gap(28.dp)
        Txt(fmtDistance(sum.distanceM), Type.Display)
        Txt("DISTANCE", Type.Eyebrow, ArmorColors.Dim)
        Gap(22.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile("TIME", fmtHms(sum.elapsedMs), Modifier.weight(1f))
            SummaryTile("MOVING", fmtHms(sum.movingMs), Modifier.weight(1f))
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile("TOP SPEED", "${(sum.maxSpeedMps * 3.6).roundToInt()} km/h", Modifier.weight(1f))
            SummaryTile("AVG MOVING", "${(sum.avgMovingSpeedMps * 3.6).roundToInt()} km/h", Modifier.weight(1f))
        }
        Box(Modifier.weight(1f))
        PrimaryButton("Done", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00), onClick = onDone)
        Gap(90.dp)
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(CARD_FILL).border(1.dp, CARD_EDGE, shape).padding(16.dp)) {
        Txt(label, Type.Eyebrow.copy(fontSize = 12.sp), ArmorColors.Dim)
        Txt(value, Type.Headline.copy(fontSize = 30.sp))
    }
}
