package com.armor.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

/**
 * Full-screen takeover for anything that needs the rider *now*. Controls are huge and few: the rider may be
 * wearing gloves, shaking, or hurt. Their own emergency always outranks someone else's.
 */
@Composable
fun CriticalScreen(snapshot: RideSnapshot, emergencyNumber: String) {
    val now = snapshot.nowMs
    val sosStatus by ArmorRuntime.sosStatus.collectAsState()

    when (val state = snapshot.safety) {
        is SafetyState.Verifying -> Takeover(
            background = ArmorColors.Amber.copy(alpha = 0.95f), foreground = Color.Black,
            title = "ARE YOU OK?",
            big = fmtMmSs(state.deadlineMs - now),
            body = "You've stopped away from the group. Tap below or your group will be alerted.",
        ) {
            BigButton("I'M OK", Color.Black, ArmorColors.Amber) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.GroupAlerted -> Takeover(
            background = ArmorColors.Red.copy(alpha = 0.95f), foreground = Color.White,
            title = "GROUP ALERTED",
            big = fmtMmSs(state.deadlineMs - now),
            body = "No answer, so your group has been told. SOS goes to your emergency contacts when this reaches zero unless someone responds.",
        ) {
            BigButton("I'M OK", Color.White, ArmorColors.Red) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.CrashCountdown -> Takeover(
            background = ArmorColors.Red.copy(alpha = 0.97f), foreground = Color.White,
            title = "CRASH DETECTED",
            big = fmtMmSs(state.deadlineMs - now),
            body = "Sending SOS with your location when this reaches zero.",
        ) {
            BigButton("I'M OK — CANCEL", Color.White, ArmorColors.Red) { ArmorRuntime.send(ArmorRuntime.Command.RiderOk) }
            HoldToSosButton(label = "HOLD TO SEND NOW") { ArmorRuntime.send(ArmorRuntime.Command.ManualSos) }
        }

        is SafetyState.Sos -> {
            val context = LocalContext.current
            Takeover(
                background = ArmorColors.Red, foreground = Color.White,
                title = "SOS SENT",
                big = null,
                body = sosStatus ?: "Your group and emergency contacts have been alerted.",
            ) {
                BigButton("CALL $emergencyNumber", Color.White, ArmorColors.Red) {
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
        background = ArmorColors.Orange, foreground = Color.Black,
        title = "${alert.riderName.uppercase()} NEEDS HELP",
        big = null,
        body = "${alert.riderName} $what.\n$where",
    ) {
        BigButton("I'M GOING", Color.Black, ArmorColors.Orange) { ArmorRuntime.send(ArmorRuntime.Command.Acknowledge(alert.id)) }
        BigButton("CALL $emergencyNumber", Color.Black.copy(alpha = 0.25f), Color.Black) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        BigButton("DISMISS", Color.Transparent, Color.Black) { ArmorRuntime.send(ArmorRuntime.Command.Dismiss(alert.id)) }
    }
}

@Composable
private fun Takeover(
    background: Color,
    foreground: Color,
    title: String,
    big: String?,
    body: String,
    buttons: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, color = foreground, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (big != null) {
            Text(big, color = foreground, fontSize = 96.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text(body, color = foreground, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        buttons()
    }
}
