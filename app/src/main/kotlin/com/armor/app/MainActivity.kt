package com.armor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.armor.app.ui.ArmorRoot
import com.armor.app.ui.ArmorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArmorTheme { ArmorRoot() } }
    }
}
