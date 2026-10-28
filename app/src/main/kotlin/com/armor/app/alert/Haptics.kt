package com.armor.app.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.armor.app.ArmorApp

/**
 * The phone's vibration motor as ARMOR's second voice, so a rider with gloves and a noisy helmet still feels what is
 * happening. Short patterns for taps and confirmations, long ones for things that need you to stop.
 * Off when the rider switches haptics off in settings; the crash and SOS alarm in [AlertController] always vibrates.
 */
object Haptics {
    private var vibrator: Vibrator? = null

    private fun motor(): Vibrator? {
        vibrator?.let { return it }
        val ctx: Context = ArmorApp.instance
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        vibrator = v
        return v
    }

    private fun enabled() = ArmorApp.instance.prefs.current.haptics

    private fun play(effect: VibrationEffect) {
        if (!enabled()) return
        motor()?.takeIf { it.hasVibrator() }?.vibrate(effect)
    }

    private fun pattern(vararg ms: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE): VibrationEffect {
        val timings = longArrayOf(0, *ms)
        val amps = IntArray(timings.size) { if (it % 2 == 1) amplitude else 0 }
        return VibrationEffect.createWaveform(timings, amps, -1)
    }

    /** A light tick under a finger: buttons, selections. */
    fun tick() = play(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) else VibrationEffect.createOneShot(10, 60))

    /** A firmer click: a confirmed action. */
    fun click() = play(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) else VibrationEffect.createOneShot(20, 120))

    /** A solid thud: something irreversible just happened, like an SOS being sent. */
    fun heavy() = play(if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK) else VibrationEffect.createOneShot(45, 255))

    /** Done, in a good way: two quick pulses. */
    fun success() = play(pattern(20, 60, 35, amplitude = 160))

    /** Something needs a look soon. */
    fun warning() = play(pattern(120, 90, 120, amplitude = 200))

    /** A new chat message from the group. */
    fun message() = play(pattern(25, 70, 25, amplitude = 140))

    /** A hazard just ahead. */
    fun hazard() = play(pattern(180, 110, 180, amplitude = 220))

    /** The lead has called a team stop, or a rider needs help: long and unmistakable. */
    fun teamStop() = play(pattern(400, 150, 400, 150, 400, amplitude = 255))
}
