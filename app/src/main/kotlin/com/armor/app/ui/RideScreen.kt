package com.armor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.ArmorApp
import com.armor.app.BuildConfig
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

    val needed = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.SEND_SMS)
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
        if (denied) Txt("Location permission is needed to track your ride. Allow it in system settings and try again.", Type.Label, ArmorColors.Red, Modifier.padding(top = 12.dp))
        if (settings.syncConfigured) Txt("Group ${settings.groupCode}", Type.Label, ArmorColors.Muted, Modifier.padding(top = 12.dp))

        Gap(22.dp)
        SlideToStart(if (starting) "STARTING\u2026" else "SLIDE TO RIDE") { if (!starting) launcher.launch(needed) }
        Gap(12.dp)
        SecondaryButton("Try a demo ride") { if (!starting) RideService.start(context, demo = true) }
        Txt("The demo needs no GPS and sends no real messages", Type.Caption, ArmorColors.Dim, Modifier.fillMaxWidth().padding(top = 12.dp), TextAlign.Center)
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
    val googleMap = BuildConfig.MAPS_API_KEY.isNotBlank() && s.online
    val mapView = MapView.of(settings.mapView)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.66f
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Real map tiles when there is a key and a connection; otherwise the offline map, so nothing safety-related waits on tiles.
                if (googleMap) GoogleRiderMap(s, trail.toList(), Modifier.fillMaxSize(), view = mapView)
                else RiderMap(s, Modifier.fillMaxSize(), trail.toList())
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MapChip(if (demo) "ARMOR · DEMO" else "ARMOR")
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            MapChip(if (s.gpsLost) "GPS lost" else "GPS", if (s.gpsLost) ArmorColors.Red else ArmorColors.Green)
                            MapChip(if (s.online) "Online" else "Offline", if (s.online) ArmorColors.Green else ArmorColors.Dim)
                            MapChip("Chat", modifier = Modifier.clickable { chatOpen = true })
                            MapChip("End", modifier = Modifier.clickable { ArmorRuntime.send(ArmorRuntime.Command.Stop) })
                        }
                    }
                    warning?.let { Txt(it, Type.Label, ArmorColors.Amber, Modifier.padding(top = 8.dp)) }
                }
                // Which map view is showing; tap to change it. Only the Google map has views, the offline map has one.
                if (googleMap) MapChip(mapView.label, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clickable { mapPicker = true })
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = maxSheet)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(ArmorColors.S1)
                    .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(ArmorColors.S3))
                Gap(14.dp)
                StatusRow(s, look)
                Gap(14.dp)
                LeadCard(s)
                Gap(10.dp)
                SafetyTools(s, settings)
                Gap(14.dp)
                StatsStrip(s)
                Gap(14.dp)
                val others = s.riders.filter { !it.isSelf }
                if (others.isNotEmpty()) {
                    ConvoyStrip(s)
                    Gap(8.dp)
                }
                if (s.safety is SafetyState.Acknowledged) {
                    PrimaryButton("I'm okay", color = ArmorColors.Green, contentColor = Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
                    Gap(14.dp)
                }
                QuickStatusRow(s.quickStatus)
                Gap(16.dp)
                HelpRow(s)
                Gap(10.dp)
                HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
                if (demo) {
                    Gap(14.dp)
                    DemoControls()
                }
            }
        }
        if (chatOpen) ChatScreen(s) { chatOpen = false }
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

/** Non-emergency "I need a hand": one tap to ask, and the same row shows who is coming and lets you cancel. */
@Composable
private fun HelpRow(s: RideSnapshot) {
    val help = s.help
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape).background(if (help != null) ArmorColors.Orange.copy(alpha = 0.14f) else ArmorColors.S2)
            .border(1.dp, if (help != null) ArmorColors.Orange else ArmorColors.Line, shape)
            .clickable { ArmorRuntime.send(if (help != null) ArmorRuntime.Command.CancelHelp else ArmorRuntime.Command.RequestHelp) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(
            when {
                help == null -> "Need a hand?"
                help.ackedByName != null -> "${help.ackedByName} is coming"
                else -> "Help requested · waiting"
            },
            Type.BodyStrong, if (help != null) ArmorColors.Orange else ArmorColors.OnBg,
        )
        Txt(if (help != null) "CANCEL" else "ASK GROUP", Type.Eyebrow, if (help != null) ArmorColors.Orange else ArmorColors.Muted)
    }
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
