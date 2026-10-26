package com.armor.core.voice

import com.armor.core.model.QuickStatus
import com.armor.core.model.StopReason

/** What a spoken or typed phrase means. */
sealed interface VoiceIntent {
    /** Lead only: everyone stops for [reason]. */
    data class TeamStop(val reason: StopReason) : VoiceIntent

    /** Just this rider stops for [status]. */
    data class StopSelf(val status: QuickStatus) : VoiceIntent

    data object ResumeTeam : VoiceIntent
    data object ImOkay : VoiceIntent
    data object NeedHand : VoiceIntent

    /** Never executed by voice alone: the UI must ask the rider to confirm before sending an SOS. */
    data object SosRequest : VoiceIntent

    data class Say(val text: String) : VoiceIntent
}

/**
 * A small deterministic phrase parser, not a model: it matches keywords, so it behaves the same every time, runs
 * offline and can be tested. Speech-to-text (the phone's own recogniser) turns audio into the string; this turns the
 * string into an action.
 *
 * Safety rules: an SOS is only ever a *request* the rider must confirm, and an unrecognised phrase is never guessed
 * at, it returns null so the app can say "didn't catch that".
 */
object VoiceCommands {
    private val reasonWords: List<Pair<StopReason, List<String>>> = listOf(
        StopReason.FUEL to listOf("fuel", "petrol", "gas", "diesel", "refuel", "fill up", "fill the tank"),
        StopReason.FOOD to listOf("food", "eat", "lunch", "dinner", "breakfast", "meal", "tea", "chai", "coffee"),
        StopReason.MECHANICAL to listOf("puncture", "flat tyre", "flat tire", "breakdown", "broke down", "mechanical", "chain", "engine"),
        StopReason.WEATHER to listOf("rain", "raining", "rainy", "weather", "storm", "stormy", "fog", "foggy"),
        StopReason.BREAK to listOf("break", "rest", "stretch", "toilet", "washroom", "bathroom", "pit stop"),
    )

    fun parse(raw: String, isLead: Boolean): VoiceIntent? {
        val text = raw.lowercase().replace("'", "").replace("\u2019", "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return null

        // Free-text chat: "message ...", "tell everyone ...", "say ..."
        Regex("^(?:send (?:a )?message|message|tell (?:everyone|the team|the group|them)|say|text) (.+)$").find(text)?.let { return VoiceIntent.Say(it.groupValues[1]) }

        if (has(text, "sos", "emergency", "call for help", "call an ambulance", "i crashed", "i have crashed")) return VoiceIntent.SosRequest
        if (has(text, "im okay", "i am okay", "im ok", "i am ok", "im fine", "i am fine", "all good", "cancel alert")) return VoiceIntent.ImOkay
        if (has(text, "need a hand", "need help", "need some help", "need assistance", "help me")) return VoiceIntent.NeedHand
        if (has(text, "ride on", "resume", "lets go", "carry on", "everyone go", "move out", "start riding")) return if (isLead) VoiceIntent.ResumeTeam else null

        val stopWords = has(text, "stop", "pull over", "pull in", "halt", "park", "stopping")
        val reason = reasonWords.firstOrNull { (_, ws) -> ws.any { has(text, it) } }?.first
        val everyone = has(text, "everyone", "everybody", "all of us", "the team", "the group", "all riders", "team stop", "we stop", "lets stop", "we need")
        if (stopWords || (reason != null && has(text, "need", "going to", "i have to", "ill", "we need"))) {
            if (isLead && everyone) return VoiceIntent.TeamStop(reason ?: StopReason.OTHER)
            if (isLead && has(text, "just me", "only me", "myself", "im stopping", "i am stopping")) return VoiceIntent.StopSelf((reason ?: StopReason.OTHER).status)
            if (reason != null || stopWords) return if (isLead && !has(text, "me", "my", "i")) VoiceIntent.TeamStop(reason ?: StopReason.OTHER) else VoiceIntent.StopSelf((reason ?: StopReason.OTHER).status)
        }
        return null
    }

    private fun has(text: String, vararg phrases: String): Boolean = phrases.any { p ->
        // whole-word / whole-phrase match so "sos" doesn't fire inside another word
        Regex("(^| )" + Regex.escape(p) + "( |$)").containsMatchIn(text)
    }
}
