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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
        Text("Group", color = ArmorColors.OnBg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (running) Text("Changes apply from your next ride.", color = ArmorColors.Amber, fontSize = 12.sp)

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
