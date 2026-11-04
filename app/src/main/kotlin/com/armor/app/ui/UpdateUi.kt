package com.armor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.armor.app.net.UpdateState
import com.armor.app.net.Updater

/** One line on the home screen when a newer build exists: tap to download and install it over this one. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val state by Updater.state.collectAsState()
    val context = LocalContext.current
    ResumeInstallOnReturn()
    when (val s = state) {
        is UpdateState.Available -> Row(modifier.fillMaxWidth().hexPanel(cut = 12.dp).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Update available", Type.BodyStrong, ArmorColors.Orange)
                Txt("Version ${s.info.versionName}" + if (s.info.notes.isNotBlank()) " \u00b7 ${s.info.notes.take(60)}" else "", Type.Caption, ArmorColors.Muted, maxLines = 2)
            }
            TextButton(onClick = { Updater.install(context, s.info) }) { Text("Update", color = ArmorColors.Orange, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
        is UpdateState.Downloading -> Column(modifier.fillMaxWidth().hexPanel(cut = 12.dp).padding(16.dp)) {
            Txt("Downloading update\u2026 ${s.percent}%", Type.BodyStrong)
        }
        is UpdateState.Ready -> if (s.needsPermission) Column(modifier.fillMaxWidth().hexPanel(cut = 12.dp).padding(16.dp)) {
            Txt("Allow installs from ARMOR, then come back", Type.BodyStrong, ArmorColors.Orange)
            Txt("Turn on \u201cAllow from this source\u201d. The update continues by itself.", Type.Caption, ArmorColors.Muted)
        }
        else -> Unit
    }
}

/** Settings: the installed version and a Check for updates button. */
@Composable
fun UpdateCard() {
    val state by Updater.state.collectAsState()
    val context = LocalContext.current
    ResumeInstallOnReturn()
    Card2 {
        SectionTitle("App updates")
        Txt("Installed: version ${Updater.installedName(context)}", Type.Body, ArmorColors.OnBg)
        when (val s = state) {
            UpdateState.Checking -> Txt("Checking\u2026", Type.Label, ArmorColors.Muted)
            UpdateState.UpToDate -> Txt("You have the latest version.", Type.Label, ArmorColors.Green)
            is UpdateState.Available -> {
                Txt("New version ${s.info.versionName} is ready." + if (s.info.notes.isNotBlank()) "\n${s.info.notes}" else "", Type.Label, ArmorColors.Orange)
                PrimaryButton("Download and install", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00)) { Updater.install(context, s.info) }
            }
            is UpdateState.Downloading -> Txt("Downloading\u2026 ${s.percent}%", Type.Label, ArmorColors.Orange)
            is UpdateState.Ready -> Txt(if (s.needsPermission) "Allow installs from ARMOR in the screen that opened, then come back." else "The installer is open. Tap Install.", Type.Label, ArmorColors.Orange)
            is UpdateState.Failed -> Txt(s.message, Type.Label, ArmorColors.Red)
            UpdateState.Idle -> Unit
        }
        if (state !is UpdateState.Downloading) TextButton(onClick = { Updater.check(context) }) { Text("Check for updates", color = ArmorColors.Orange, fontSize = 14.sp) }
    }
}

/** When the rider comes back from Android's "allow installs" switch, carry on with the update. */
@Composable
private fun ResumeInstallOnReturn() {
    val context = LocalContext.current
    // The activity is the lifecycle owner; there is nothing to observe in previews or tests.
    val owner = context as? androidx.lifecycle.LifecycleOwner ?: return
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) Updater.resumeInstall(context) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
}
