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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.ArmorApp
import com.armor.app.data.ContactEntity
import com.armor.app.data.Sensitivity
import com.armor.app.data.Settings
import com.armor.app.sensors.BluetoothAudioMonitor
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings) {
    val app = ArmorApp.instance
    val contactsFlow = remember { app.db.contacts().all() }
    val contacts by contactsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Settings")

        Card2 {
            SectionTitle("Emergency contacts")
            Text("An SOS is texted to these numbers with your location. SMS works without internet.", color = ArmorColors.Muted, fontSize = 12.sp)
            contacts.forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(c.name, color = ArmorColors.OnBg, fontWeight = FontWeight.Medium)
                        Text(c.phone, color = ArmorColors.Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { scope.launch { app.db.contacts().delete(c) } }) { Text("Remove", color = ArmorColors.Red) }
                }
            }
            OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) },
                label = { Text("Phone number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 5,
                onClick = {
                    scope.launch { app.db.contacts().insert(ContactEntity(name = name.trim(), phone = phone)) }
                    name = ""; phone = ""
                },
            ) { Text("Add contact", color = ArmorColors.Orange) }
        }

        Card2 {
            SectionTitle("Emergency number")
            OutlinedTextField(
                settings.emergencyNumber,
                { v -> app.prefs.update { it.copy(emergencyNumber = v.filter { ch -> ch.isDigit() }.take(6)) } },
                label = { Text("Number the Call button dials") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("112 works across the EU, India and most GSM networks. Use 911 in the US.", color = ArmorColors.Muted, fontSize = 12.sp)
        }

        Card2 {
            SectionTitle("Crash sensitivity")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sensitivity.entries.forEach { level ->
                    Pill("${level.label} · ${level.impactG.toInt()} g", selected = level == settings.sensitivity) {
                        app.prefs.update { it.copy(sensitivity = level) }
                    }
                }
            }
            Text(
                "The impact needed to start crash detection. Higher means fewer false alarms but a harder crash is needed. Default thresholds are untuned: test them on your own bike.",
                color = ArmorColors.Muted, fontSize = 12.sp,
            )
        }

        Card2 {
            SectionTitle("Monitoring")
            ToggleRow("Check on me when riding solo", "Ask \"are you OK?\" after a long stop when no group is in range.", settings.soloMonitoring) { v -> app.prefs.update { it.copy(soloMonitoring = v) } }
            ToggleRow("Keep screen on while riding", "Uses more battery.", settings.keepScreenOn) { v -> app.prefs.update { it.copy(keepScreenOn = v) } }
        }

        Card2 {
            SectionTitle("Voice & helmet intercom")
            ToggleRow("I use a helmet intercom", "Offline, voice runs on the intercom. ARMOR keeps the safety features running either way.", settings.usesIntercom) { v -> app.prefs.update { it.copy(usesIntercom = v) } }
            val devices = remember { BluetoothAudioMonitor.connectedNames(context) }
            Text(
                if (devices.isEmpty()) "No Bluetooth audio device connected." else "Connected: ${devices.joinToString()}",
                color = ArmorColors.Muted, fontSize = 12.sp,
            )
        }

        Text(
            "ARMOR's emergency decisions are made by fixed rules and timers, never by a learned model. Everything it does is listed in the Events tab.",
            color = ArmorColors.Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = ArmorColors.OnBg, fontWeight = FontWeight.Medium)
            Text(detail, color = ArmorColors.Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
