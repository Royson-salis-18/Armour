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
import androidx.compose.material3.OutlinedTextField
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
import com.armor.app.net.ArmorBackendTransport
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
import java.security.SecureRandom

private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private fun newGroupCode(): String {
    val rnd = SecureRandom()
    return (1..6).map { CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)] }.joinToString("")
}

@Composable
fun GroupScreen(settings: Settings) {
    val prefs = ArmorApp.instance.prefs
    val running by ArmorRuntime.running.collectAsState()
    val snapshot by ArmorRuntime.snapshot.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Group")
        if (running) Text("Changes apply from your next ride.", color = ArmorColors.Amber, fontSize = 12.sp)

        RideSessionCard(settings)

        if (settings.groupCode.isNotBlank() && !settings.backendConfigured) {
            Card2 {
                Txt("GROUP CODE", Type.Caption, ArmorColors.Dim, Modifier.fillMaxWidth(), TextAlign.Center)
                Txt(settings.groupCode, Type.Display.copy(fontSize = 42.sp, letterSpacing = 10.sp), ArmorColors.Orange, Modifier.fillMaxWidth(), TextAlign.Center)
                Text("Share it with the riders you're joining", color = ArmorColors.Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        Card2 {
            SectionTitle("You")
            OutlinedTextField(
                value = settings.riderName,
                onValueChange = { v -> prefs.update { it.copy(riderName = v.take(24)) } },
                label = { Text("Your name (shown to the group)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Card2 {
            SectionTitle("Ride group")
            OutlinedTextField(
                value = settings.groupCode,
                onValueChange = { v -> prefs.update { it.copy(groupCode = v.uppercase().filter { c -> c in CODE_ALPHABET }.take(6)) } },
                label = { Text("Group code (share it with your riders)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                TextButton(onClick = { prefs.update { it.copy(groupCode = newGroupCode()) } }) { Text("Create a new code", color = ArmorColors.Orange) }
                TextButton(onClick = { prefs.update { it.copy(groupCode = "") } }) { Text("Leave group", color = ArmorColors.Muted) }
            }
            OutlinedTextField(
                value = settings.firebaseUrl,
                onValueChange = { v -> prefs.update { it.copy(firebaseUrl = v.trim()) } },
                label = { Text("Firebase Realtime Database URL") },
                supportingText = { Text("Optional. Without it ARMOR works fully offline; with it, riders see each other and share alerts.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledRow("Online sync", if (settings.syncConfigured) "Configured" else "Off", if (settings.syncConfigured) ArmorColors.Green else ArmorColors.Muted)
        }

        val riders = snapshot?.riders.orEmpty()
        Card2 {
            SectionTitle("Riders now")
            if (riders.isEmpty()) {
                Text(if (running) "Nobody else is visible yet." else "Start a ride to see your group.", color = ArmorColors.Muted)
            }
            riders.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (r.isSelf) "${r.name} (you)" else r.name, color = if (r.isOutlier) ArmorColors.Red else ArmorColors.OnBg, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                r.distanceFromSelfM?.let { fmtDistance(it) },
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
 * Create a ride on the ARMOR server, or join one with its code. The creator is the admin and can start and end the ride
 * for everyone; once in a ride, the group code is shown big for sharing.
 */
@Composable
private fun RideSessionCard(settings: Settings) {
    val prefs = ArmorApp.instance.prefs
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rideName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    val name = settings.riderName.ifBlank { "Rider" }

    fun adopt(session: com.armor.app.net.RideSession?, failure: String) {
        busy = false
        if (session == null) { message = failure; return }
        message = null
        prefs.update { it.copy(riderId = session.riderId, rideId = session.rideId, rideCode = session.code, rideName = session.rideName, isRideAdmin = session.isAdmin) }
    }

    if (settings.backendConfigured) {
        Card2 {
            SectionTitle(if (settings.isRideAdmin) "Your ride · admin" else "Your ride")
            Txt(settings.rideName.ifBlank { "Ride" }, Type.Title, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            Txt(settings.rideCode, Type.Display.copy(fontSize = 44.sp, letterSpacing = 10.sp), ArmorColors.Orange, Modifier.fillMaxWidth(), TextAlign.Center)
            Txt("Share this code with the riders you're joining", Type.Caption, ArmorColors.Muted, Modifier.fillMaxWidth(), TextAlign.Center)
            if (settings.isRideAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch { val ok = ArmorBackendTransport(settings.backendUrl, settings.rideId).startRide(); busy = false; message = if (ok) "Ride started for everyone." else "Couldn't reach the server." }
                    }) { Text("Start ride", color = ArmorColors.Orange) }
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val ok = ArmorBackendTransport(settings.backendUrl, settings.rideId).endRide()
                            busy = false
                            if (ok) prefs.update { it.copy(rideId = "", rideCode = "", rideName = "", isRideAdmin = false) } else message = "Couldn't reach the server."
                        }
                    }) { Text("End ride for all", color = ArmorColors.Red) }
                }
            }
            TextButton(onClick = { prefs.update { it.copy(rideId = "", rideCode = "", rideName = "", isRideAdmin = false) } }) { Text("Leave ride", color = ArmorColors.Muted) }
            message?.let { Txt(it, Type.Caption, ArmorColors.Amber) }
        }
    } else {
        Card2 {
            SectionTitle("Group ride")
            OutlinedTextField(
                value = settings.backendUrl, onValueChange = { v -> prefs.update { it.copy(backendUrl = v.trim()) } },
                label = { Text("ARMOR server URL") }, singleLine = true,
                supportingText = { Text("e.g. https://your-armor-server.example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.phone, onValueChange = { v -> prefs.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' }.take(16)) } },
                label = { Text("Your phone number") }, singleLine = true,
                supportingText = { Text(if (settings.phone.isBlank()) "Shared with the ride, so riders can text and call you if you go down." else "Shared with the riders in this ride.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = rideName, onValueChange = { rideName = it.take(40) }, label = { Text("Ride name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PrimaryButton(if (busy) "Creating…" else "Create ride", color = ArmorColors.Orange, contentColor = androidx.compose.ui.graphics.Color(0xFF1A0B00)) {
                if (!busy && settings.backendUrl.isNotBlank() && rideName.isNotBlank()) {
                    busy = true
                    scope.launch { adopt(ArmorBackendTransport.createRide(settings.backendUrl, rideName.trim(), name, settings.phone), "Couldn't create the ride. Check the server URL.") }
                }
            }
            OutlinedTextField(
                value = joinCode, onValueChange = { v -> joinCode = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                label = { Text("Ride code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(if (busy) "Joining…" else "Join ride") {
                if (!busy && settings.backendUrl.isNotBlank() && joinCode.isNotBlank()) {
                    busy = true
                    scope.launch { adopt(ArmorBackendTransport.joinRide(settings.backendUrl, joinCode, name, settings.phone), "Couldn't join. Check the code and the server URL.") }
                }
            }
            message?.let { Txt(it, Type.Caption, ArmorColors.Amber) }
        }
    }
}
