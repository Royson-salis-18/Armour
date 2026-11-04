package com.armor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.armor.app.ui.ArmorRoot
import com.armor.app.ui.ArmorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A quiet check for a newer build; a banner appears on the home screen if there is one.
        com.armor.app.net.Updater.check(this, quiet = true)
        setContent { ArmorTheme { ArmorRoot() } }
    }
}
