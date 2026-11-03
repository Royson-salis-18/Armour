package com.armor.app.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.armor.app.ArmorApp
import com.armor.app.service.ArmorRuntime

private enum class Tab(val label: String, val icon: ImageVector) {
    RIDE("Ride", Icons.Filled.Home),
    GROUP("Group", Icons.Filled.Person),
    EVENTS("Log", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun ArmorRoot() {
    val snapshot by ArmorRuntime.snapshot.collectAsState()
    val running by ArmorRuntime.running.collectAsState()
    val settings by ArmorApp.instance.prefs.settings.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val critical = snapshot?.takeIf { it.needsAttention }
    ScreenBehaviour(showOverLockScreen = critical != null, keepOn = running && settings.keepScreenOn)

    Box(Modifier.fillMaxSize().background(ArmorColors.Bg).warmGlow(strength = 0.16f)) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                FloatingNav(Tab.entries.map { it.label }, Tab.entries.map { it.icon }, tab) { tab = it }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                androidx.compose.animation.Crossfade(tab, animationSpec = androidx.compose.animation.core.tween(180), label = "tab") { t ->
                    when (Tab.entries[t]) {
                        Tab.RIDE -> RideScreen(settings)
                        Tab.GROUP -> Box(Modifier.statusBarsPadding()) { GroupScreen(settings) }
                        Tab.EVENTS -> Box(Modifier.statusBarsPadding()) { EventsScreen() }
                        Tab.SETTINGS -> Box(Modifier.statusBarsPadding()) { SettingsScreen(settings) }
                    }
                }
            }
        }
        if (critical != null) CriticalScreen(critical, settings.emergencyNumber, settings.medical)
    }
}

/** Lets an alert light the screen over the lock screen, and keeps the display awake during a ride if wanted. */
@Composable
private fun ScreenBehaviour(showOverLockScreen: Boolean, keepOn: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    LaunchedEffect(showOverLockScreen) {
        if (Build.VERSION.SDK_INT >= 27) {
            activity.setShowWhenLocked(showOverLockScreen)
            activity.setTurnScreenOn(showOverLockScreen)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (showOverLockScreen) activity.window.addFlags(flags) else activity.window.clearFlags(flags)
        }
    }
    DisposableEffect(keepOn) {
        if (keepOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
