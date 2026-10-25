package com.armor.app

import android.app.Application
import androidx.room.Room
import com.armor.app.alert.Notifications
import com.armor.app.data.ArmorDatabase
import com.armor.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ArmorApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var db: ArmorDatabase
        private set

    /** Outlives any single ride, so the final audit-trail entries are never lost when the service stops. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        db = Room.databaseBuilder(this, ArmorDatabase::class.java, "armor.db").build()
        Notifications.createChannels(this)
    }

    companion object {
        lateinit var instance: ArmorApp
            private set
    }
}
