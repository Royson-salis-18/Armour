package com.armor.app.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/**
 * After an SOS text, rings the emergency contacts one at a time, with no screen to tap and no permission prompt
 * (CALL_PHONE is granted once, during setup). It moves to the next person when a call is not picked up.
 *
 * Android does not say whether a call was answered, so a call that lasted [ANSWERED_MS] or longer counts as answered and
 * ends the round; a shorter one (rejected, busy, no signal) moves on to the next number. A voicemail greeting that runs
 * past that time looks like an answer, which is why the SOS text always goes out first.
 */
class SosCaller(private val context: Context, private val handler: Handler, private val report: (String) -> Unit) {
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    private val queue = ArrayDeque<String>()
    private var active = false
    private var stopOnAnswer = true
    private var offHook = false
    private var offHookAt = 0L
    private var legacy: PhoneStateListener? = null
    private var modern: TelephonyCallback? = null

    val calling: Boolean get() = active

    fun canCall() = telecom != null && context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun start(numbers: List<String>, stopOnAnswer: Boolean = true) {
        if (active || !canCall()) return
        this.stopOnAnswer = stopOnAnswer
        val list = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (list.isEmpty()) return
        queue.clear(); queue.addAll(list)
        active = true
        listen()
        next()
    }

    fun stop() {
        if (!active) return
        active = false
        queue.clear()
        handler.removeCallbacks(noStart)
        unlisten()
    }

    private fun next() {
        val number = queue.removeFirstOrNull()
        if (number == null) { report("No one picked up. Call emergency services now."); stop(); return }
        offHook = false
        try {
            telecom!!.placeCall(Uri.fromParts("tel", number, null), Bundle())
            report("Calling $number")
            // If the call never starts (no signal), move on rather than wait.
            handler.postDelayed(noStart, START_TIMEOUT_MS)
        } catch (e: SecurityException) {
            report("The phone refused to place the call")
            stop()
        }
    }

    private val noStart = Runnable {
        if (active && !offHook) { report("The call did not start, trying the next number"); next() }
    }

    private fun onCallState(state: Int) {
        handler.post {
            if (!active) return@post
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> if (!offHook) {
                    offHook = true
                    offHookAt = SystemClock.elapsedRealtime()
                    handler.removeCallbacks(noStart)
                }
                TelephonyManager.CALL_STATE_IDLE -> if (offHook) {
                    offHook = false
                    if (SystemClock.elapsedRealtime() - offHookAt >= ANSWERED_MS) {
                        report("Someone answered the call")
                        if (stopOnAnswer) stop() else handler.postDelayed({ if (active) next() }, NEXT_DELAY_MS)
                    } else {
                        report("No answer, trying the next contact")
                        handler.postDelayed({ if (active) next() }, NEXT_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun listen() {
        val tm = telephony ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                }
                tm.registerTelephonyCallback(context.mainExecutor, cb)
                modern = cb
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                legacy = l
            }
        } catch (e: SecurityException) {
            // Without READ_PHONE_STATE we cannot tell how a call ended: the first call is made and the round stops there.
            report("Calling the first contact only (phone state permission missing)")
            queue.clear()
        }
    }

    private fun unlisten() {
        val tm = telephony ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) modern?.let { tm.unregisterTelephonyCallback(it) }
            else @Suppress("DEPRECATION") legacy?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (e: RuntimeException) {
            // Already unregistered.
        }
        modern = null
        legacy = null
    }

    companion object {
        private const val START_TIMEOUT_MS = 15_000L
        private const val ANSWERED_MS = 35_000L
        private const val NEXT_DELAY_MS = 1_500L
    }
}
