package com.armor.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.data.Settings
import com.armor.app.service.ArmorRuntime
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.HazardType
import com.armor.core.model.LatLon
import com.armor.core.sos.SosMessages

/**
 * Hands navigation to Google Maps (turn-by-turn, real roads, traffic). Falls back to the Maps web link in a browser when
 * the Maps app is not installed.
 */
fun openNavigation(context: Context, to: LatLon) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${to.lat},${to.lon}&mode=d"))
        .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(nav)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SosMessages.directionsLink(to))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun dial(context: Context, number: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The ride sheet's safety tools: what the group should know right now (regroup point, low batteries, paused crash
 * detection) and the quick actions (report a hazard, off the bike, regroup here, roadside help).
 */
@Composable
fun SafetyTools(s: RideSnapshot, settings: Settings) {
    val context = LocalContext.current
    var hazardPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.regroup?.let { r ->
            Banner("REGROUP · ${r.detail?.ifBlank { null } ?: "Meeting point"}", "Set by ${r.riderName}", ArmorColors.Orange,
                action = if (r.position != null) "NAVIGATE" else null) { r.position?.let { openNavigation(context, it) } }
        }
        if (s.lowBatteryRiders.isNotEmpty()) {
            Banner("LOW BATTERY", "${s.lowBatteryRiders.joinToString(", ")}: their tracking may stop soon", ArmorColors.Amber)
        }
        if (s.crashDetectionPaused) {
            Banner("CRASH DETECTION PAUSED", "Resumes by itself when you ride again", ArmorColors.Muted, action = "RESUME") {
                ArmorRuntime.send(ArmorRuntime.Command.PauseCrashDetection(false))
            }
        }
        s.hazards.firstOrNull { it.riderId != s.self?.id }?.let { h ->
            val type = HazardType.entries.firstOrNull { it.name == h.detail }
            Banner("AHEAD: ${(type?.label ?: "Hazard").uppercase()}", "Reported by ${h.riderName}", ArmorColors.Amber,
                action = if (h.position != null) "SHOW" else null) { h.position?.let { openNavigation(context, it) } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolChip("Report hazard", Modifier.weight(1f), hazardPicker) { hazardPicker = !hazardPicker }
            ToolChip(if (s.crashDetectionPaused) "Back on bike" else "Off the bike", Modifier.weight(1f), s.crashDetectionPaused) {
                ArmorRuntime.send(ArmorRuntime.Command.PauseCrashDetection(!s.crashDetectionPaused))
            }
            if (s.isLead) ToolChip("Regroup here", Modifier.weight(1f)) { ArmorRuntime.send(ArmorRuntime.Command.SetRegroup("Regroup point")) }
            else if (settings.roadsideNumber.isNotBlank()) ToolChip("Roadside help", Modifier.weight(1f)) { dial(context, settings.roadsideNumber) }
        }
        if (hazardPicker) {
            HazardType.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> ToolChip(t.label, Modifier.weight(1f)) { ArmorRuntime.send(ArmorRuntime.Command.ReportHazard(t)); hazardPicker = false } }
                }
            }
        }
    }
}

@Composable
private fun Banner(title: String, sub: String, accent: Color, action: String? = null, onAction: () -> Unit = {}) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(accent.copy(alpha = 0.12f)).border(1.dp, accent.copy(alpha = 0.5f), shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.Eyebrow.copy(fontSize = 13.sp), accent)
            Txt(sub, Type.Caption, ArmorColors.Muted, maxLines = 2)
        }
        if (action != null) Txt(action, Type.Eyebrow.copy(fontSize = 13.sp), accent, Modifier.clickable(onClick = onAction).padding(6.dp))
    }
}

@Composable
private fun ToolChip(label: String, modifier: Modifier, on: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(44.dp).clip(shape).background(if (on) ArmorColors.Orange.copy(alpha = 0.18f) else ArmorColors.S2)
            .border(1.dp, if (on) ArmorColors.Orange else ArmorColors.Line, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(label, Type.Label.copy(fontSize = 13.sp), if (on) ArmorColors.Orange else ArmorColors.OnBg, maxLines = 1) }
}
