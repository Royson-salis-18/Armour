package com.armor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.armor.app.service.RideService
import com.armor.app.ui.ArmorRoot
import com.armor.app.ui.ArmorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "Keep protecting me in the background": opening the app also (re)starts protection if it is not running.
        val located = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ArmorApp.instance.prefs.current.alwaysOn && located) RideService.start(this, demo = false)
        setContent { ArmorTheme { ArmorRoot() } }
    }
}
