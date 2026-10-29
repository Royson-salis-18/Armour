package com.armor.app.alert

import android.content.Context
import android.telephony.TelephonyManager
import com.armor.core.sos.PhoneNumbers

/** [PhoneNumbers.normalize] for this phone's country (from the SIM, else the network, else India). */
object Phones {
    fun country(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso).firstOrNull { it.length == 2 }?.uppercase() ?: "IN"
    }

    fun normalize(context: Context, raw: String): String = PhoneNumbers.normalize(raw, country(context))
}
