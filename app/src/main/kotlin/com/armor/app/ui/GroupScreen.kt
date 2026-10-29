package com.armor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.armor.app.net.ArmorCloudTransport
import com.armor.app.net.CloudSession
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.ArmorApp
import com.armor.app.data.Settings
import com.armor.app.service.ArmorRuntime
import com.armor.core.model.SafetyCode

private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


@Composable
fun GroupScreen(settings: Settings) {
    val prefs = ArmorApp.instance.prefs
    val running by ArmorRuntime.running.collectAsState()
    val snapshot by ArmorRuntime.snapshot.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Group ride", "Ride with friends: everyone sees each other live, and an SOS reaches the whole group.")

        Card2 {
            SectionTitle("You")
            ArmorField(
                value = settings.riderName,
                onValueChange = { v -> prefs.update { it.copy(riderName = v.take(24)) } },
                label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            ArmorField(
                value = settings.phone, onValueChange = { v -> prefs.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' }.take(16)) } },
                label = { Text("Your phone number") }, singleLine = true,
                supportingText = { Text("So your group can text and call you if you go down.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
        }

        RideSessionCard(settings)

        val riders = snapshot?.riders.orEmpty()
        if (settings.backendConfigured) Card2 {
            SectionTitle("Riders")
            if (riders.isEmpty()) {
                Text(if (running) "Waiting for the others to start riding." else "Slide to ride on the home screen to go live.", color = ArmorColors.Muted)
            }
            riders.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (r.isSelf) "${r.name} (you)" else r.name, color = if (r.isOutlier) ArmorColors.Red else ArmorColors.OnBg, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                r.distanceFromSelfM?.takeIf { !r.isSelf }?.let { fmtDistance(it) },
                                fmtSpeed(r.speedMps),
                                r.status?.label,
                                if (r.isStale) "last seen ${fmtMmSs(r.ageMs)} ago" else null,
                            ).joinToString(" · "),
                            color = ArmorColors.Muted, fontSize = 12.sp,
                        )
                    }
                    if (r.safety != SafetyCode.NORMAL) Text(r.safety.name.lowercase(), color = ArmorColors.Amber, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Create a ride or join one with its code. The creator leads and can start and end it for everyone; once in a ride its
 * code is shown big, with a share button that sends an invite link (it opens the web app, or the code goes into this app).
 */
@Composable
private fun RideSessionCard(settings: Settings) {
    val prefs = ArmorApp.instance.prefs
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var rideName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    val name = settings.riderName.ifBlank { "Rider" }

    fun adopt(session: CloudSession?, failure: String) {
        busy = null
        if (session == null) { message = failure; return }
        message = null
        prefs.update { it.copy(riderId = session.riderId, rideCode = session.code, rideToken = session.token, rideName = session.rideName, isRideAdmin = session.isLead) }
    }
    fun forget() = prefs.update { it.copy(rideCode = "", rideToken = "", rideName = "", isRideAdmin = false) }
    fun cloud() = ArmorCloudTransport(settings.rideCode, settings.riderId, settings.rideToken)

    if (settings.backendConfigured) {
        Card2 {
            SectionTitle(if (settings.isRideAdmin) "Your ride · you lead" else "Your ride")
            Txt(settings.rideName.ifBlank { "Group ride" }, Type.Title, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            Txt(settings.rideCode, Type.Display.copy(fontSize = 44.sp, letterSpacing = 10.sp), ArmorColors.Orange, Modifier.fillMaxWidth(), TextAlign.Center)
            Txt("Riders join with this code", Type.Caption, ArmorColors.Muted, Modifier.fillMaxWidth(), TextAlign.Center)
            PrimaryButton("Invite riders", color = ArmorColors.Orange, contentColor = androidx.compose.ui.graphics.Color(0xFF1A0B00)) {
                val text = "Ride with me on ARMOR. Code ${settings.rideCode}, or open ${ArmorCloudTransport.inviteLink(settings.rideCode)}"
                context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Invite riders"))
            }
            if (settings.isRideAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = busy == null, onClick = {
                        busy = "start"
                        scope.launch { val ok = cloud().start(); busy = null; message = if (ok) "Ride started for everyone." else "Couldn't reach the ARMOR server. Check your internet." }
                    }) { Text("Start for everyone", color = ArmorColors.Orange) }
                    TextButton(enabled = busy == null, onClick = {
                        busy = "end"
                        scope.launch { val ok = cloud().end(); busy = null; if (ok) forget() else message = "Couldn't reach the ARMOR server. Check your internet." }
                    }) { Text("End for everyone", color = ArmorColors.Red) }
                }
            }
            TextButton(onClick = { scope.launch { cloud().leave() }; forget() }) { Text("Leave ride", color = ArmorColors.Muted) }
            message?.let { Txt(it, Type.Caption, ArmorColors.Amber) }
        }
    } else {
        Card2 {
            SectionTitle("Start a group ride")
            ArmorField(value = rideName, onValueChange = { rideName = it.take(40) }, label = { Text("Ride name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PrimaryButton(if (busy == "create") "Creating\u2026" else "Create ride", color = ArmorColors.Orange, contentColor = androidx.compose.ui.graphics.Color(0xFF1A0B00)) {
                if (busy == null) {
                    busy = "create"; message = "Connecting to ARMOR\u2026 the first connection can take up to a minute."
                    scope.launch { adopt(ArmorCloudTransport.createRide(rideName.trim().ifBlank { "$name's ride" }, name, com.armor.app.alert.Phones.normalize(context, settings.phone)), "Couldn't create the ride. Check your internet and try again.") }
                }
            }
        }
        Card2 {
            SectionTitle("Join a ride")
            ArmorField(
                value = joinCode, onValueChange = { v -> joinCode = v.uppercase().filter { c -> c in CODE_ALPHABET }.take(6) },
                label = { Text("6-letter ride code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(if (busy == "join") "Joining\u2026" else "Join ride") {
                if (busy == null && joinCode.length == 6) {
                    busy = "join"; message = "Connecting to ARMOR\u2026"
                    scope.launch { adopt(ArmorCloudTransport.joinRide(joinCode, name, com.armor.app.alert.Phones.normalize(context, settings.phone)), "Couldn't join. Check the code (it may have ended) and your internet.") }
                }
            }
            message?.let { Txt(it, Type.Caption, ArmorColors.Muted) }
        }
    }
}
