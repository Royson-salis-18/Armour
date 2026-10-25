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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.ArmorApp
import com.armor.app.data.Settings
import com.armor.app.service.ArmorRuntime
import com.armor.app.service.RideService
import com.armor.app.sim.DemoAction
import com.armor.core.engine.RideSnapshot
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
    val s = snapshot
    if (!running || s == null) PreRideHost(settings, starting = running) else ActiveRide(s, demo, warning)
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

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Txt("RIDER SAFETY", Type.Label.copy(letterSpacing = 1.5.sp), ArmorColors.Orange)
        Gap(8.dp)
        Txt("Ride together.", Type.Headline.copy(fontSize = 38.sp, lineHeight = 42.sp))
        Txt("Stay protected.", Type.Headline.copy(fontSize = 38.sp, lineHeight = 42.sp), ArmorColors.Orange)
        Txt("ARMOR watches over your group and calls for help if you can't.", Type.Body, ArmorColors.Muted, Modifier.padding(top = 12.dp, bottom = 28.dp))

        val locOk = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val smsOk = granted(Manifest.permission.SEND_SMS)
        ListCard {
            ListRow("Location", icon = Icons.Filled.LocationOn, trailing = if (locOk) "Granted" else "Needed", trailingColor = if (locOk) ArmorColors.Green else ArmorColors.Amber)
            Divider1()
            ListRow("SOS by text message", icon = Icons.Filled.Email, trailing = if (smsOk) "Granted" else "Needed", trailingColor = if (smsOk) ArmorColors.Green else ArmorColors.Amber)
            Divider1()
            ListRow("Emergency contacts", icon = Icons.Filled.Person, trailing = if (contactCount > 0) "$contactCount saved" else "Add one in Settings", trailingColor = if (contactCount > 0) ArmorColors.Green else ArmorColors.Amber)
            Divider1()
            ListRow("Group", icon = Icons.Filled.AccountCircle, trailing = if (settings.syncConfigured) settings.groupCode else "Optional", trailingColor = if (settings.syncConfigured) ArmorColors.Green else ArmorColors.Muted)
        }
        if (denied) Txt("Location permission is needed to track your ride. Allow it in system settings and try again.", Type.Label, ArmorColors.Red, Modifier.padding(top = 12.dp))

        Gap(24.dp)
        PrimaryButton(if (starting) "Starting…" else "Start ride", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00), arrow = true) { if (!starting) launcher.launch(needed) }
        Gap(12.dp)
        SecondaryButton("Try a demo ride") { if (!starting) RideService.start(context, demo = true) }
        Txt("The demo needs no GPS and sends no real messages", Type.Caption, ArmorColors.Dim, Modifier.fillMaxWidth().padding(top = 12.dp), TextAlign.Center)
    }
}

@Composable
internal fun ActiveRide(s: RideSnapshot, demo: Boolean, warning: String?) {
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.66f
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RiderMap(s, Modifier.fillMaxSize(), trail.toList())
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MapChip(if (demo) "ARMOR · Demo" else "ARMOR")
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            MapChip(if (s.gpsLost) "GPS lost" else "GPS", if (s.gpsLost) ArmorColors.Red else ArmorColors.Green)
                            MapChip(if (s.online) "Online" else "Offline", if (s.online) ArmorColors.Green else ArmorColors.Dim)
                            MapChip("End", modifier = Modifier.clickable { ArmorRuntime.send(ArmorRuntime.Command.Stop) })
                        }
                    }
                    warning?.let { Txt(it, Type.Label, ArmorColors.Amber, Modifier.padding(top = 8.dp)) }
                }
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
                val others = s.riders.filter { !it.isSelf }
                if (others.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(others, key = { it.id }) { RiderItem(it) } }
                    Gap(16.dp)
                }
                if (s.safety is SafetyState.Acknowledged) {
                    PrimaryButton("I'm okay", color = ArmorColors.Green, contentColor = Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
                    Gap(14.dp)
                }
                QuickStatusRow(s.quickStatus)
                Gap(16.dp)
                HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
                if (demo) {
                    Gap(14.dp)
                    DemoControls()
                }
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
        SpeedGauge(kmh)
    }
}

/** Orange arc gauge, 270° sweep, filling with speed (full at 120 km/h). */
@Composable
private fun SpeedGauge(kmh: Double) {
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val round = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, inset, arc, style = round)
            val fill = (kmh / 120.0).toFloat().coerceIn(0f, 1f)
            if (fill > 0.02f) drawArc(ArmorColors.Orange, 135f, 270f * fill, false, inset, arc, style = round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(kmh.roundToInt().toString(), Type.Speed.copy(fontSize = 26.sp))
            Txt("km/h", Type.Caption, ArmorColors.Dim)
        }
    }
}

@Composable
private fun RiderItem(r: RiderView) {
    val color = if (r.isStale) ArmorColors.Dim else ArmorColors.riderColor(r.id)
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Avatar(r.name.firstOrNull()?.uppercase() ?: "?", color)
            if (r.isOutlier && !r.isStale) {
                Box(Modifier.align(Alignment.TopEnd).size(16.dp).background(ArmorColors.S1, CircleShape).padding(2.dp).background(ArmorColors.Red, CircleShape), contentAlignment = Alignment.Center) {
                    Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Txt(r.name, Type.Label.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp), maxLines = 1)
        Txt(
            when {
                r.isStale -> "Last seen"
                r.status != null -> r.status!!.shortLabel()
                r.distanceFromSelfM != null -> fmtDistance(r.distanceFromSelfM!!) + (r.bearingFromSelfDeg?.let { " ${compass(it)}" } ?: "")
                else -> ""
            },
            Type.Caption, if (r.status != null && !r.isStale) ArmorColors.Amber else ArmorColors.Muted, maxLines = 1,
        )
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
