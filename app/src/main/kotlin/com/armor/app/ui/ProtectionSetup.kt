package com.armor.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** One thing ARMOR needs from Android to protect a rider with the screen off and no data. */
internal class Check(val key: String, val title: String, val why: String, val ok: Boolean)

/**
 * Everything the always-on protection needs. The ordinary permissions are asked together, once; the two Android keeps
 * behind its own settings pages (background location, battery) are walked through by [ProtectionSetupDialog].
 */
internal fun protectionChecks(context: Context): List<Check> {
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(PowerManager::class.java)
    val notifications = context.getSystemService(NotificationManager::class.java)
    return listOf(
        Check("loc", "Location while riding", "Shares your position and watches for a crash.", granted(Manifest.permission.ACCESS_FINE_LOCATION)),
        Check("bg", "Location all the time", "Keeps protecting you with the screen off and after a restart. Choose \"Allow all the time\".",
            Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
        Check("sms", "Send SOS texts", "Texts your location to your contacts and riders with no internet.", granted(Manifest.permission.SEND_SMS)),
        Check("call", "Make calls", "Rings your contacts and riders after an SOS without you touching the phone.",
            granted(Manifest.permission.CALL_PHONE) && granted(Manifest.permission.READ_PHONE_STATE)),
        Check("notif", "Alerts", "Shows the ride and crash alerts.", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)),
        Check("mic", "Voice commands", "Say \"Armor\" for hands-free commands.", granted(Manifest.permission.RECORD_AUDIO)),
        Check("battery", "Run without limits", "Stops the phone putting ARMOR to sleep in the background.", power?.isIgnoringBatteryOptimizations(context.packageName) == true),
        Check("fsi", "Full-screen alarms", "Lets a crash alarm take over a locked screen.", Build.VERSION.SDK_INT < 34 || notifications?.canUseFullScreenIntent() == true),
    )
}

/** (ready, total) for showing "5 of 8 ready". */
internal fun protectionReady(context: Context): Pair<Int, Int> = protectionChecks(context).let { c -> c.count { it.ok } to c.size }

/** A one-tap card for the start screen while setup is unfinished. */
@Composable
internal fun SetupCard(ready: Int, total: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ArmorColors.S1).border(1.dp, ArmorColors.Orange.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Txt("Finish setting up", Type.Title)
            Txt("$ready of $total ready. One tap and ARMOR can text and call for you with the screen off.", Type.Caption, ArmorColors.Muted)
        }
        Txt("Set up", Type.Label, ArmorColors.Orange)
    }
}

/**
 * The one-time setup: asks for everything in order so nothing is asked again mid-ride, and shows what is still missing.
 * Rows that Android only lets the rider change in its own settings open the right page.
 */
@Composable
internal fun ProtectionSetupDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    val attempted = remember { mutableSetOf<String>() }

    // Android tells us nothing when the rider comes back from a settings page, so look again every moment or so.
    LaunchedEffect(Unit) { while (true) { delay(700); tick++ } }

    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun uri() = Uri.parse("package:${context.packageName}")
    fun openAppSettings() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    var next: () -> Unit = {}
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++; if (auto) next() }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; if (auto) next() }

    fun launch(key: String) {
        attempted += key
        when (key) {
            "loc", "sms", "call", "notif", "mic" -> {
                attempted += listOf("loc", "sms", "call", "notif", "mic")
                val list = buildList {
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    if (!granted(Manifest.permission.SEND_SMS)) add(Manifest.permission.SEND_SMS)
                    if (!granted(Manifest.permission.CALL_PHONE)) add(Manifest.permission.CALL_PHONE)
                    if (!granted(Manifest.permission.READ_PHONE_STATE)) add(Manifest.permission.READ_PHONE_STATE)
                    if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
                    if (!granted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
                }
                if (list.isNotEmpty()) multi.launch(list.toTypedArray()) else { tick++; if (auto) next() }
            }
            "bg" -> if (Build.VERSION.SDK_INT >= 29) single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            "battery" -> context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "fsi" -> if (Build.VERSION.SDK_INT >= 34) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    next = {
        val c = protectionChecks(context).firstOrNull { !it.ok && it.key !in attempted }
        if (c == null) auto = false else launch(c.key)
    }

    @Suppress("UNUSED_EXPRESSION") tick
    val checks = protectionChecks(context)
    val ready = checks.count { it.ok }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(ArmorColors.Bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt("Always-on safety", Type.Title)
            Txt(
                "Set this up once. Afterwards ARMOR texts and calls for you, with no prompts, even with the screen off, offline or after a restart.",
                Type.Body, ArmorColors.Muted,
            )
            checks.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ArmorColors.S1).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (c.ok) ArmorColors.Green else ArmorColors.S3),
                        contentAlignment = Alignment.Center,
                    ) { if (c.ok) Txt("✓", Type.Label, ArmorColors.Bg) }
                    Column(Modifier.weight(1f)) {
                        Txt(c.title, Type.Body)
                        Txt(c.why, Type.Caption, ArmorColors.Muted)
                    }
                    if (c.ok) Txt("Ready", Type.Label, ArmorColors.Green)
                    else {
                        // After one ask, Android may refuse to show the prompt again, so the button turns into "Settings".
                        val retry = c.key in attempted && c.key !in setOf("bg", "battery", "fsi")
                        Txt(
                            if (retry) "Settings" else "Allow", Type.Label, ArmorColors.Orange,
                            Modifier.clip(RoundedCornerShape(50)).clickable { if (retry) openAppSettings() else launch(c.key) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Txt(
                "On Xiaomi, Oppo, Vivo, OnePlus and Samsung phones also allow \"Autostart\" or \"Never sleeping apps\" for ARMOR in the phone's own battery settings, or the maker's power saver may still stop it.",
                Type.Caption, ArmorColors.Dim,
            )
            PrimaryButton(if (ready == checks.size) "All set" else "Set everything up", color = ArmorColors.Orange, contentColor = androidx.compose.ui.graphics.Color(0xFF1A0B00)) {
                if (ready == checks.size) onClose() else { attempted.clear(); auto = true; next() }
            }
            SecondaryButton("Close", onClick = onClose)
        }
    }
}
