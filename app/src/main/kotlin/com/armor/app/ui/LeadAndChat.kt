package com.armor.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.service.ArmorRuntime
import com.armor.core.engine.RideSnapshot
import com.armor.core.model.StopReason
import com.armor.core.voice.VoiceCommands
import com.armor.core.voice.VoiceIntent

private enum class StopMode { TEAM, SOLO }

/**
 * The lead's controls, and everyone's view of a group stop.
 *
 * - The lead chooses between **Team stop** (everyone is told to stop and must confirm) and **Just me** (the group keeps
 *   riding and is told why the lead is stopping).
 * - While a team stop is active everyone sees who has stopped; the lead can release the group with **Ride on**.
 * - A rider who hasn't confirmed can do so here as well as from the full-screen alert.
 */
@Composable
fun LeadCard(s: RideSnapshot) {
    var mode by remember { mutableStateOf<StopMode?>(null) }
    val ts = s.teamStop
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(CARD_FILL).border(1.dp, CARD_EDGE, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.clip(CircleShape).background(if (s.isLead) ArmorColors.Orange else ArmorColors.S3).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Txt(if (s.isLead) "LEAD" else "RIDER", Type.Eyebrow.copy(fontSize = 12.sp), if (s.isLead) Color(0xFF1A0B00) else ArmorColors.Muted)
            }
            val leadName = s.riders.firstOrNull { it.id == s.leadId }?.name
            if (!s.isLead && leadName != null) Txt("$leadName leads", Type.Label, ArmorColors.Muted)
        }

        when {
            ts != null -> {
                Txt("TEAM STOP · ${ts.reason.label.uppercase()}", Type.Title, ArmorColors.Orange)
                Txt("${ts.calledByName} called it · ${ts.stopped.size} stopped, ${ts.waiting.size} still moving", Type.Label, ArmorColors.Muted)
                ts.waiting.takeIf { it.isNotEmpty() }?.let { Txt("Waiting for ${it.joinToString(", ")}", Type.Caption, ArmorColors.Amber) }
                if (ts.calledByMe) {
                    PrimaryButton("Ride on", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00)) { ArmorRuntime.send(ArmorRuntime.Command.ResumeTeam) }
                } else if (s.self?.status == null) {
                    PrimaryButton("I'm stopping", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00)) { ArmorRuntime.send(ArmorRuntime.Command.ConfirmTeamStop(ts.alertId)) }
                } else {
                    Txt("You've stopped. Waiting for ${ts.calledByName} to ride on.", Type.Label, ArmorColors.Muted)
                }
            }

            mode != null -> {
                Txt(if (mode == StopMode.TEAM) "WHY IS EVERYONE STOPPING?" else "WHY ARE YOU STOPPING?", Type.Eyebrow, ArmorColors.Muted)
                StopReason.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { r ->
                            ReasonChip(r.label, Modifier.weight(1f)) {
                                if (mode == StopMode.TEAM) ArmorRuntime.send(ArmorRuntime.Command.CallTeamStop(r))
                                else {
                                    ArmorRuntime.send(ArmorRuntime.Command.SetStatus(r.status))
                                    if (s.isLead) ArmorRuntime.send(ArmorRuntime.Command.SendMessage("Stopping for ${r.label.lowercase()}. Keep riding, I'll catch up."))
                                }
                                mode = null
                            }
                        }
                    }
                }
                Txt("Cancel", Type.BodyStrong, ArmorColors.Muted, Modifier.clickable { mode = null }.padding(vertical = 4.dp))
            }

            s.isLead -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigChoice("Team stop", "Everyone stops and confirms", true, Modifier.weight(1f)) { mode = StopMode.TEAM }
                BigChoice("Just me", "Team keeps riding", false, Modifier.weight(1f)) { mode = StopMode.SOLO }
            }

            else -> BigChoice("I'm stopping", "Just me. The lead and group are told", false, Modifier.fillMaxWidth()) { mode = StopMode.SOLO }
        }
    }
}

@Composable
private fun ReasonChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(ArmorColors.S2).border(1.dp, ArmorColors.Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Txt(label, Type.BodyStrong.copy(fontSize = 14.sp))
    }
}

@Composable
private fun BigChoice(title: String, sub: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(if (primary) ArmorColors.Orange else ArmorColors.S2)
            .then(if (primary) Modifier else Modifier.border(1.dp, ArmorColors.Line, shape)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Txt(title, Type.BodyStrong, if (primary) Color(0xFF1A0B00) else ArmorColors.OnBg)
        Txt(sub, Type.Caption, if (primary) Color(0xFF1A0B00).copy(alpha = 0.7f) else ArmorColors.Muted)
    }
}

// ---- chat + voice ---------------------------------------------------------------------------

private val QUICK_REPLIES = listOf("Fuel is low", "Need a break", "Follow me", "Slow down", "Behind you", "All good", "Running late")

/** Full-screen group chat with quick replies, a text box and a microphone for voice commands. */
@Composable
fun ChatScreen(s: RideSnapshot, onClose: () -> Unit) {
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.size - 1) }
    fun send(t: String) { if (t.isNotBlank()) ArmorRuntime.send(ArmorRuntime.Command.SendMessage(t)) }

    Column(Modifier.fillMaxSize().background(ArmorColors.Bg).warmGlow(strength = 0.14f).statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("GROUP CHAT", Type.Hero.copy(fontSize = 34.sp, lineHeight = 36.sp))
            Txt("Close", Type.BodyStrong, ArmorColors.Orange, Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.messages.isEmpty()) item { Txt("No messages yet. Say something to the group.", Type.Body, ArmorColors.Dim) }
            items(s.messages, key = { it.id }) { m ->
                val mine = m.riderId == s.self?.id
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Column(
                        Modifier.clip(RoundedCornerShape(18.dp)).background(if (mine) ArmorColors.Orange.copy(alpha = 0.2f) else ArmorColors.S2)
                            .then(if (mine) Modifier.border(1.dp, ArmorColors.Orange.copy(alpha = 0.4f), RoundedCornerShape(18.dp)) else Modifier)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        if (!mine) Txt(m.riderName.uppercase() + if (m.riderId == s.leadId) " · LEAD" else "", Type.Eyebrow.copy(fontSize = 11.sp, letterSpacing = 1.5.sp), ArmorColors.Muted)
                        Txt(m.text, Type.Body)
                    }
                }
            }
        }
        LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(QUICK_REPLIES) { q ->
                Box(Modifier.clip(CircleShape).background(ArmorColors.S2).border(1.dp, ArmorColors.Line, CircleShape).clickable { send(q) }.padding(horizontal = 12.dp, vertical = 8.dp)) { Txt(q, Type.Label) }
            }
        }
        VoiceCommandBar(s)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = text, onValueChange = { text = it.take(280) }, singleLine = true,
                textStyle = Type.Body.copy(color = ArmorColors.OnBg), cursorBrush = SolidColor(ArmorColors.Orange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(text); text = "" }),
                modifier = Modifier.weight(1f).height(46.dp).clip(CircleShape).background(ArmorColors.S2).border(1.dp, ArmorColors.Line, CircleShape).padding(horizontal = 16.dp),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (text.isEmpty()) Txt("Message the group", Type.Body, ArmorColors.Dim); inner() } },
            )
            Box(Modifier.size(46.dp).clip(CircleShape).background(ArmorColors.Orange).clickable { send(text); text = "" }, contentAlignment = Alignment.Center) { Txt("›", Type.Title.copy(fontSize = 28.sp), Color(0xFF1A0B00)) }
        }
    }
}

/**
 * Microphone row. The phone's own speech recogniser (offline where the device has the language pack) turns speech into
 * text; [VoiceCommands] turns the text into an action, deterministically. Anything that affects other riders asks for a
 * tap to confirm first, and an SOS is never sent by voice.
 */
@Composable
fun VoiceCommandBar(s: RideSnapshot) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<VoiceIntent?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    fun handle(text: String) {
        heard = text
        val intent = VoiceCommands.parse(text, s.isLead)
        pending = null
        when (intent) {
            null -> note = "Didn't catch that. Try again."
            VoiceIntent.ImOkay -> { ArmorRuntime.send(ArmorRuntime.Command.RiderOk); note = "Marked okay." }
            VoiceIntent.SosRequest -> note = "SOS is never sent by voice. Hold the red SOS button to send it."
            else -> { pending = intent; note = null }
        }
    }

    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) { listening = false; results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::handle) }
            override fun onError(error: Int) { listening = false; note = "Couldn't hear that. Try again." }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }
    fun start() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        listening = true; note = null; recognizer?.startListening(i)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() else note = "Microphone permission is needed for voice commands." }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(if (listening) ArmorColors.Red else ArmorColors.S2).border(1.dp, ArmorColors.Line, CircleShape).clickable {
                    if (recognizer == null) note = "Speech recognition isn't available on this phone."
                    else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO)
                },
                contentAlignment = Alignment.Center,
            ) { MicGlyph(if (listening) Color.White else ArmorColors.Muted) }
            Txt(
                when {
                    listening -> "Listening…"
                    heard != null -> "Heard: \"$heard\""
                    else -> "Tap the mic: \"everyone stop for fuel\", \"tell everyone I'm behind\""
                },
                Type.Label, ArmorColors.Muted, maxLines = 2,
            )
        }
        note?.let { Txt(it, Type.Caption, ArmorColors.Amber) }
        pending?.let { intent ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ArmorColors.S2).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(describe(intent), Type.BodyStrong.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Txt("Confirm", Type.BodyStrong, ArmorColors.Orange, Modifier.clickable { run(intent); pending = null }.padding(8.dp))
                Txt("Cancel", Type.BodyStrong, ArmorColors.Muted, Modifier.clickable { pending = null }.padding(8.dp))
            }
        }
    }
}

private fun describe(i: VoiceIntent): String = when (i) {
    is VoiceIntent.TeamStop -> "Team stop for ${i.reason.label.lowercase()}: everyone is told to stop."
    is VoiceIntent.StopSelf -> "Stop just you (${i.status.label.lowercase()})."
    VoiceIntent.ResumeTeam -> "Tell the group to ride on."
    VoiceIntent.NeedHand -> "Ask the group for a hand."
    is VoiceIntent.Say -> "Send to the group: \"${i.text}\""
    VoiceIntent.ImOkay -> "Say you're okay."
    VoiceIntent.SosRequest -> "Hold the SOS button to send an SOS."
}

private fun run(i: VoiceIntent) {
    when (i) {
        is VoiceIntent.TeamStop -> ArmorRuntime.send(ArmorRuntime.Command.CallTeamStop(i.reason))
        is VoiceIntent.StopSelf -> ArmorRuntime.send(ArmorRuntime.Command.SetStatus(i.status))
        VoiceIntent.ResumeTeam -> ArmorRuntime.send(ArmorRuntime.Command.ResumeTeam)
        VoiceIntent.NeedHand -> ArmorRuntime.send(ArmorRuntime.Command.RequestHelp)
        is VoiceIntent.Say -> ArmorRuntime.send(ArmorRuntime.Command.SendMessage(i.text))
        VoiceIntent.ImOkay -> ArmorRuntime.send(ArmorRuntime.Command.RiderOk)
        VoiceIntent.SosRequest -> Unit // never executed by voice
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(color, Offset(w * 0.36f, w * 0.08f), Size(w * 0.28f, w * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = stroke)
        drawArc(color, 0f, 180f, false, Offset(w * 0.2f, w * 0.28f), Size(w * 0.6f, w * 0.46f), style = stroke)
        drawLine(color, Offset(w * 0.5f, w * 0.74f), Offset(w * 0.5f, w * 0.92f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}
