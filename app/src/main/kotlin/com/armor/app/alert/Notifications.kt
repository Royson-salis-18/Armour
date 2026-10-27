package com.armor.app.alert

import com.armor.app.requireService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.armor.app.MainActivity
import com.armor.app.R
import com.armor.app.service.ActionReceiver

object Notifications {
    const val CHANNEL_RIDE = "ride"
    const val CHANNEL_ALERT = "alert"
    const val ID_RIDE = 1
    const val ID_ALERT = 2
    const val ID_REMINDER = 3

    fun createChannels(context: Context) {
        val nm = context.requireService<NotificationManager>()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RIDE, "Ride monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while ARMOR is watching over your ride."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Safety alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Are-you-OK checks, crash countdowns, SOS and group alerts."
                // Sound and vibration are driven by AlertController so they can loop and be stopped instantly.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun ride(context: Context, text: String): Notification =
        Notification.Builder(context, CHANNEL_RIDE)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("ARMOR is monitoring your ride")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .addAction(action(context, "I'm OK", ActionReceiver.ACTION_OK, 10))
            .addAction(action(context, "End ride", ActionReceiver.ACTION_STOP, 12))
            .build()

    fun alert(context: Context, title: String, text: String, canAnswer: Boolean): Notification =
        Notification.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .setFullScreenIntent(openApp(context), true)
            .apply { if (canAnswer) addAction(action(context, "I'm OK", ActionReceiver.ACTION_OK, 10)) }
            .build()

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(context: Context, label: String, action: String, requestCode: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_shield), label, pi).build()
    }
}
