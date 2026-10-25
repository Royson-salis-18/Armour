package com.armor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ARMOR", color = ArmorColors.Orange, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Advanced Rider Monitoring & Orientation Reasoning System", color = ArmorColors.Muted, fontSize = 13.sp)

        Card2 {
            SectionTitle("Before you ride")
            Check("Location (GPS)", granted(Manifest.permission.ACCESS_FINE_LOCATION), "Required to track the ride")
            Check("SMS", granted(Manifest.permission.SEND_SMS), "Lets SOS reach your contacts with no internet")
            Check("Emergency contacts", contactCount > 0, if (contactCount == 0) "Add at least one in Settings" else "$contactCount saved")
            Check("Group", settings.syncConfigured, if (settings.syncConfigured) "Code ${settings.groupCode}" else "Optional: set up in the Group tab. Solo monitoring still works")
        }
        if (denied) Text("Location permission is needed to track your ride. Allow it in system settings and try again.", color = ArmorColors.Red)

        BigButton(if (starting) "STARTING…" else "START RIDE", ArmorColors.Orange, Color.Black) {
            if (!starting) launcher.launch(needed)
        }
        BigButton("TRY A DEMO RIDE", ArmorColors.SurfaceHigh, ArmorColors.OnBg) {
            if (!starting) RideService.start(context, demo = true)
        }
        Text(
            "The demo needs no GPS and sends no real messages: it simulates a group ride so you can watch a stop, a group alert, an SOS and a crash play out.",
            color = ArmorColors.Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun Check(label: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (ok) "●" else "○", color = if (ok) ArmorColors.Green else ArmorColors.Amber, fontSize = 16.sp)
        Column {
            Text(label, color = ArmorColors.OnBg, fontWeight = FontWeight.Medium)
            Text(detail, color = ArmorColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun ActiveRide(s: RideSnapshot, demo: Boolean, warning: String?) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusStrip(s, demo)
        Banner(s)
        warning?.let { Text(it, color = ArmorColors.Amber, fontSize = 12.sp) }

        RiderMap(s, Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.riders.filter { !it.isSelf }, key = { it.id }) { RiderChip(it) }
        }

        if (s.safety is SafetyState.Acknowledged) {
            BigButton("I'M OK", ArmorColors.Green, Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
        }
        QuickStatusRow(s.quickStatus)
        HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        if (demo) DemoControls()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { ArmorRuntime.send(ArmorRuntime.Command.Stop) }) { Text("End ride", color = ArmorColors.Muted) }
        }
    }
}

@Composable
private fun StatusStrip(s: RideSnapshot, demo: Boolean) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (demo) item { Pill("DEMO", color = ArmorColors.Orange, contentColor = Color.Black) }
        item {
            if (s.gpsLost) Pill("GPS lost", color = ArmorColors.Amber, contentColor = Color.Black)
            else Pill("GPS ${s.self?.accuracyM?.let { "±${it.toInt()} m" } ?: "…"}")
        }
        item { Pill(if (s.online) "Online" else "Offline", color = if (s.online) ArmorColors.SurfaceHigh else ArmorColors.OrangeDim) }
        item {
            Pill(
                when (s.voice) {
                    VoiceRoute.MOBILE_NETWORK -> "Voice: mobile network"
                    VoiceRoute.HELMET_INTERCOM -> "Voice: helmet intercom"
                    VoiceRoute.UNAVAILABLE -> "Voice: none"
                },
            )
        }
    }
}

@Composable
private fun Banner(s: RideSnapshot) {
    val (text, color) = when (val state = s.safety) {
        is SafetyState.Acknowledged -> "${state.byName} is coming to help" to ArmorColors.Amber
        is SafetyState.Normal -> when (s.assessment) {
            OutlierAssessment.IN_CLUSTER -> "With the group · ${s.riders.count { !it.isStale }} riders" to ArmorColors.Green
            OutlierAssessment.OUTLIER -> "Separated from the group" to ArmorColors.Red
            OutlierAssessment.UNKNOWN -> (if (s.riders.size > 1) "Group out of reach" else "Riding solo") to ArmorColors.Muted
        }
        else -> "Safety check in progress" to ArmorColors.Amber
    }
    val stopped = if (s.stationaryForMs >= 10_000) "  ·  stopped ${fmtMmSs(s.stationaryForMs)}" else ""
    Text(text + stopped, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}

@Composable
private fun RiderChip(r: RiderView) {
    val tint = when {
        r.isStale -> ArmorColors.Stale
        r.isOutlier -> ArmorColors.Red
        else -> ArmorColors.Orange
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(ArmorColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(r.name, color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            buildString {
                r.distanceFromSelfM?.let { append(fmtDistance(it)); r.bearingFromSelfDeg?.let { b -> append(" ${compass(b)}") } }
                if (r.isStale) append(" · last seen")
            },
            color = ArmorColors.Muted, fontSize = 12.sp,
        )
        r.status?.let { Text(it.label, color = ArmorColors.Amber, fontSize = 12.sp) }
    }
}

@Composable
private fun QuickStatusRow(current: QuickStatus?) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(QuickStatus.entries.toList(), key = { it.name }) { st ->
            Pill(st.label, selected = st == current) {
                ArmorRuntime.send(ArmorRuntime.Command.SetStatus(if (st == current) null else st))
            }
        }
    }
}

@Composable
private fun DemoControls() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ArmorColors.Surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("DEMO CONTROLS", color = ArmorColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Pill("Stop my bike") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_ME)) } }
            item { Pill("Crash!") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.CRASH_ME)) } }
            item { Pill("Kabir stops") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.STOP_PEER)) } }
            item { Pill("Everyone rides") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.RESUME_ALL)) } }
            item { Pill("Peers acknowledge: on/off") { ArmorRuntime.send(ArmorRuntime.Command.Demo(DemoAction.TOGGLE_PEER_ACK)) } }
        }
    }
}
