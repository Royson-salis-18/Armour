package com.armor.app.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Entry point for notification buttons and SMS delivery reports; both just forward to the running ride. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OK -> ArmorRuntime.send(ArmorRuntime.Command.RiderOk)
            ACTION_STOP -> ArmorRuntime.send(ArmorRuntime.Command.Stop)
            ACTION_SMS_SENT -> ArmorRuntime.send(
                ArmorRuntime.Command.SmsResult(
                    phone = intent.getStringExtra(EXTRA_PHONE).orEmpty(),
                    ok = resultCode == Activity.RESULT_OK,
                    attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1),
                ),
            )
        }
    }

    companion object {
        const val ACTION_OK = "com.armor.app.action.OK"
        const val ACTION_STOP = "com.armor.app.action.STOP"
        const val ACTION_SMS_SENT = "com.armor.app.action.SMS_SENT"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_ATTEMPT = "attempt"
    }
}
