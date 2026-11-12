package com.armor.app.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Call
import com.armor.app.alert.SmsSender
import com.armor.app.service.ActionReceiver
import com.armor.app.service.ArmorRuntime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.armor.app.alert.Phones
import com.armor.core.sos.PhoneNumbers
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
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
import com.armor.app.alert.Haptics
import com.armor.app.ArmorApp
import com.armor.app.data.ContactEntity
import com.armor.app.data.Sensitivity
import com.armor.app.data.Settings
import com.armor.app.sensors.BluetoothAudioMonitor
import kotlinx.coroutines.launch
import com.armor.app.service.RideService

@Composable
fun SettingsScreen(settings: Settings) {
    val app = ArmorApp.instance
    val contactsFlow = remember { app.db.contacts().all() }
    val contacts by contactsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val smsTest by ArmorRuntime.smsTest.collectAsState()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var contactNote by remember { mutableStateOf<String?>(null) }

    /** Saves a contact in international form, once per person. */
    fun addContact(contactName: String, number: String) {
        val clean = Phones.normalize(context, number)
        if (!PhoneNumbers.looksValid(clean)) { contactNote = "That number looks too short."; return }
        if (contacts.any { PhoneNumbers.sameNumber(it.phone, clean) }) { contactNote = "$contactName is already on the list."; return }
        scope.launch { app.db.contacts().insert(ContactEntity(name = contactName.trim().ifBlank { "Contact" }.take(30), phone = clean)) }
        contactNote = "Added $contactName ($clean)."
    }

    // Opens the phone's Contacts app; the chosen number comes back with no contacts permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) addContact(c.getString(0) ?: "Contact", c.getString(1) ?: "")
            }
        } catch (e: Exception) {
            contactNote = "Couldn't read that contact. Type the number below instead."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Settings")

        UpdateCard()

        Card2 {
            SectionTitle("How ARMOR works")
            Text("A one-minute walkthrough with an animated demo, handy for showing a friend.", color = ArmorColors.Muted, fontSize = 13.sp)
            SecondaryButton("Replay the walkthrough") { Tour.show.value = true }
        }

        Card2 {
            SectionTitle("Always-on safety")
            var setup by remember { mutableStateOf(false) }
            val (ready, total) = protectionReady(context)
            Text(if (ready == total) "Everything ARMOR needs is allowed." else "$ready of $total permissions ready.", color = if (ready == total) ArmorColors.Green else ArmorColors.Amber, fontSize = 13.sp)
            SecondaryButton(if (ready == total) "Review permissions" else "Set up permissions") { setup = true }
            if (setup) ProtectionSetupDialog { setup = false }
            ToggleRow("Keep my ride protected in the background", "Keeps a ride running with the screen off and picks it up again after a restart. Nothing runs when no ride is on.", settings.alwaysOn) { v ->
                app.prefs.update { it.copy(alwaysOn = v) }
                if (v && ready < total) setup = true
            }
            ToggleRow("Call after an SOS", "Texts everyone, then rings the nearest rider and your emergency contacts one by one until someone answers.", settings.autoCall) { v -> app.prefs.update { it.copy(autoCall = v) } }
            ToggleRow("Keep calling after someone answers", "Ring everyone on the list, not just until the first answer.", settings.callAll) { v -> app.prefs.update { it.copy(callAll = v) } }
            ToggleRow("Also call emergency services", "After your contacts, dial " + settings.emergencyNumber.ifBlank { "112" } + ". Leave off unless you want that: a false alarm ties up the line.", settings.callEmergency) { v -> app.prefs.update { it.copy(callEmergency = v) } }
            ArmorField(
                value = settings.phone, onValueChange = { v -> app.prefs.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' }.take(16)) } },
                label = { Text("Your phone number") }, singleLine = true,
                supportingText = { Text("Shared with the riders in your ride, so they can text and call you if you go down.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
        }

        Card2 {
            SectionTitle("Emergency contacts")
            Text("An SOS is texted to these numbers with your location. SMS works without internet.", color = ArmorColors.Muted, fontSize = 12.sp)
            contacts.forEach { c ->
                ContactRow(
                    c.name, c.phone,
                    onCall = { Phones.call(context, c.phone) },
                    onMessage = { Phones.openMessages(context, c.phone) },
                    onTest = { sendTestText(context, c.phone, settings.riderName) },
                    onRemove = { scope.launch { app.db.contacts().delete(c) } },
                )
            }
            smsTest?.let { Text(it, color = ArmorColors.OnBg, fontSize = 13.sp) }
            PrimaryButton("Pick from contacts", color = ArmorColors.Orange, contentColor = androidx.compose.ui.graphics.Color(0xFF1A0B00)) {
                picker.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
            }
            Text("Or type one in:", color = ArmorColors.Muted, fontSize = 12.sp)
            ArmorField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ArmorField(
                phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(20) },
                label = { Text("Phone number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 5,
                onClick = { addContact(name, phone); name = ""; phone = "" },
            ) { Text("Add contact", color = ArmorColors.Orange) }
            contactNote?.let { Text(it, color = ArmorColors.OnBg, fontSize = 13.sp) }
        }

        Card2 {
            SectionTitle("Voice assistant")
            Text("Tap the orange mic on the map, or press your helmet or headset button, then speak after the short buzz. ARMOR answers out loud (through your helmet when connected) and stops listening. Tap again to stop early.", color = ArmorColors.Muted, fontSize = 12.sp)
            ToggleRow("Also listen for \u201cArmor\u201d (experimental)", "Keeps the microphone listening while the ride screen is open. Android has no true wake word for apps, so some phones click or beep between listens and it may miss words. Off is more reliable.", settings.voiceWake) { v -> app.prefs.update { it.copy(voiceWake = v) } }
            Text("Try: \u201cstop for fuel\u201d \u00b7 \u201cstatus\u201d \u00b7 \u201cread messages\u201d \u00b7 \u201cpothole ahead\u201d \u00b7 \u201ccall Mom\u201d \u00b7 \u201ctext Mom running late\u201d \u00b7 \u201cneed a hand\u201d \u00b7 \u201cSOS\u201d (asks you to say yes)", color = ArmorColors.OnBg, fontSize = 13.sp)
        }

        Card2 {
            SectionTitle("Emergency number")
            ArmorField(
                settings.emergencyNumber,
                { v -> app.prefs.update { it.copy(emergencyNumber = v.filter { ch -> ch.isDigit() }.take(6)) } },
                label = { Text("Number the Call button dials") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("112 works across the EU, India and most GSM networks. Use 911 in the US.", color = ArmorColors.Muted, fontSize = 12.sp)
        }

        Card2 {
            SectionTitle("Medical ID")
            Text("Shown on the SOS screen for whoever reaches you first, and texted to your contacts with an SOS.", color = ArmorColors.Muted, fontSize = 12.sp)
            ArmorField(settings.bloodGroup, { v -> app.prefs.update { it.copy(bloodGroup = v.take(6)) } }, label = { Text("Blood group (e.g. O+)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ArmorField(settings.allergies, { v -> app.prefs.update { it.copy(allergies = v.take(60)) } }, label = { Text("Allergies") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ArmorField(settings.conditions, { v -> app.prefs.update { it.copy(conditions = v.take(60)) } }, label = { Text("Medical conditions") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ArmorField(settings.medications, { v -> app.prefs.update { it.copy(medications = v.take(60)) } }, label = { Text("Medications") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        Card2 {
            SectionTitle("Roadside help")
            ArmorField(
                settings.roadsideNumber,
                { v -> app.prefs.update { it.copy(roadsideNumber = v.filter { ch -> ch.isDigit() || ch == '+' }.take(16)) } },
                label = { Text("Breakdown / roadside assistance number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Your insurer's or bike maker's helpline. Shows as a Roadside help button during rides.", color = ArmorColors.Muted, fontSize = 12.sp)
        }

        Card2 {
            SectionTitle("Fall and crash sensitivity")
            val g = settings.impactG
            val preset = Sensitivity.entries.firstOrNull { kotlin.math.abs(it.impactG - g) < 0.05 }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(java.util.Locale.US, "%.1f g", g), color = ArmorColors.OnBg, style = Type.Numeric.copy(fontSize = 30.sp))
                Text("  " + (preset?.label ?: "Custom"), color = ArmorColors.Orange, style = Type.Label, modifier = Modifier.padding(bottom = 5.dp))
            }
            androidx.compose.material3.Slider(
                value = g.toFloat(),
                onValueChange = { v -> app.prefs.update { it.copy(impactG = (Math.round(v * 2f) / 2.0)) } },
                valueRange = RideService.MIN_IMPACT_G.toFloat()..RideService.MAX_IMPACT_G.toFloat(), steps = 11,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = ArmorColors.Orange, activeTrackColor = ArmorColors.Orange, inactiveTrackColor = ArmorColors.S3),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("More sensitive", color = ArmorColors.Dim, style = Type.Meta)
                Text("Needs a harder hit", color = ArmorColors.Dim, style = Type.Meta)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Sensitivity.entries.forEach { level ->
                    Pill("${level.label} · ${level.impactG.toInt()} g", selected = level == preset) { app.prefs.update { it.copy(impactG = level.impactG) } }
                }
            }
            ImpactMeter(g)
            Text(
                "The impact that starts a crash check. A fall also needs a sudden change in the phone's tilt and the bike to stop before ARMOR asks if you're okay. Lower catches gentler falls but may ask more often; higher means fewer false alarms. Takes effect from your next ride.",
                color = ArmorColors.Muted, fontSize = 12.sp,
            )
        }

        Card2 {
            SectionTitle("Monitoring")
            ToggleRow("Check on me when riding solo", "Ask \"are you OK?\" after a long stop when no group is in range.", settings.soloMonitoring) { v -> app.prefs.update { it.copy(soloMonitoring = v) } }
            ToggleRow("Keep screen on while riding", "Uses more battery.", settings.keepScreenOn) { v -> app.prefs.update { it.copy(keepScreenOn = v) } }
            ToggleRow("Vibration feedback", "A buzz for taps, messages, hazards and team stops.", settings.haptics) { v -> app.prefs.update { it.copy(haptics = v) }; if (v) Haptics.success() }
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

/** Sends a real text to one contact so the rider can check SOS texting works on this phone and SIM before a ride. */
private fun sendTestText(context: android.content.Context, phone: String, name: String) {
    if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        ArmorRuntime.smsTest.value = "ARMOR isn't allowed to send SMS. Open Always-on safety setup above and allow SMS."
        return
    }
    ArmorRuntime.smsTest.value = "Sending a test text to $phone\u2026"
    val sender = SmsSender(context)
    val text = "ARMOR test: ${name.ifBlank { "A rider" }} added you as an emergency contact. If they ever need help, you'll get a text like this with their location. No action needed."
    if (!sender.send(phone, text, attempt = 1, action = ActionReceiver.ACTION_SMS_TEST)) {
        ArmorRuntime.smsTest.value = "Couldn't send the test text: ${sender.lastError ?: "unknown reason"}."
    }
}

/** One emergency contact: initial, name and number, with round Call and Message buttons and quieter test/remove links. */
@Composable
private fun ContactRow(name: String, phone: String, onCall: () -> Unit, onMessage: () -> Unit, onTest: () -> Unit, onRemove: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(ArmorColors.S2).border(1.dp, ArmorColors.Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(name.firstOrNull()?.uppercase() ?: "?", ArmorColors.Orange, 42.dp, textColor = androidx.compose.ui.graphics.Color(0xFF1A0B00))
            Column(Modifier.weight(1f)) {
                Text(name, color = ArmorColors.OnBg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                Text(phone, color = ArmorColors.Muted, fontSize = 14.sp, maxLines = 1)
            }
            RoundAction(androidx.compose.material.icons.Icons.Filled.Call, "Call $name", onCall)
            RoundAction(androidx.compose.material.icons.Icons.Filled.Email, "Message $name", onMessage)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onTest) { Text("Send test SOS text", color = ArmorColors.Orange, fontSize = 13.sp) }
            Box(Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text("Remove", color = ArmorColors.Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ArmorColors.Orange.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(icon, contentDescription = label, tint = ArmorColors.Orange, modifier = Modifier.size(20.dp)) }
}


/**
 * A live readout for tuning: shake or tap the phone and it shows the hardest hit in the last few seconds against the
 * threshold, so the rider can see what their setting would and would not catch. Only listens while it is on screen.
 */
@Composable
private fun ImpactMeter(thresholdG: Double) {
    val context = LocalContext.current
    var peak by remember { mutableStateOf(0.0) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val accel = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var lastPeakAt = 0L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val gMag = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / 9.81
                val now = android.os.SystemClock.elapsedRealtime()
                if (gMag >= peak || now - lastPeakAt > 4_000) { peak = gMag; lastPeakAt = now }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        if (accel != null) sm.registerListener(listener, accel, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    val over = peak >= thresholdG
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TEST: SHAKE OR TAP THE PHONE", color = ArmorColors.Dim, style = Type.Eyebrow, modifier = Modifier.weight(1f))
            Text(String.format(java.util.Locale.US, "%.1f g", peak), color = if (over) ArmorColors.Orange else ArmorColors.OnBg, style = Type.Numeric.copy(fontSize = 16.sp))
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val max = RideService.MAX_IMPACT_G.toFloat() + 2f
            drawRoundRect(ArmorColors.S3, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            drawRoundRect(if (over) ArmorColors.Orange else Color(0xFF8B8E97), size = androidx.compose.ui.geometry.Size(size.width * (peak.toFloat() / max).coerceIn(0.02f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            val x = size.width * (thresholdG.toFloat() / max)
            drawLine(Color.White, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 2.dp.toPx())
        }
        Text(if (over) "That hit is above your setting: a crash check would start if the phone also tilted and the bike stopped." else "Below your setting: ignored.", color = if (over) ArmorColors.Orange else ArmorColors.Muted, style = Type.Caption)
    }
}
