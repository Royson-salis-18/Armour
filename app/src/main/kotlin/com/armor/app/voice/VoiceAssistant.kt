package com.armor.app.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.armor.app.alert.Haptics
import com.armor.app.alert.SmsSender
import com.armor.app.data.ContactEntity
import com.armor.app.service.ArmorRuntime
import com.armor.app.ui.fmtDistance
import com.armor.core.engine.RideSnapshot
import com.armor.core.voice.VoiceCommands
import com.armor.core.voice.VoiceIntent
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "Armor, ..." hands-free assistant for the ride screen. The phone's own speech recogniser turns speech into text,
 * [VoiceCommands] turns text into an action, and every action is answered out loud (through the helmet intercom when
 * one is connected), so the rider never needs to look at the screen.
 *
 * Two ways in: tap the mic and speak, or, with wake mode on, just say "Armor" followed by the command. An SOS by voice
 * is always confirmed with a spoken "yes" first.
 */
class VoiceAssistant(private val context: Context) {
    /** What the assistant is doing or last said, for the caption on screen. */
    val caption = MutableStateFlow<String?>(null)
    val listening = MutableStateFlow(false)
    /** Microphone loudness while listening, for the animated ring on the mic button. */
    val level = MutableStateFlow(0f)
    private var wakeFailures = 0

    /** Names and numbers that "call ..." and "text ..." can reach: emergency contacts, then the riders in the ride. */
    var contacts: List<ContactEntity> = emptyList()

    private enum class Await { WAKE, COMMAND, CONFIRM_SOS }

    private val main = Handler(Looper.getMainLooper())
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    private var ttsReady = false
    private var speaking = false
    private var awaiting: Await? = null
    private var afterSpeech: Await? = null
    private var wakeMode = false
    private var released = false

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts.language = if (tts.isLanguageAvailable(Locale("en", "IN")) >= TextToSpeech.LANG_AVAILABLE) Locale("en", "IN") else Locale.ENGLISH
        }
    }

    val available: Boolean get() = recognizer != null

    /**
     * A helmet intercom or headset button (play/pause) starts listening, so the rider never touches the phone. Android
     * sends media buttons to the most recent media session, which is this one while the ride screen is open.
     */
    private val buttons = android.media.session.MediaSession(context, "ARMOR assistant").apply {
        setCallback(object : android.media.session.MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val key = mediaButtonIntent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (key.action == android.view.KeyEvent.ACTION_UP && key.keyCode in setOf(
                        android.view.KeyEvent.KEYCODE_HEADSETHOOK, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                    )) main.post { listenForCommand() }
                return true
            }
        })
        setPlaybackState(
            android.media.session.PlaybackState.Builder()
                .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_PLAY or android.media.session.PlaybackState.ACTION_PAUSE)
                .setState(android.media.session.PlaybackState.STATE_PAUSED, 0, 1f).build(),
        )
        isActive = true
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = main.post { speaking = false; resumeAfterSpeech() }.let {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = main.post { speaking = false; resumeAfterSpeech() }.let {}
        })
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening.value = false
                heard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() })
            }
            override fun onError(error: Int) {
                listening.value = false
                val silent = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                when (awaiting) {
                    // Hands-free mode: nothing said is normal. Wait a moment before listening again so the phone isn't
                    // clicking on and off; after repeated trouble, stop and leave it to the mic button.
                    Await.WAKE, null -> {
                        awaiting = null
                        wakeFailures = if (silent) 0 else wakeFailures + 1
                        if (wakeMode && wakeFailures < 3) main.postDelayed({ if (wakeMode && awaiting == null && !speaking) listen(Await.WAKE) }, 1500)
                        else if (wakeMode) caption.value = "Tap the mic to talk"
                    }
                    Await.CONFIRM_SOS -> { awaiting = null; say("No answer, so no SOS was sent. Hold the red button if you need help.") }
                    Await.COMMAND -> { awaiting = null; say(if (silent) "I didn't hear anything. Tap the mic and speak after the buzz." else "Sorry, the microphone had a problem. Try again.") }
                }
            }
            override fun onReadyForSpeech(params: Bundle?) {
                if (awaiting != Await.WAKE) { Haptics.tick(); caption.value = if (awaiting == Await.CONFIRM_SOS) "Say yes or no" else "Speak now…" }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level.value = rmsdB }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if (awaiting != Await.WAKE) caption.value = "Got it…" }
            override fun onPartialResults(partialResults: Bundle?) {
                if (awaiting == Await.WAKE) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { caption.value = "“$it”" }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    /** Wake mode: keep listening for "Armor" while the ride screen is open. */
    fun setWakeMode(on: Boolean) {
        if (wakeMode == on) return
        wakeMode = on
        if (on) {
            if (!speaking && awaiting == null) listen(Await.WAKE)
        } else if (awaiting == Await.WAKE) {
            recognizer?.cancel(); awaiting = null; listening.value = false
        }
    }

    /** Back on the ride screen (after a call, say): pick up listening for the wake word again. */
    fun resume() {
        val inCall = context.getSystemService(android.media.AudioManager::class.java)?.mode != android.media.AudioManager.MODE_NORMAL
        if (wakeMode && !speaking && awaiting == null && !listening.value && !inCall) listen(Await.WAKE)
    }

    /** The mic button (or a helmet/headset button): listen for one command; pressed again while listening, stop. */
    fun listenForCommand() {
        if (recognizer == null) { caption.value = "Speech recognition isn't available on this phone."; return }
        if (listening.value && awaiting != Await.WAKE) { recognizer.cancel(); listening.value = false; awaiting = null; caption.value = "Stopped. Tap the mic to talk."; return }
        recognizer.cancel()
        tts.stop(); speaking = false
        Haptics.click()
        listen(Await.COMMAND)
    }

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts.stop(); tts.shutdown()
        try { buttons.isActive = false; buttons.release() } catch (_: Exception) {}
    }

    private fun listen(what: Await) {
        if (released || recognizer == null) return
        awaiting = what
        if (what != Await.WAKE) caption.value = if (what == Await.CONFIRM_SOS) "Say yes or no" else "Listening…"
        else if (caption.value == null) caption.value = "Say \"Armor\" and a command"
        // Online recognition is far more accurate; offline only when there is no connection.
        val online = context.getSystemService(android.net.ConnectivityManager::class.java)?.activeNetwork != null
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            // Give riders time: wind noise and helmets make people pause mid-sentence.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        listening.value = true
        try { recognizer.startListening(i) } catch (e: Exception) { listening.value = false }
    }

    /** [heard] holds the recogniser's guesses, best first; the first one that makes sense wins. */
    private fun heard(heard: List<String>) {
        val stage = awaiting
        awaiting = null
        if (heard.isEmpty()) { if (stage == Await.WAKE && wakeMode) listen(Await.WAKE); return }
        when (stage) {
            Await.CONFIRM_SOS -> when (heard.firstNotNullOfOrNull { VoiceCommands.yesNo(it) }) {
                true -> { ArmorRuntime.send(ArmorRuntime.Command.ManualSos); Haptics.heavy(); say("SOS sent. Your contacts and the group are being told.", then = null) }
                false -> say("Okay, no SOS.")
                null -> say("Say yes to send an SOS, or no.", then = Await.CONFIRM_SOS)
            }
            Await.WAKE, null -> {
                val rest = heard.firstNotNullOfOrNull { VoiceCommands.afterWakeWord(it) } ?: run { if (wakeMode) listen(Await.WAKE); return }
                wakeFailures = 0
                Haptics.tick()
                if (rest.isEmpty()) say("Yes?", then = Await.COMMAND) else run(listOf(rest))
            }
            Await.COMMAND -> run(heard.map { VoiceCommands.afterWakeWord(it)?.takeIf { r -> r.isNotEmpty() } ?: it })
        }
    }

    private fun run(guesses: List<String>) {
        val s = ArmorRuntime.snapshot.value
        val names = contacts.map { it.name }
        // Try each of the recogniser's guesses ("stop for fuel" may come back as "stop for few" first).
        val (text, parsed) = guesses.firstNotNullOfOrNull { g -> VoiceCommands.parse(g, s?.isLead == true, names)?.let { g to it } } ?: (guesses.first() to null)
        caption.value = "“$text”"
        when (val intent = parsed) {
            null -> say("I heard: $text. I can't do that yet. Try stop for fuel, status, read messages, call someone, or pothole ahead.")
            VoiceIntent.SosRequest -> say("Send an SOS to everyone? Say yes or no.", then = Await.CONFIRM_SOS)
            VoiceIntent.ImOkay -> { ArmorRuntime.send(ArmorRuntime.Command.RiderOk); say("Okay, glad you're fine.") }
            VoiceIntent.NeedHand -> { ArmorRuntime.send(ArmorRuntime.Command.RequestHelp); say("Asked the group for a hand.") }
            VoiceIntent.ResumeTeam -> { ArmorRuntime.send(ArmorRuntime.Command.ResumeTeam); say("Telling everyone to ride on.") }
            is VoiceIntent.TeamStop -> { ArmorRuntime.send(ArmorRuntime.Command.CallTeamStop(intent.reason)); say("Team stop for ${intent.reason.label.lowercase()}. Everyone is being told.") }
            is VoiceIntent.StopSelf -> { ArmorRuntime.send(ArmorRuntime.Command.SetStatus(intent.status)); say("Marked you as stopped: ${intent.status.label.lowercase()}.") }
            is VoiceIntent.Say -> { ArmorRuntime.send(ArmorRuntime.Command.SendMessage(intent.text)); say("Sent to the group.") }
            is VoiceIntent.Hazard -> { ArmorRuntime.send(ArmorRuntime.Command.ReportHazard(intent.type)); say("${intent.type.label} marked for the riders behind.") }
            VoiceIntent.Status -> say(status(s))
            VoiceIntent.ReadMessages -> say(messages(s))
            is VoiceIntent.CallContact -> {
                val c = contacts.first { it.name == intent.name }
                say("Calling ${c.name}.", then = null)
                main.postDelayed({
                    try { context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + c.phone)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { say("I couldn't place the call. Allow phone calls for ARMOR in settings.") }
                }, 1200)
            }
            is VoiceIntent.TextContact -> {
                val c = contacts.first { it.name == intent.name }
                val ok = SmsSender(context).sendPlain(c.phone, intent.text)
                say(if (ok) "Texted ${c.name}: ${intent.text}" else "I couldn't send that text. Allow SMS for ARMOR in settings.")
            }
        }
    }

    private fun status(s: RideSnapshot?): String {
        if (s == null) return "No ride is running."
        val kmh = ((s.self?.speedMps ?: 0.0) * 3.6).roundToInt()
        val others = s.riders.filter { !it.isSelf }
        val group = when {
            others.isEmpty() -> "You're riding solo."
            else -> {
                val far = others.maxByOrNull { it.distanceFromSelfM ?: 0.0 }
                "${others.size} other rider${if (others.size == 1) "" else "s"}; the furthest is ${far?.name} at ${fmtDistance(far?.distanceFromSelfM ?: 0.0)}."
            }
        }
        val trouble = s.incomingAlerts.map { it.alert }.filter { !it.resolved && it.riderId != s.self?.id && !it.kind.informational }.joinToString(" ") { "${it.riderName} needs attention." }
        val unread = s.messages.count { it.riderId != s.self?.id }
        return listOf("You're doing $kmh kilometres an hour.", group, trouble, if (unread > 0) "There are group messages; say read messages." else "").filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun messages(s: RideSnapshot?): String {
        val last = s?.messages?.filter { it.riderId != s.self?.id }?.takeLast(3).orEmpty()
        if (last.isEmpty()) return "No new messages."
        return last.joinToString(" ") { "${it.riderName} says: ${it.text}." }
    }

    private fun say(text: String, then: Await? = Await.WAKE) {
        caption.value = text
        afterSpeech = then
        if (!ttsReady) { resumeAfterSpeech(); return }
        speaking = true
        recognizer?.cancel(); listening.value = false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "armor-" + System.nanoTime())
    }

    private fun resumeAfterSpeech() {
        if (released) return
        when (val next = afterSpeech.also { afterSpeech = null }) {
            Await.COMMAND, Await.CONFIRM_SOS -> listen(next)
            Await.WAKE -> if (wakeMode) listen(Await.WAKE)
            null -> Unit
        }
    }
}
