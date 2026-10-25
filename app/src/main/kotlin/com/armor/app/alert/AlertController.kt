package com.armor.app.alert

import com.armor.app.requireService
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.armor.core.engine.RideSnapshot
import com.armor.core.safety.SafetyState

/**
 * Turns the current safety state into something a helmeted rider can notice: beeps and short buzzes for a check,
 * a looping alarm at full volume for a crash countdown / group alert / SOS. State-driven, so it can never be
 * left ringing after the state has moved on. Call from the service handler's thread only.
 */
class AlertController(private val context: Context, private val handler: Handler) {
    enum class Level { NONE, PROMPT, INCOMING, URGENT }

    var level = Level.NONE
        private set

    private val audio = context.requireService<AudioManager>()
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.requireService<VibratorManager>().defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var savedAlarmVolume: Int? = null
    private val beep = object : Runnable {
        override fun run() {
            if (level == Level.PROMPT || level == Level.INCOMING) {
                playBeep()
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                handler.postDelayed(this, if (level == Level.PROMPT) 2_000 else 3_000)
            }
        }
    }

    fun levelFor(s: RideSnapshot?): Level {
        if (s == null) return Level.NONE
        return when (s.safety) {
            is SafetyState.CrashCountdown, is SafetyState.GroupAlerted, is SafetyState.Sos -> Level.URGENT
            is SafetyState.Verifying -> Level.PROMPT
            else -> if (s.incomingAlerts.any { !it.acknowledgedByMe && it.alert.ackedById == null }) Level.INCOMING else Level.NONE
        }
    }

    fun update(s: RideSnapshot?) = setLevel(levelFor(s))

    fun stop() = setLevel(Level.NONE)

    private fun setLevel(next: Level) {
        if (next == level) return
        val previous = level
        level = next
        handler.removeCallbacks(beep)
        if (previous == Level.URGENT) stopAlarm()
        if (previous != Level.NONE && next == Level.NONE) vibrator.cancel()

        when (next) {
            Level.NONE -> Unit
            Level.PROMPT, Level.INCOMING -> handler.post(beep)
            Level.URGENT -> startAlarm()
        }
    }

    private fun startAlarm() {
        savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0))
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // No usable alarm tone on this device: fall back to repeating synthesized beeps.
            player?.release()
            player = null
            handler.post(object : Runnable {
                override fun run() {
                    if (level == Level.URGENT) {
                        playBeep()
                        handler.postDelayed(this, 700)
                    }
                }
            })
        }
    }

    private fun stopAlarm() {
        player?.let {
            try { it.stop() } catch (e: IllegalStateException) { /* never started */ }
            it.release()
        }
        player = null
        savedAlarmVolume?.let { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        savedAlarmVolume = null
        vibrator.cancel()
    }

    private fun playBeep() {
        try {
            val t = tone ?: ToneGenerator(AudioManager.STREAM_ALARM, 90).also { tone = it }
            t.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        } catch (e: RuntimeException) {
            // Tone hardware unavailable: vibration and the on-screen alert still work.
        }
    }

    /** Releases native resources; the controller is unusable afterwards. */
    fun release() {
        stop()
        tone?.release()
        tone = null
    }
}
