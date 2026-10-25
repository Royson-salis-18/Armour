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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
private fun Wordmark(size: Int) {
    Text(
        buildAnnotatedString {
            append("ARM")
            withStyle(SpanStyle(color = ArmorColors.Orange)) { append("O") }
            append("R")
        },
        color = ArmorColors.OnBg, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (size / 6).sp,
    )
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Wordmark(38)
        Text("Ride together. Stay watched over.", color = ArmorColors.Muted)
        Gap(6.dp)

        Card2 {
            SectionTitle("Ready to ride?")
            Check("Location (GPS)", granted(Manifest.permission.ACCESS_FINE_LOCATION), "Required to track the ride")
            Check("SMS for SOS", granted(Manifest.permission.SEND_SMS), "Reaches contacts with no internet")
            Check("Emergency contacts", contactCount > 0, if (contactCount == 0) "Add one in Settings" else "$contactCount saved")
            Check("Group", settings.syncConfigured, if (settings.syncConfigured) "Code ${settings.groupCode}" else "Optional. Solo monitoring still works")
        }
        if (denied) Text("Location permission is needed to track your ride. Allow it in system settings and try again.", color = ArmorColors.Red)

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFF8A30), Color(0xFFFF6A00))))
                .clickable { if (!starting) launcher.launch(needed) }
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (starting) "STARTING…" else "START RIDE", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ArmorColors.Surface)
                .border(1.dp, ArmorColors.Line, RoundedCornerShape(20.dp))
                .clickable { if (!starting) RideService.start(context, demo = true) }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Try a demo ride", color = ArmorColors.OnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text("The demo needs no GPS and sends no real messages.", color = ArmorColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Check(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(if (ok) "✓" else "!", if (ok) ArmorColors.Green else ArmorColors.Amber, size = 22.dp, fontSize = 12)
            Text(label, color = ArmorColors.OnBg, fontWeight = FontWeight.Medium)
        }
        Text(detail, color = if (ok) ArmorColors.Muted else ArmorColors.Amber, fontSize = 12.sp)
    }
}

@Composable
internal fun ActiveRide(s: RideSnapshot, demo: Boolean, warning: String?) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(s, demo)
        StatusCard(s)
        warning?.let { Text(it, color = ArmorColors.Amber, fontSize = 12.sp) }

        val mapShape = RoundedCornerShape(22.dp)
        RiderMap(s, Modifier.weight(1f).fillMaxWidth().clip(mapShape).border(1.dp, ArmorColors.Line, mapShape))

        val others = s.riders.filter { !it.isSelf }
        if (others.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(others, key = { it.id }) { RiderCard(it) } }
        }

        if (s.safety is SafetyState.Acknowledged) {
            BigButton("I'M OK", ArmorColors.Green, Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
        }
        QuickStatusRow(s.quickStatus)
        HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        if (demo) DemoControls()
        Gap(2.dp)
    }
}

@Composable
private fun Header(s: RideSnapshot, demo: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Wordmark(20)
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            if (demo) item { StatusChip("DEMO", ArmorColors.Orange) }
            item {
                if (s.gpsLost) StatusChip("GPS lost", ArmorColors.Amber)
                else StatusChip("GPS ${s.self?.accuracyM?.let { "±${it.roundToInt()} m" } ?: "…"}", ArmorColors.Green)
            }
            item { StatusChip(if (s.online) "Online" else "Offline", if (s.online) ArmorColors.Green else ArmorColors.Muted) }
            item {
                StatusChip(
                    when (s.voice) {
                        VoiceRoute.MOBILE_NETWORK -> "Voice"
                        VoiceRoute.HELMET_INTERCOM -> "Intercom"
                        VoiceRoute.UNAVAILABLE -> "No voice"
                    },
                )
            }
        }
        HGap(8.dp)
        Text("End", color = ArmorColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { ArmorRuntime.send(ArmorRuntime.Command.Stop) }.padding(6.dp))
    }
}

@Composable
private fun StatusCard(s: RideSnapshot) {
    val others = s.riders.filter { !it.isSelf && !it.isStale }
    val (title, sub, color, icon) = when (val state = s.safety) {
        is SafetyState.Acknowledged -> StatusLook("${state.byName} is coming to help", "Hold on, help is on the way", ArmorColors.Amber, "🤝")
        is SafetyState.Normal -> when (s.assessment) {
            OutlierAssessment.IN_CLUSTER -> StatusLook(
                "With the group",
                "${others.size + 1} riders within ${fmtDistance(others.maxOfOrNull { it.distanceFromSelfM ?: 0.0 } ?: 0.0)}",
                ArmorColors.Green, "✓",
            )
            OutlierAssessment.OUTLIER -> StatusLook(
                "Separated from the group",
                if (s.stationaryForMs >= 10_000) "Stopped ${fmtMmSs(s.stationaryForMs)}" else "${fmtDistance(others.minOfOrNull { it.distanceFromSelfM ?: 0.0 } ?: 0.0)} from the nearest rider",
                ArmorColors.Red, "!",
            )
            OutlierAssessment.UNKNOWN -> StatusLook(
                if (s.riders.size > 1) "Group out of reach" else "Riding solo",
                if (s.stationaryForMs >= 10_000) "Stopped ${fmtMmSs(s.stationaryForMs)}" else "No other riders in range",
                ArmorColors.Muted, "◎",
            )
        }
        else -> StatusLook("Safety check in progress", "Follow the prompt on screen", ArmorColors.Amber, "!")
    }
    val kmh = max(0.0, s.self?.speedMps ?: 0.0) * 3.6
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(ArmorColors.Surface).border(1.dp, ArmorColors.Line, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Row(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(icon, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(sub, color = ArmorColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(kmh.roundToInt().toString(), color = ArmorColors.OnBg, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("km/h", color = ArmorColors.Muted, fontSize = 11.sp)
            }
        }
    }
}

private data class StatusLook(val title: String, val sub: String, val color: Color, val icon: String)

@Composable
private fun RiderCard(r: RiderView) {
    val tint = when {
        r.isStale -> ArmorColors.Stale
        r.isOutlier -> ArmorColors.Red
        else -> ArmorColors.Orange
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(84.dp).clip(shape).background(ArmorColors.Surface).border(1.dp, ArmorColors.Line, shape).padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(r.name.firstOrNull()?.uppercase() ?: "?", tint)
        Gap(5.dp)
        Text(r.name, color = ArmorColors.OnBg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            when {
                r.isStale -> "last seen"
                r.status != null -> r.status!!.shortLabel()
                r.distanceFromSelfM != null -> fmtDistance(r.distanceFromSelfM!!) + (r.bearingFromSelfDeg?.let { " ${compass(it)}" } ?: "")
                else -> ""
            },
            color = if (r.status != null && !r.isStale) ArmorColors.Amber else ArmorColors.Muted, fontSize = 11.sp, maxLines = 1,
        )
    }
}

@Composable
private fun QuickStatusRow(current: QuickStatus?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickStatus.entries.forEach { st ->
            val on = st == current
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (on) ArmorColors.Orange else ArmorColors.Surface)
                    .border(1.dp, if (on) ArmorColors.Orange else ArmorColors.Line, shape)
                    .clickable { ArmorRuntime.send(ArmorRuntime.Command.SetStatus(if (on) null else st)) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(st.icon(), fontSize = 20.sp)
                Text(st.shortLabel(), color = if (on) Color.Black else ArmorColors.Muted, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DemoControls() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ArmorColors.Surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("DEMO CONTROLS", color = ArmorColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Pill("Stop my bike") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_ME)) } }
            item { Pill("Crash!") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.CRASH_ME)) } }
            item { Pill("Kabir stops") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_PEER)) } }
            item { Pill("Everyone rides") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.RESUME_ALL)) } }
            item { Pill("Peers acknowledge: on/off") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.TOGGLE_PEER_ACK)) } }
        }
    }
}
