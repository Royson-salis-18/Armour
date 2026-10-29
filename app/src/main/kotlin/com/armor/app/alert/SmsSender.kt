package com.armor.app.alert

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.armor.app.service.ActionReceiver

/**
 * Sends the SOS text over the cellular network, which works with no internet. Delivery reports come back
 * through [ActionReceiver] so a failed send (no signal) can be retried by the service.
 */
class SmsSender(private val context: Context) {
    /** Why the last send could not even be queued, for the SOS status and the event log. */
    var lastError: String? = null
        private set

    /**
     * The SIM to text from. On a dual-SIM phone set to "ask every time" there is no default SMS SIM and the plain
     * SmsManager fails silently, so fall back to the first active SIM. Null on a device with no telephony.
     */
    private val sms: SmsManager?
        get() = try {
            val sub = subscription()
            if (Build.VERSION.SDK_INT >= 31) {
                val base = context.getSystemService(SmsManager::class.java)
                if (base != null && sub != null) base.createForSubscriptionId(sub) else base
            } else {
                @Suppress("DEPRECATION")
                if (sub != null) SmsManager.getSmsManagerForSubscriptionId(sub) else SmsManager.getDefault()
            }
        } catch (e: Exception) {
            lastError = "no SIM available for texting ($e)"
            null
        }

    private fun subscription(): Int? {
        val def = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (def != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return def
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
        } catch (e: SecurityException) {
            null
        }
    }

    private fun refusal(e: Exception): Boolean {
        lastError = when (e) {
            is SecurityException -> "SMS permission is off for ARMOR"
            is IllegalArgumentException -> "the number looks wrong"
            else -> "the phone refused to send ($e)"
        }
        return false
    }

    /**
     * A follow-up text with no delivery tracking (medical info, safe-arrival notes). Kept apart from [send] so its
     * result can never be mistaken for the SOS itself in the retry logic.
     */
    fun sendPlain(number: String, text: String): Boolean {
        val phone = Phones.normalize(context, number)
        val manager = sms ?: return false
        return try {
            manager.sendMultipartTextMessage(phone, null, manager.divideMessage(text), null, null)
            true
        } catch (e: Exception) {
            refusal(e)
        }
    }

    /** Returns false if the request could not even be queued (permission denied, malformed number...). */
    fun send(number: String, text: String, attempt: Int, action: String = ActionReceiver.ACTION_SMS_SENT): Boolean {
        // "98765 43210" or "098765..." fails on some networks; +91XXXXXXXXXX works everywhere. Reports keep the number as
        // stored so the retry logic can match them.
        val phone = Phones.normalize(context, number)
        val manager = sms ?: return false
        return try {
            val parts = manager.divideMessage(text)
            val sent = PendingIntent.getBroadcast(
                context,
                (phone.hashCode() * 31) + attempt,
                Intent(context, ActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(ActionReceiver.EXTRA_PHONE, number)
                    .putExtra(ActionReceiver.EXTRA_ATTEMPT, attempt),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Only the last part carries the report: its result stands for the whole message.
            val intents = ArrayList<PendingIntent?>(parts.map { null }).also { it[it.lastIndex] = sent }
            manager.sendMultipartTextMessage(phone, null, parts, intents, null)
            true
        } catch (e: Exception) {
            refusal(e)
        }
    }

    companion object {
        /** A plain-language reason for a failed send report. */
        fun reason(resultCode: Int): String = when (resultCode) {
            SmsManager.RESULT_ERROR_RADIO_OFF -> "the phone's radio is off (airplane mode?)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "no mobile signal"
            SmsManager.RESULT_ERROR_NULL_PDU -> "the message could not be built"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "the phone's SMS limit was reached"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "the network or SIM refused it (check balance and SMS plan)"
            else -> "error code $resultCode"
        }
    }
}
