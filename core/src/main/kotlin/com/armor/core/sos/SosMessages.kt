package com.armor.core.sos

import com.armor.core.model.LatLon
import com.armor.core.model.SosReason
import java.util.Locale

/**
 * Text for the SMS sent to emergency contacts. It is written to fit a **single** SMS (160 GSM-7 characters): a
 * multipart text can arrive partially or out of order on a weak signal, which could separate the map link from
 * the message. Plain ASCII keeps it in GSM-7, and numbers use [Locale.ROOT] so the link stays valid on phones whose
 * locale uses decimal commas.
 */
object SosMessages {
    fun mapsLink(p: LatLon): String = String.format(Locale.ROOT, "https://maps.google.com/?q=%.6f,%.6f", p.lat, p.lon)

    fun sos(riderName: String, reason: SosReason, position: LatLon?, accuracyM: Double?, fixAgeS: Long?): String {
        val what = when (reason) {
            SosReason.MANUAL -> "pressed SOS"
            SosReason.STATIONARY_UNACKNOWLEDGED -> "has stopped and is not responding"
            SosReason.CRASH_NO_RESPONSE -> "may have crashed and is not responding"
        }
        val where = if (position == null) {
            "Location unknown (no GPS fix)."
        } else {
            val detail = buildList {
                if (accuracyM != null) add(String.format(Locale.ROOT, "within %.0f m", accuracyM))
                if (fixAgeS != null) add("$fixAgeS s old")
            }.joinToString(", ")
            "Loc: ${mapsLink(position)}" + if (detail.isEmpty()) "" else " ($detail)"
        }
        return "ARMOR SOS: ${ascii(riderName)} $what. $where"
    }

    /**
     * A second text with what first responders need. Kept separate so the SOS itself always fits one SMS; this one is
     * trimmed to one SMS as well, or null when nothing was filled in.
     */
    fun medical(riderName: String, info: com.armor.core.model.MedicalInfo): String? {
        if (info.isEmpty) return null
        val parts = buildList {
            if (info.bloodGroup.isNotBlank()) add("Blood ${info.bloodGroup.trim()}")
            if (info.allergies.isNotBlank()) add("Allergies: ${info.allergies.trim()}")
            if (info.conditions.isNotBlank()) add("Conditions: ${info.conditions.trim()}")
            if (info.medications.isNotBlank()) add("Meds: ${info.medications.trim()}")
        }
        val text = "ARMOR medical, ${ascii(riderName)}: " + parts.joinToString(". ").map { if (it.code in 32..126) it else '?' }.joinToString("")
        return if (text.length <= 160) text else text.take(157) + "..."
    }

    /** Safe-arrival texts for family. */
    fun arrived(riderName: String, label: String): String = "ARMOR: ${ascii(riderName)} has arrived safely at ${ascii(label)}."

    fun late(riderName: String, label: String, position: LatLon?): String =
        "ARMOR: ${ascii(riderName)} has not reached ${ascii(label)} yet and is past the expected time." + (position?.let { " Last loc: ${mapsLink(it)}" } ?: "")

    /** Google Maps turn-by-turn directions to [p] (opens the Maps app on phones). */
    fun directionsLink(p: LatLon): String = String.format(Locale.ROOT, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", p.lat, p.lon)

    fun cancelled(riderName: String): String =
        "ARMOR: ${ascii(riderName)} has cancelled the earlier SOS and says they are OK."

    /** Replaces anything outside printable ASCII so a name like "Zoë" cannot silently switch the SMS to UCS-2. */
    private fun ascii(s: String): String =
        s.take(MAX_NAME).map { if (it.code in 32..126) it else '?' }.joinToString("").trim().ifEmpty { "A rider" }

    /** Matches the 24-character limit on the name field, and keeps the worst-case message within one SMS. */
    const val MAX_NAME = 24
}
