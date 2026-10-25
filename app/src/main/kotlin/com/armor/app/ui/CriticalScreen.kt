package com.armor.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.armor.app.service.ArmorRuntime
import com.armor.core.engine.IncomingAlert
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.AlertKind
import com.armor.core.safety.SafetyState

private fun light(c: Color) = Brush.linearGradient(listOf(c, c.copy(alpha = 0.78f)))
private val WHITE_BTN = Brush.linearGradient(listOf(Color.White, Color(0xFFCFE9FF)))
private val DARK_BTN = Brush.linearGradient(listOf(Color(0xFF0E1D31), Color(0xFF08121F)))

/**
 * Full-screen takeover for anything that needs the rider *now*. Controls are huge and few: the rider may be
 * wearing gloves, shaking, or hurt. Their own emergency always outranks someone else's.
 */
@Composable
fun CriticalScreen(snapshot: RideSnapshot, emergencyNumber: String) {
    val now = snapshot.nowMs
    val sosStatus by ArmorRuntime.sosStatus.collectAsState()

    fun ring(sinceMs: Long, deadlineMs: Long) = (deadlineMs - now).toFloat() / (deadlineMs - sinceMs).coerceAtLeast(1)

    when (val state = snapshot.safety) {
        is SafetyState.Verifying -> Takeover(
            accent = ArmorColors.Amber, tag = "SYSTEM NOTICE", title = "ARE YOU OK?",
            subtitle = "STATIONARY · AWAY FROM GROUP",
            ringFraction = ring(state.sinceMs, state.deadlineMs), ringTime = fmtMmSs(state.deadlineMs - now), ringCaption = "GROUP ALERT IN",
            steps = listOf("Your group is alerted" to fmtMmSs(state.deadlineMs - now), "The group gets a minute to respond" to "THEN", "SOS is texted to your contacts" to "THEN"),
        ) {
            HudButton("I'M OK", Brush.linearGradient(listOf(Color(0xFFFFD36A), Color(0xFFFF9D00))), Color(0xFF1A1000), ArmorColors.Amber) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(sub = null) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.GroupAlerted -> Takeover(
            accent = ArmorColors.Red, tag = "GROUP ALERTED", title = "NO RESPONSE",
            subtitle = "YOUR GROUP HAS BEEN TOLD",
            ringFraction = ring(state.sinceMs, state.deadlineMs), ringTime = fmtMmSs(state.deadlineMs - now), ringCaption = "SOS IN",
            steps = listOf("Waiting for a rider to respond" to "NOW", "SOS texted to your contacts" to fmtMmSs(state.deadlineMs - now)),
        ) {
            HudButton("I'M OK", WHITE_BTN, Color(0xFF06101A), ArmorColors.Cyan) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(quiet = true, sub = null) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.CrashCountdown -> Takeover(
            accent = ArmorColors.Red, tag = "EMERGENCY PROTOCOL", title = "CRASH DETECTED",
            subtitle = "IMPACT + ORIENTATION CHANGE",
            ringFraction = ring(state.sinceMs, state.deadlineMs), ringTime = fmtMmSs(state.deadlineMs - now), ringCaption = "SOS IN",
            steps = listOf("SOS texted with your location" to fmtMmSs(state.deadlineMs - now), "Your group is alerted" to "AT ONCE", "Alarm sounds at full volume" to "NOW"),
        ) {
            HudButton("I'M OK — CANCEL", WHITE_BTN, Color(0xFF06101A), ArmorColors.Cyan) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(label = "HOLD TO SEND NOW", quiet = true, sub = null) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.Sos -> {
            val context = LocalContext.current
            Takeover(
                accent = ArmorColors.Red, tag = "SOS ACTIVE", title = "SOS SENT",
                subtitle = "LAST KNOWN LOCATION SHARED",
                statusText = sosStatus ?: "Your group and emergency contacts have been alerted.",
            ) {
                HudButton("CALL $emergencyNumber", WHITE_BTN, Color(0xFF300008), ArmorColors.Cyan) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                HudButton("I'M OK — CANCEL SOS", DARK_BTN, Color(0xFF9FD8FF), ArmorColors.Violet, textSize = 16, vertical = 18.dp) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
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
    }
    val distance = incoming.distanceM
    val bearing = incoming.bearingDeg
    Takeover(
        accent = ArmorColors.Orange, tag = "ALLY IN DISTRESS", title = "${alert.riderName.uppercase()} NEEDS HELP",
        subtitle = if (bearing != null) "BEARING ${bearing.roundToInt()}° · ${compass(bearing)}" else "LOCATION UNKNOWN",
        bigReadout = distance?.let { fmtDistance(it).uppercase() },
        statusText = "${alert.riderName} $what",
    ) {
        HudButton("I'M GOING", Brush.linearGradient(listOf(Color(0xFFFFB066), Color(0xFFFF6A00))), Color(0xFF1A0800), ArmorColors.Orange) { ArmorRuntime.send(ArmorRuntime.Command.Acknowledge(alert.id)) }
        HudButton("CALL $emergencyNumber", DARK_BTN, Color(0xFF9FD8FF), ArmorColors.Violet, textSize = 16, vertical = 18.dp) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        MonoText(
            "DISMISS", ArmorColors.Muted, 11, spacing = 2.0, align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickableNoRipple { ArmorRuntime.send(ArmorRuntime.Command.Dismiss(alert.id)) }.padding(8.dp),
        )
    }
}

@Composable
private fun Takeover(
    accent: Color,
    tag: String,
    title: String,
    subtitle: String,
    ringFraction: Float? = null,
    ringTime: String = "",
    ringCaption: String = "",
    bigReadout: String? = null,
    statusText: String? = null,
    steps: List<Pair<String, String>> = emptyList(),
    buttons: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().hudBackground().drawBehind {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.28f), radius = size.width * 0.9f))
        },
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SysTag(tag, accent)
            HudPanel(Modifier.fillMaxWidth(), accent = accent, padding = 16.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlowText(title, Color.White, 26, glow = 30f, mono = false, spacing = 3, weight = FontWeight.Black, align = TextAlign.Center)
                    MonoText(subtitle, ArmorColors.Muted, 10, align = TextAlign.Center, spacing = 2.0, modifier = Modifier.padding(top = 4.dp))
                    if (ringFraction != null) {
                        TickRing(ringFraction, accent, Modifier.padding(top = 6.dp), size = 200.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GlowText(ringTime, Color.White, 46, glow = 28f)
                                MonoText(ringCaption, accent, 9, spacing = 2.5)
                            }
                        }
                    }
                    if (bigReadout != null) GlowText(bigReadout, accent, 38, modifier = Modifier.padding(vertical = 14.dp), glow = 26f)
                    if (statusText != null) {
                        Text(statusText, color = ArmorColors.OnBg, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp))
                    }
                }
            }
            if (steps.isNotEmpty()) {
                HudPanel(Modifier.fillMaxWidth(), accent = accent, padding = 14.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        HudLabel("What happens next", accent)
                        steps.forEachIndexed { i, (text, trailing) -> StepRow(i + 1, text, trailing, accent) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            buttons()
        }
    }
}
