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

    /** Rings [number] straight away through the phone's own dialer (or opens it with the number filled in without permission). */
    fun call(context: Context, number: String) {
        val uri = android.net.Uri.parse("tel:" + normalize(context, number))
        val direct = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val intent = android.content.Intent(if (direct) android.content.Intent.ACTION_CALL else android.content.Intent.ACTION_DIAL, uri)
        try { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    /** Opens the phone's Messages app on a conversation with [number], with [body] typed in ready to send. */
    fun openMessages(context: Context, number: String, body: String = "") {
        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + normalize(context, number)))
        if (body.isNotEmpty()) intent.putExtra("sms_body", body)
        try { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
