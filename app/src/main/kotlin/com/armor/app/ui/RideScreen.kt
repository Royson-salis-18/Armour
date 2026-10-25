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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.armor.core.comms.VoiceRoute
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
private fun Logo(size: Int) {
    HexBadge("A", ArmorColors.Cyan, size = size.dp, textColor = Color(0xFF021018))
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(92)
        GlowText("ARMOR", ArmorColors.OnBg, 34, glow = 28f, mono = false, spacing = 9, weight = FontWeight.Black)
        MonoText("RIDER SAFETY SYSTEM", ArmorColors.Cyan, 10, spacing = 3.0)
        Gap(6.dp)

        HudPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                HudLabel("System check")
                Gap(6.dp)
                Check("Location lock", granted(Manifest.permission.ACCESS_FINE_LOCATION), "GRANTED")
                Check("SOS messaging", granted(Manifest.permission.SEND_SMS), "GRANTED")
                Check("Emergency contacts", contactCount > 0, if (contactCount == 0) "ADD ONE" else "$contactCount SAVED")
                Check("Group link", settings.syncConfigured, if (settings.syncConfigured) settings.groupCode else "OPTIONAL")
            }
        }
        if (denied) Text("Location permission is needed to track your ride. Allow it in system settings and try again.", color = ArmorColors.Red, fontSize = 12.sp)

        HudButton(
            if (starting) "STARTING…" else "INITIATE RIDE",
            Brush.linearGradient(listOf(Color(0xFFFF9A40), Color(0xFFFF5A00))), Color(0xFF1A0800), glow = ArmorColors.Orange,
        ) { if (!starting) launcher.launch(needed) }

        HudPanel(Modifier.fillMaxWidth().clickable { if (!starting) RideService.start(context, demo = true) }, accent = ArmorColors.Violet) {
            Text("TRY DEMO RIDE", Modifier.fillMaxWidth(), color = ArmorColors.OnBg, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        }
        MonoText("NO GPS · NO REAL MESSAGES", size = 10, spacing = 1.0)
    }
}

@Composable
private fun Check(label: String, ok: Boolean, detail: String) {
    val c = if (ok) ArmorColors.Green else ArmorColors.Amber
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).border(1.5.dp, c, CutShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(if (ok) "✓" else "!", color = c, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text(label, Modifier.weight(1f).padding(start = 10.dp), color = ArmorColors.OnBg, fontSize = 14.sp)
        MonoText(detail, if (ok) ArmorColors.Muted else ArmorColors.Amber, 10)
    }
}

@Composable
internal fun ActiveRide(s: RideSnapshot, demo: Boolean, warning: String?) {
    // Your recent route, for the glowing trail on the map.
    val trail = remember { mutableStateListOf<LatLon>() }
    val here = s.self?.position
    LaunchedEffect(here) {
        if (here != null && (trail.isEmpty() || Geo.distanceM(trail.last(), here) > 4.0)) {
            trail.add(here)
            if (trail.size > 80) trail.removeAt(0)
        }
    }
    val look = linkLook(s)

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(s, demo)
        warning?.let { Text(it, color = ArmorColors.Amber, fontSize = 12.sp) }

        HudPanel(Modifier.weight(1f).fillMaxWidth(), accent = look.color, cut = 16.dp, padding = 0.dp) {
            RiderMap(s, Modifier.fillMaxSize(), trail.toList())
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VelocityPanel(s, Modifier.weight(1f).fillMaxHeight())
            LinkPanel(s, look, Modifier.weight(1f).fillMaxHeight())
        }

        val others = s.riders.filter { !it.isSelf }
        if (others.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(others, key = { it.id }) { RiderCard(it) } }
        }

        if (s.safety is SafetyState.Acknowledged) {
            HudButton("I'M OK", Brush.linearGradient(listOf(ArmorColors.Green, Color(0xFF14B870))), Color.Black, ArmorColors.Green, vertical = 16.dp) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
        }
        QuickStatusRow(s.quickStatus)
        HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        if (demo) DemoControls()
        Gap(2.dp)
    }
}

@Composable
private fun Header(s: RideSnapshot, demo: Boolean) {
    val gps = when {
        s.gpsLost -> 0
        (s.self?.accuracyM ?: 99.0) <= 5 -> 4
        (s.self?.accuracyM ?: 99.0) <= 10 -> 3
        (s.self?.accuracyM ?: 99.0) <= 25 -> 2
        else -> 1
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(34)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            GlowText("ARMOR", ArmorColors.OnBg, 17, glow = 14f, mono = false, spacing = 4, weight = FontWeight.Black)
            MonoText(if (demo) "// DEMO MODE" else "// SYSTEM ONLINE", ArmorColors.Cyan, 8, spacing = 2.0)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            SignalBars(gps, if (s.gpsLost) ArmorColors.Red else ArmorColors.Green, "GPS")
            SignalBars(if (s.online) 4 else 0, ArmorColors.Cyan, "NET")
            SignalBars(
                when (s.voice) { VoiceRoute.MOBILE_NETWORK -> 4; VoiceRoute.HELMET_INTERCOM -> 3; VoiceRoute.UNAVAILABLE -> 0 },
                ArmorColors.Violet, "VOICE",
            )
        }
        Text("END", Modifier.clickable { ArmorRuntime.send(ArmorRuntime.Command.Stop) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), color = ArmorColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

private data class LinkLook(val title: String, val sub: String, val color: Color)

private fun linkLook(s: RideSnapshot): LinkLook {
    val others = s.riders.filter { !it.isSelf && !it.isStale }
    return when (val state = s.safety) {
        is SafetyState.Acknowledged -> LinkLook("ASSISTED", "${state.byName} is on the way", ArmorColors.Amber)
        is SafetyState.Normal -> when (s.assessment) {
            OutlierAssessment.IN_CLUSTER -> LinkLook("STABLE", "${others.size + 1} riders in formation", ArmorColors.Green)
            OutlierAssessment.OUTLIER -> LinkLook(
                "LOST",
                if (s.stationaryForMs >= 10_000) "Stopped ${fmtMmSs(s.stationaryForMs)}" else "${fmtDistance(others.minOfOrNull { it.distanceFromSelfM ?: 0.0 } ?: 0.0)} from nearest",
                ArmorColors.Red,
            )
            OutlierAssessment.UNKNOWN -> LinkLook(
                if (s.riders.size > 1) "NO LINK" else "SOLO",
                if (s.stationaryForMs >= 10_000) "Stopped ${fmtMmSs(s.stationaryForMs)}" else "No other riders in range",
                ArmorColors.Muted,
            )
        }
        else -> LinkLook("CHECKING", "Follow the prompt", ArmorColors.Amber)
    }
}

@Composable
private fun VelocityPanel(s: RideSnapshot, modifier: Modifier) {
    val kmh = max(0.0, s.self?.speedMps ?: 0.0) * 3.6
    HudPanel(modifier) {
        Column(Modifier.fillMaxWidth()) {
            HudLabel("Velocity")
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                GlowText(kmh.roundToInt().toString(), ArmorColors.OnBg, 40, glow = 26f)
                MonoText(" KM/H", size = 11, modifier = Modifier.padding(bottom = 6.dp))
            }
            Gap(4.dp)
            SegmentBar((kmh / 10).roundToInt().coerceIn(0, 12), 12, ArmorColors.Cyan)
            Gap(4.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoText("GPS", size = 10)
                MonoText(if (s.gpsLost) "LOST" else "±${(s.self?.accuracyM ?: 0.0).roundToInt()} M", ArmorColors.OnBg, 10, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LinkPanel(s: RideSnapshot, look: LinkLook, modifier: Modifier) {
    val fresh = s.riders.filter { !it.isStale }
    val spread = fresh.filter { !it.isSelf }.maxOfOrNull { it.distanceFromSelfM ?: 0.0 } ?: 0.0
    HudPanel(modifier, accent = look.color) {
        Column(Modifier.fillMaxWidth()) {
            HudLabel("Link", look.color)
            GlowText(look.title, look.color, 20, glow = 18f, mono = false, modifier = Modifier.padding(top = 6.dp))
            Text(look.sub, color = ArmorColors.Muted, fontSize = 10.5.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
            SegmentBar(fresh.count { !it.isOutlier }, fresh.size.coerceIn(1, 10), look.color)
            Gap(4.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoText("SPREAD", size = 10)
                MonoText(fmtDistance(spread).uppercase(), ArmorColors.OnBg, 10, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RiderCard(r: RiderView) {
    val tint = when {
        r.isStale -> ArmorColors.Stale
        r.isOutlier -> ArmorColors.Red
        else -> ArmorColors.Orange
    }
    val shape = CutShape(8.dp)
    Column(
        Modifier.width(84.dp).clip(shape).background(Brush.verticalGradient(listOf(ArmorColors.Panel, ArmorColors.PanelDeep))).border(1.dp, ArmorColors.Line, shape).padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HexBadge(r.name.firstOrNull()?.uppercase() ?: "?", tint, size = 32.dp)
        Gap(5.dp)
        Text(r.name, color = ArmorColors.OnBg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        MonoText(
            when {
                r.isStale -> "LAST SEEN"
                r.status != null -> r.status!!.shortLabel().uppercase()
                r.distanceFromSelfM != null -> fmtDistance(r.distanceFromSelfM!!).uppercase().replace(" ", "") + (r.bearingFromSelfDeg?.let { " ${compass(it)}" } ?: "")
                else -> ""
            },
            if (r.status != null && !r.isStale) ArmorColors.Amber else ArmorColors.Muted, 9,
        )
    }
}

@Composable
private fun QuickStatusRow(current: QuickStatus?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        QuickStatus.entries.forEach { st ->
            val on = st == current
            val shape = CutShape(8.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (on) Brush.verticalGradient(listOf(ArmorColors.Cyan, Color(0xFF1AA6C9))) else Brush.verticalGradient(listOf(ArmorColors.Panel, ArmorColors.PanelDeep)))
                    .border(1.dp, if (on) ArmorColors.Cyan else ArmorColors.Line, shape)
                    .clickable { ArmorRuntime.send(ArmorRuntime.Command.SetStatus(if (on) null else st)) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(st.icon(), fontSize = 19.sp)
                Text(st.shortLabel().uppercase(), color = if (on) Color(0xFF022027) else ArmorColors.Muted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DemoControls() {
    HudPanel(Modifier.fillMaxWidth(), accent = ArmorColors.Violet, padding = 10.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudLabel("Demo controls", ArmorColors.Violet)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("Stop my bike") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_ME)) } }
                item { Pill("Crash!") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.CRASH_ME)) } }
                item { Pill("Kabir stops") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_PEER)) } }
                item { Pill("Everyone rides") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.RESUME_ALL)) } }
                item { Pill("Peers acknowledge: on/off") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.TOGGLE_PEER_ACK)) } }
            }
        }
    }
}
