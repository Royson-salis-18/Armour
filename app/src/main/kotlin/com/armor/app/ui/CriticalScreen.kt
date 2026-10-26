package com.armor.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.service.ArmorRuntime
import com.armor.core.engine.IncomingAlert
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.AlertKind
import com.armor.core.safety.SafetyState
import kotlin.math.roundToInt

/**
 * Full-screen takeover for anything that needs the rider *now*. It follows one pattern every time — an icon, a plain
 * title, the key number, what happens next, then one obvious primary action — because the rider may be shaken,
 * gloved or hurt and must never have to work out what to do.
 */
@Composable
fun CriticalScreen(snapshot: RideSnapshot, emergencyNumber: String) {
    val now = snapshot.nowMs
    val sosStatus by ArmorRuntime.sosStatus.collectAsState()

    fun fraction(sinceMs: Long, deadlineMs: Long) = (deadlineMs - now).toFloat() / (deadlineMs - sinceMs).coerceAtLeast(1)

    when (val state = snapshot.safety) {
        is SafetyState.Verifying -> Takeover(
            icon = Icons.Filled.Warning, accent = ArmorColors.Amber, title = "Are you okay?",
            subtitle = "You've stopped away from your group.",
            ring = Ring(fraction(state.sinceMs, state.deadlineMs), fmtMmSs(state.deadlineMs - now), "until your group is alerted"),
            steps = listOf("Your group is alerted" to fmtMmSs(state.deadlineMs - now), "They get a minute to respond" to "then", "SOS is texted to your contacts" to "then"),
        ) {
            PrimaryButton("I'm okay") { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(sub = null, outlined = true) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.GroupAlerted -> Takeover(
            icon = Icons.Filled.Notifications, accent = ArmorColors.Red, title = "Your group has been alerted",
            subtitle = "No answer, so we told them.",
            ring = Ring(fraction(state.sinceMs, state.deadlineMs), fmtMmSs(state.deadlineMs - now), "until SOS is sent"),
            steps = listOf("Waiting for a rider to respond" to "now", "SOS is texted to your contacts" to fmtMmSs(state.deadlineMs - now)),
        ) {
            PrimaryButton("I'm okay") { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(sub = null, outlined = true) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.CrashCountdown -> Takeover(
            icon = Icons.Filled.Warning, accent = ArmorColors.Red, title = "Crash detected",
            subtitle = "We'll send SOS with your location.",
            ring = Ring(fraction(state.sinceMs, state.deadlineMs), fmtMmSs(state.deadlineMs - now), "until SOS is sent"),
            steps = listOf("SOS is texted with your location" to fmtMmSs(state.deadlineMs - now), "Your group is alerted" to "at once", "An alarm sounds at full volume" to "now"),
        ) {
            PrimaryButton("I'm okay — cancel") { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(label = "Hold to send now", sub = null, outlined = true) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.Sos -> {
            val context = LocalContext.current
            Takeover(
                icon = Icons.Filled.Check, accent = ArmorColors.Red, solidIcon = true, title = "SOS sent",
                subtitle = "Your last known location was shared.",
                detail = sosStatus ?: "Your group and emergency contacts have been alerted.",
            ) {
                PrimaryButton("Call $emergencyNumber") {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                SecondaryButton("I'm okay — cancel SOS") { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            }
        }

        else -> snapshot.incomingAlerts.firstOrNull { !it.acknowledgedByMe && it.alert.ackedById == null }?.let {
            IncomingAlertScreen(it, emergencyNumber)
        }
    }
}

@Composable
private fun IncomingAlertScreen(incoming: IncomingAlert, emergencyNumber: String) {
    val context = LocalContext.current
    val alert = incoming.alert
    val what = when (alert.kind) {
        AlertKind.SOS -> "has sent an SOS."
        AlertKind.CRASH -> "may have crashed."
        AlertKind.STATIONARY -> "has stopped and isn't responding."
        AlertKind.HELP -> "asked for a hand."
    }
    val distance = incoming.distanceM
    val bearing = incoming.bearingDeg
    Takeover(
        icon = Icons.Filled.LocationOn, accent = ArmorColors.Orange, title = if (alert.kind == AlertKind.HELP) "${alert.riderName} needs a hand" else "${alert.riderName} needs help",
        subtitle = "${alert.riderName} $what",
        keyFact = distance?.let { fmtDistance(it) },
        keyCaption = if (bearing != null) "${compass(bearing)} of you · bearing ${bearing.roundToInt()}°" else "Location unknown",
    ) {
        PrimaryButton("I'm going", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00)) { ArmorRuntime.send(ArmorRuntime.Command.Acknowledge(alert.id)) }
        SecondaryButton("Call $emergencyNumber") {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Txt(
            "Dismiss", Type.BodyStrong, ArmorColors.Muted, align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickableNoRipple { ArmorRuntime.send(ArmorRuntime.Command.Dismiss(alert.id)) }.padding(8.dp),
        )
    }
}

private data class Ring(val fraction: Float, val time: String, val caption: String)

@Composable
private fun Takeover(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    solidIcon: Boolean = false,
    ring: Ring? = null,
    keyFact: String? = null,
    keyCaption: String? = null,
    detail: String? = null,
    steps: List<Pair<String, String>> = emptyList(),
    buttons: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(ArmorColors.Bg).warmGlow(accent, 0.34f).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArmorLogo(18.dp, accent)
                Txt("SAFETY ALERT", Type.Eyebrow, accent)
            }
            Gap(22.dp)
            Box(Modifier.size(76.dp).background(if (solidIcon) accent else accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (solidIcon) Color.White else accent, modifier = Modifier.size(38.dp))
            }
            Gap(18.dp)
            Txt(title.uppercase(), Type.Hero.copy(fontSize = 40.sp, lineHeight = 42.sp), align = TextAlign.Center)
            Txt(subtitle, Type.Body.copy(fontSize = 16.sp), ArmorColors.Muted, Modifier.padding(top = 6.dp), TextAlign.Center)
            if (ring != null) {
                Gap(22.dp)
                CountdownRing(ring.fraction, accent) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(ring.time, Type.Display)
                        Txt(ring.caption, Type.Body, ArmorColors.Muted, Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (keyFact != null) {
                Gap(26.dp)
                Txt(keyFact, Type.Display.copy(fontSize = 54.sp), accent)
                if (keyCaption != null) Txt(keyCaption, Type.Body, ArmorColors.Muted, Modifier.padding(top = 4.dp), TextAlign.Center)
            }
            if (detail != null) {
                Gap(22.dp)
                Card2 { Txt(detail, Type.Body, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            if (steps.isNotEmpty()) {
                Gap(22.dp)
                ListCard {
                    steps.forEachIndexed { i, (text, trailing) ->
                        if (i > 0) Divider1()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(24.dp).background(ArmorColors.S3, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = ArmorColors.OnBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Txt(text, Type.Body, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            Txt(trailing, Type.Label, ArmorColors.Muted)
                        }
                    }
                }
            }
            Gap(12.dp)
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
    }
}
