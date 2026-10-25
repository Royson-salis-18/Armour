package com.armor.app.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.armor.app.ArmorApp
import com.armor.app.service.ArmorRuntime

private enum class Tab(val label: String, val icon: ImageVector) {
    RIDE("Ride", Icons.Filled.Home),
    GROUP("Group", Icons.Filled.Person),
    EVENTS("Events", Icons.AutoMirrored.Filled.List),
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

    Box(Modifier.fillMaxSize().background(ArmorColors.Bg)) {
        Scaffold(
            containerColor = ArmorColors.Bg,
            bottomBar = {
                NavigationBar(containerColor = ArmorColors.Surface) {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ArmorColors.Orange,
                                selectedTextColor = ArmorColors.Orange,
                                indicatorColor = ArmorColors.SurfaceHigh,
                                unselectedIconColor = ArmorColors.Muted,
                                unselectedTextColor = ArmorColors.Muted,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (Tab.entries[tab]) {
                    Tab.RIDE -> RideScreen(settings)
                    Tab.GROUP -> GroupScreen(settings)
                    Tab.EVENTS -> EventsScreen()
                    Tab.SETTINGS -> SettingsScreen(settings)
                }
            }
        }
        if (critical != null) CriticalScreen(critical, settings.emergencyNumber)
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
