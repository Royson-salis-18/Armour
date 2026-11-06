package com.armor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
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

    var refresh by remember { mutableIntStateOf(0) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val gate = remember(refresh, settings, contactCount) { RideGate.items(context, settings, contactCount) }
    val blocked = gate.any { !it.ok }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        denied = !granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!RideGate.missing(context, settings, contactCount).any()) RideService.start(context, demo = false)
    }

    var gateOpen by remember { mutableStateOf(false) }
    val (ready, total) = protectionReady(context)
    if (showSetup) ProtectionSetupDialog { showSetup = false }

    // The slider is pinned to the bottom so starting a ride never needs a scroll; everything else sits above it.
    Box(Modifier.fillMaxSize().warmGlow().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Box(Modifier.weight(1f))
                Txt(if (blocked) "SETUP NEEDED" else "READY", Type.Meta, if (blocked) ArmorColors.Muted else ArmorColors.Orange)
            }
            UpdateBanner(Modifier.padding(top = 12.dp))
            Gap(28.dp)
            Txt("Ride together.", Type.Headline.copy(fontSize = 34.sp, lineHeight = 38.sp))
            Txt("Come home together.", Type.Headline.copy(fontSize = 34.sp, lineHeight = 38.sp), ArmorColors.Orange)
            Txt(
                if (settings.syncConfigured) "Group ride ${settings.rideCode.ifBlank { settings.groupCode }}" else "Riding solo. Join or start a group ride from the Group tab.",
                Type.Body, ArmorColors.Muted, Modifier.padding(top = 10.dp),
            )
            Gap(24.dp)
            GateCard(gate, open = gateOpen, onToggle = { gateOpen = !gateOpen })
            if (ready < total) { Gap(12.dp); SlimRow("Finish setting up", "$ready of $total permissions ready", "Set up", accent = true) { showSetup = true } }
            Gap(12.dp)
            SlimRow("How ARMOR works", "Eight real situations, one minute each", "Watch") { Tour.show.value = true }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (denied) Txt("Location permission is needed to track your ride. Allow it in system settings and try again.", Type.Label, ArmorColors.Red)
            SlideToStart(if (starting) "STARTING\u2026" else if (blocked) "FINISH SETUP TO RIDE" else "SLIDE TO RIDE", enabled = !starting) {
                if (starting) return@SlideToStart
                val ask = RideGate.requestable(context)
                if (blocked && ask.isNotEmpty()) launcher.launch(ask)
                else if (!blocked) RideService.start(context, demo = false)
                else refresh++
            }
        }
    }
}

/** A one-line card that opens something: title, a short line under it, and what tapping does. */
@Composable
private fun SlimRow(title: String, sub: String, action: String, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().panel(18.dp, neon = accent).clickable { Haptics.tick(); onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.BodyStrong)
            Txt(sub, Type.Caption, ArmorColors.Muted)
        }
        Txt(action, Type.Label, ArmorColors.Orange)
    }
}

/**
 * The pre-ride checklist as a route: each safety check is a stop on the rail, lit orange once it passes. While anything is
 * missing it stays open and says what to do; once everything passes it folds down to one line.
 */
@Composable
private fun GateCard(items: List<GateItem>, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val firstOpen = items.indexOfFirst { !it.ok }
    val allOk = firstOpen < 0
    if (allOk && !open) {
        Row(
            modifier.fillMaxWidth().panel(18.dp).clickable { Haptics.tick(); onToggle() }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(ArmorColors.Orange))
            Column(Modifier.weight(1f)) {
                Txt("All safety checks passed", Type.BodyStrong)
                Txt("${items.size} of ${items.size} \u00B7 location, GPS, SMS, alerts, contacts", Type.Caption, ArmorColors.Muted, maxLines = 1)
            }
            Txt("Details", Type.Label, ArmorColors.Muted)
        }
        return
    }
    Column(modifier.fillMaxWidth().panel(22.dp, neon = !allOk).padding(18.dp)) {
        Row(Modifier.then(if (allOk) Modifier.clickable { onToggle() } else Modifier), verticalAlignment = Alignment.CenterVertically) {
            Txt("BEFORE YOU RIDE", Type.Eyebrow, ArmorColors.Orange, Modifier.weight(1f))
            Txt(if (allOk) "Hide" else "${items.count { it.ok }}/${items.size}", Type.Meta, ArmorColors.Muted)
        }
        Txt(if (allOk) "All safety checks passed." else "The ride unlocks when every check passes.", Type.Title.copy(fontSize = 18.sp, lineHeight = 23.sp), ArmorColors.OnBg, Modifier.padding(top = 6.dp, bottom = 16.dp))
        Rail(items.mapIndexed { i, it -> when { it.ok -> RailState.DONE; i == firstOpen -> RailState.ACTIVE; else -> RailState.TODO } }, nodeTop = 10.dp, gap = 12.dp) { i ->
            val it = items[i]
            Column {
                Txt(it.title, Type.BodyStrong.copy(fontSize = 15.sp), if (it.ok || i == firstOpen) ArmorColors.OnBg else ArmorColors.Muted)
                if (!it.ok) Txt(it.fix, Type.Caption, ArmorColors.Muted, Modifier.padding(top = 2.dp))
            }
        }
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
    var tool by remember { mutableStateOf<RideTool?>(null) }
    // Opening the details scrolls them into view; otherwise they open below the fold and look like nothing happened.
    val sheetScroll = rememberScrollState()
    // Far enough to bring the lead controls (the first thing in the details) up under the status line.
    val detailsJump = with(androidx.compose.ui.platform.LocalDensity.current) { 190.dp.roundToPx() }
    LaunchedEffect(tool) {
        if (tool != null) { kotlinx.coroutines.delay(120); sheetScroll.animateScrollTo(minOf(sheetScroll.maxValue, sheetScroll.value + detailsJump)) }
    }
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
                // Full-map mode: a route card in the corner (destination, group, your speed, who is closest), like a nav widget.
                if (minimized) RouteCard(s, look, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 64.dp))
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
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(ArmorColors.Panel)
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
                } else Column(Modifier.weight(1f, fill = false).verticalScroll(sheetScroll)) {
                Box(grip) { StatusRow(s, look) }
                Gap(12.dp)
                StatsStrip(s)
                if (s.safety is SafetyState.Acknowledged) {
                    Gap(12.dp)
                    PrimaryButton("I'm okay", color = ArmorColors.Green, contentColor = Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
                }
                if (hasBanners(s)) { Gap(12.dp); RideBanners(s) }
                // A team stop is time-critical, so it is never hidden behind a tool.
                if (s.teamStop != null) { Gap(12.dp); LeadCard(s) }
                Gap(16.dp)
                Txt("QUICK ACTIONS", Type.Eyebrow, ArmorColors.Dim, Modifier.padding(start = 2.dp, bottom = 8.dp))
                QuickActions(s, tool) { t ->
                    if (t == RideTool.OFF_BIKE) ArmorRuntime.send(ArmorRuntime.Command.PauseCrashDetection(!s.crashDetectionPaused))
                    else tool = if (tool == t) null else t
                }
                when (tool) {
                    RideTool.STOP -> { Gap(12.dp); Txt("Why are you stopping? The group sees it, and no alarm is raised.", Type.Caption, ArmorColors.Muted, Modifier.padding(bottom = 8.dp)); QuickStatusRow(s.quickStatus) }
                    RideTool.HAZARD -> { Gap(12.dp); HazardGrid { tool = null } }
                    RideTool.GROUP -> {
                        Gap(12.dp)
                        if (s.teamStop == null) { LeadCard(s); Gap(12.dp) }
                        if (s.riders.any { !it.isSelf }) { ConvoyStrip(s); Gap(12.dp) }
                        GroupTools(s, settings)
                    }
                    else -> Unit
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
/** A navigation-style card: a rail of stops with the destination, the group, your speed, and the nearest rider. */
@Composable
private fun RouteCard(s: RideSnapshot, look: LinkLook, modifier: Modifier = Modifier) {
    val kmh = max(0.0, s.self?.speedMps ?: 0.0) * 3.6
    val plan = s.arrival
    val others = s.riders.filter { !it.isSelf && !it.isStale }
    val nearest = others.minByOrNull { it.distanceFromSelfM ?: Double.MAX_VALUE }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.width(214.dp).clip(shape).background(Brush.verticalGradient(listOf(Color(0xF01B1B1E), Color(0xF0111113))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        val states = listOf(RailState.DONE, RailState.DONE, RailState.ACTIVE, RailState.TODO)
        Rail(states, nodeTop = 9.dp, gap = 10.dp) { i ->
            when (i) {
                0 -> Column {
                    Txt(plan?.label ?: "Ride started", Type.BodyStrong.copy(fontSize = 14.sp), maxLines = 1)
                    Txt(if (plan != null) "Destination" else "No destination set", Type.Meta, ArmorColors.Dim)
                }
                1 -> Column {
                    Txt(look.title, Type.BodyStrong.copy(fontSize = 14.sp), maxLines = 1)
                    Txt(look.sub, Type.Meta, ArmorColors.Dim, maxLines = 2)
                }
                2 -> Row(verticalAlignment = Alignment.Bottom) {
                    Txt(kmh.roundToInt().toString(), Type.Numeric.copy(fontSize = 26.sp))
                    Txt(" km/h", Type.Meta, ArmorColors.Muted, Modifier.padding(bottom = 4.dp))
                }
                else -> Column {
                    Txt(nearest?.name ?: "Riding solo", Type.BodyStrong.copy(fontSize = 14.sp), if (nearest != null) ArmorColors.OnBg else ArmorColors.Muted, maxLines = 1)
                    Txt(nearest?.distanceFromSelfM?.let { fmtDistance(it) + " away" } ?: "No one else yet", Type.Meta, ArmorColors.Dim)
                }
            }
        }
        if (plan != null) {
            val mins = ((plan.expectedAtMs - s.nowMs) / 60_000).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                Txt("$mins", Type.Numeric.copy(fontSize = 20.sp)); Txt(" min", Type.Meta, ArmorColors.Muted, Modifier.padding(bottom = 3.dp))
            }
        }
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
                Txt(look.title, Type.Title, maxLines = 1)
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
    Row(Modifier.fillMaxWidth().panel(radius = 18.dp, neon = false).padding(vertical = 14.dp)) {
        listOf(
            Triple("DISTANCE", fmtDistance(st.distanceM), ""),
            Triple("TIME", fmtHms(st.elapsedMs), ""),
            Triple("TOP SPEED", (st.maxSpeedMps * 3.6).roundToInt().toString(), " km/h"),
        ).forEach { (label, value, unit) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(label, Type.Eyebrow.copy(fontSize = 10.sp), ArmorColors.Dim)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Txt(value, Type.Numeric)
                    if (unit.isNotEmpty()) Txt(unit, Type.Caption, ArmorColors.Muted, Modifier.padding(start = 2.dp, bottom = 3.dp))
                }
            }
        }
    }
}

private enum class RideTool { STOP, HAZARD, OFF_BIKE, GROUP }

/** The four things a rider actually reaches for mid-ride, always on screen and labelled. */
@Composable
private fun QuickActions(s: RideSnapshot, open: RideTool?, onTap: (RideTool) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile("Stopping", s.self?.status != null || open == RideTool.STOP, Modifier.weight(1f), { onTap(RideTool.STOP) }) { tint -> StatusIcon(QuickStatus.BREAK, tint) }
        ActionTile("Hazard", open == RideTool.HAZARD, Modifier.weight(1f), { onTap(RideTool.HAZARD) }) { tint ->
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Warning, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        ActionTile(if (s.crashDetectionPaused) "Back on" else "Off bike", s.crashDetectionPaused, Modifier.weight(1f), { onTap(RideTool.OFF_BIKE) }) { tint ->
            Canvas(Modifier.size(24.dp)) {
                val u = size.width / 24f
                drawRoundRect(tint, Offset(6f * u, 4f * u), Size(4f * u, 16f * u), CornerRadius(1.5f * u))
                drawRoundRect(tint, Offset(14f * u, 4f * u), Size(4f * u, 16f * u), CornerRadius(1.5f * u))
            }
        }
        ActionTile("Riders", open == RideTool.GROUP || s.teamStop != null, Modifier.weight(1f), { onTap(RideTool.GROUP) }) { tint ->
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Person, null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ActionTile(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val tint = if (on) ArmorColors.Orange else ArmorColors.Muted
    Column(
        modifier.height(72.dp).clip(shape).background(if (on) ArmorColors.Orange.copy(alpha = 0.14f) else ArmorColors.S2)
            .border(1.dp, if (on) ArmorColors.Orange else ArmorColors.Line, shape).clickable { Haptics.click(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        icon(tint)
        Txt(label, Type.Label.copy(fontSize = 12.sp), if (on) ArmorColors.Orange else ArmorColors.OnBg, Modifier.padding(top = 6.dp), maxLines = 1)
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
    Column(modifier.panel(radius = 20.dp, neon = false).padding(16.dp)) {
        Txt(label, Type.Eyebrow.copy(fontSize = 12.sp), ArmorColors.Dim)
        Txt(value, Type.Headline.copy(fontSize = 30.sp))
    }
}
