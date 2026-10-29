package com.armor.core.voice

import com.armor.core.model.HazardType
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

    /** Spoken read-out: state, speed, the group, open alerts. */
    data object Status : VoiceIntent

    /** Read the latest group messages aloud. */
    data object ReadMessages : VoiceIntent

    /** Mark a hazard at the rider's position for everyone behind. */
    data class Hazard(val type: HazardType) : VoiceIntent

    /** Ring one of the rider's emergency contacts or riders through the phone app. [name] is as saved. */
    data class CallContact(val name: String) : VoiceIntent

    /** Text one of the rider's contacts; the message also shows in the phone's Messages app. */
    data class TextContact(val name: String, val text: String) : VoiceIntent
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

    /** Words the phone's recogniser hears when someone says "Armor". */
    private val wakeWords = listOf("hey armor", "hey armour", "ok armor", "okay armor", "armor", "armour", "amour", "armer", "armoured", "armored")

    private fun clean(raw: String) = raw.lowercase().replace("'", "").replace("\u2019", "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * If [raw] contains the wake word, what was said after it ("" when only "Armor" was said); null when the wake word
     * isn't there, so background talk is ignored.
     */
    fun afterWakeWord(raw: String): String? {
        val text = clean(raw)
        for (w in wakeWords) {
            val m = Regex("(^| )" + Regex.escape(w) + "( |$)").find(text) ?: continue
            return text.substring(m.range.last + 1).trim()
        }
        return null
    }

    /** A yes or no answer to "send an SOS?" and similar; null if it was neither. */
    fun yesNo(raw: String): Boolean? {
        val text = clean(raw)
        if (has(text, "yes", "yeah", "yep", "yup", "confirm", "do it", "send it", "go ahead", "haan", "ha")) return true
        if (has(text, "no", "nope", "cancel", "dont", "stop", "nahi", "na")) return false
        return null
    }

    private val hazardWords: List<Pair<HazardType, List<String>>> = listOf(
        HazardType.POTHOLE to listOf("pothole", "potholes", "speed breaker", "speed bump", "bump"),
        HazardType.OIL to listOf("oil", "gravel", "sand", "slippery", "diesel spill", "mud"),
        HazardType.ACCIDENT to listOf("accident"),
        HazardType.ANIMAL to listOf("animal", "animals", "cow", "cows", "dog", "dogs", "cattle", "buffalo", "goat"),
        HazardType.POLICE to listOf("police", "cops", "checkpoint", "check post", "checking"),
        HazardType.ROADWORK to listOf("roadwork", "roadworks", "road work", "construction", "diversion", "road closed"),
    )

    /**
     * @param contacts names of the rider's emergency contacts and fellow riders, so "call Mom" or "text Ravi on my way"
     * can be matched to a person. Names never matched are not guessed.
     */
    fun parse(raw: String, isLead: Boolean, contacts: List<String> = emptyList()): VoiceIntent? {
        val text = clean(raw)
        if (text.isEmpty()) return null
        // A full name ("ravi kumar"), or a single spoken word matching someone's first name ("ravi").
        fun contact(spoken: String): String? {
            val words = spoken.trim()
            if (words.isEmpty()) return null
            return contacts.firstOrNull { clean(it) == words }
                ?: if (" " in words) null else contacts.firstOrNull { clean(it).split(" ").first() == words }
        }

        // "call Mom", "ring Ravi"
        Regex("^(?:call|ring|phone|dial) (.+)$").find(text)?.let { m ->
            val who = m.groupValues[1].removePrefix("to ")
            (contact(who) ?: contact(who.split(" ").first()))?.let { return VoiceIntent.CallContact(it) }
        }
        // "text Mom I'm running late", "message Ravi on my way": the first one or two words name the person.
        Regex("^(?:text|sms|message|send (?:a )?message to|send a text to|tell) (.+)$").find(text)?.let { m ->
            val words = m.groupValues[1].split(" ")
            for (n in listOf(2, 1)) {
                if (words.size > n) contact(words.take(n).joinToString(" "))?.let { return VoiceIntent.TextContact(it, words.drop(n).joinToString(" ")) }
            }
        }

        // Free-text chat: "message ...", "tell everyone ...", "say ..."
        Regex("^(?:send (?:a )?message|message|tell (?:everyone|the team|the group|them)|say|text) (.+)$").find(text)?.let { return VoiceIntent.Say(it.groupValues[1]) }

        if (has(text, "sos", "emergency", "call for help", "call an ambulance", "i crashed", "i have crashed", "i had an accident", "ive had an accident")) return VoiceIntent.SosRequest
        if (has(text, "status", "where is everyone", "where are they", "how far", "whats my speed", "update me", "how are we doing")) return VoiceIntent.Status
        if (has(text, "read messages", "read the messages", "any messages", "read chat", "new messages", "whats new")) return VoiceIntent.ReadMessages
        hazardWords.firstOrNull { (_, ws) -> ws.any { has(text, it) } }?.let { (type, _) ->
            // "accident" alone is a hazard ahead; the rider's own crash was caught as an SOS above.
            if (!has(text, "stop", "pull over") || has(text, "ahead", "careful", "watch out", "here", "on the road")) return VoiceIntent.Hazard(type)
        }
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
