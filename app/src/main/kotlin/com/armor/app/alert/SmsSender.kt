package com.armor.app.alert

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.armor.app.service.ActionReceiver

/**
 * Sends the SOS text over the cellular network, which works with no internet. Delivery reports come back
 * through [ActionReceiver] so a failed send (no signal) can be retried by the service.
 */
class SmsSender(private val context: Context) {
    /** Null on a device with no telephony (some tablets), where texting an SOS is simply impossible. */
    private val sms: SmsManager? =
        if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    /** Returns false if the request could not even be queued (permission denied, malformed number...). */
    fun send(phone: String, text: String, attempt: Int): Boolean {
        val manager = sms ?: return false
        return try {
            val parts = manager.divideMessage(text)
            val sent = PendingIntent.getBroadcast(
                context,
                (phone.hashCode() * 31) + attempt,
                Intent(context, ActionReceiver::class.java)
                    .setAction(ActionReceiver.ACTION_SMS_SENT)
                    .putExtra(ActionReceiver.EXTRA_PHONE, phone)
                    .putExtra(ActionReceiver.EXTRA_ATTEMPT, attempt),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Only the last part carries the report: its result stands for the whole message.
            val intents = ArrayList<PendingIntent?>(parts.map { null }).also { it[it.lastIndex] = sent }
            manager.sendMultipartTextMessage(phone, null, parts, intents, null)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
