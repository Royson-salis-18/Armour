package com.armor.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private fun gradient(top: Long, bottom: Long) = Brush.verticalGradient(listOf(Color(top), Color(bottom)))

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
            background = gradient(0xFFFFD54A, 0xFFFFB300), foreground = Color.Black,
            icon = "⚠", title = "ARE YOU OK?",
            ringFraction = ring(state.sinceMs, state.deadlineMs), ringTime = fmtMmSs(state.deadlineMs - now), ringCaption = "GROUP ALERT IN",
            body = "You've stopped away from the group. Tap below or your group will be alerted.",
        ) {
            BigButton("I'M OK", Color.Black, Color(0xFFFFC107)) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(sub = null) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.GroupAlerted -> Takeover(
            background = gradient(0xFFFF4A3F, 0xFFC91F16), foreground = Color.White,
            icon = "📣", title = "GROUP ALERTED",
            ringFraction = ring(state.sinceMs, state.deadlineMs), ringTime = fmtMmSs(state.deadlineMs - now), ringCaption = "SOS IN",
            body = "No answer, so your group has been told. SOS goes to your emergency contacts at zero unless someone responds.",
        ) {
            BigButton("I'M OK", Color.White, Color(0xFFC91F16)) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(quiet = true, sub = null) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.CrashCountdown -> Takeover(
            background = gradient(0xFFFF4A3F, 0xFFC91F16), foreground = Color.White,
            icon = "💥", title = "CRASH DETECTED",
            ringFraction = ring(state.sinceMs, state.deadlineMs), ringTime = fmtMmSs(state.deadlineMs - now), ringCaption = "SOS IN",
            body = "Sending SOS with your location when this reaches zero.",
        ) {
            BigButton("I'M OK — CANCEL", Color.White, Color(0xFFC91F16)) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(label = "HOLD TO SEND NOW", quiet = true, sub = null) { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.Sos -> {
            val context = LocalContext.current
            Takeover(
                background = gradient(0xFFFF3B30, 0xFFA8150D), foreground = Color.White,
                icon = "🛡", title = "SOS SENT",
                body = sosStatus ?: "Your group and emergency contacts have been alerted.",
                bodyCard = true,
            ) {
                BigButton("CALL $emergencyNumber", Color.White, Color(0xFFA8150D)) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                BigButton("I'M OK — CANCEL SOS", Color.Black.copy(alpha = 0.35f), Color.White) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
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
        AlertKind.SOS -> "has sent an SOS"
        AlertKind.CRASH -> "may have crashed"
        AlertKind.STATIONARY -> "has stopped and isn't responding"
    }
    val distance = incoming.distanceM
    val bearing = incoming.bearingDeg
    val where = if (distance != null && bearing != null) "${fmtDistance(distance)} to the ${compass(bearing)}" else "location unknown"
    Takeover(
        background = gradient(0xFFFF8A30, 0xFFFF6A00), foreground = Color.Black,
        icon = "🏍", title = "${alert.riderName.uppercase()} NEEDS HELP",
        bigText = where,
        body = "${alert.riderName} $what.",
    ) {
        BigButton("I'M GOING", Color.Black, Color(0xFFFF8A30)) { ArmorRuntime.send(ArmorRuntime.Command.Acknowledge(alert.id)) }
        BigButton("CALL $emergencyNumber", Color.Black.copy(alpha = 0.2f), Color.Black) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Text(
            "Dismiss", color = Color.Black.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickableNoRipple { ArmorRuntime.send(ArmorRuntime.Command.Dismiss(alert.id)) }.padding(8.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Takeover(
    background: Brush,
    foreground: Color,
    icon: String,
    title: String,
    body: String,
    ringFraction: Float? = null,
    ringTime: String = "",
    ringCaption: String = "",
    bigText: String? = null,
    bodyCard: Boolean = false,
    buttons: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 44.sp)
        Text(title, color = foreground, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        if (ringFraction != null) {
            CountdownRing(ringFraction, color = foreground, track = foreground.copy(alpha = 0.22f), size = 190.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ringTime, color = foreground, fontSize = 54.sp, fontWeight = FontWeight.Black)
                    Text(ringCaption, color = foreground.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
        }
        if (bigText != null) Text(bigText, color = foreground, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        if (bodyCard) {
            val shape = RoundedCornerShape(20.dp)
            Text(
                body, color = foreground, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f), shape).border(1.dp, Color.White.copy(alpha = 0.2f), shape).padding(16.dp),
            )
        } else {
            Text(body, color = foreground.copy(alpha = 0.92f), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        buttons()
    }
}
