package com.armor.core.sos

/**
 * Turns a number as people type or store it ("98765 43210", "098765-43210", "91 98765 43210", "+91 98765 43210") into
 * the international form the SMS and phone apps accept everywhere ("+919876543210").
 *
 * India is handled in full: a 10-digit mobile starting 6-9, with or without a leading 0 or 91, becomes +91XXXXXXXXXX, and
 * an STD landline written with its trunk 0 becomes +91 plus area code and number.
 * For other countries a leading + or 00 is kept as international; anything else is passed through as digits, which the
 * phone network then reads as a local number.
 */
object PhoneNumbers {
    fun normalize(raw: String, country: String = "IN"): String {
        val t = raw.trim()
        val digits = t.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (t.startsWith("+")) return "+$digits"
        if (digits.startsWith("00") && digits.length > 6) return "+" + digits.drop(2)
        if (country.equals("IN", ignoreCase = true)) {
            // A trunk 0 plus 10 digits is a mobile or an STD landline (080 2233 4455); +91 is right for both.
            if (digits.length == 11 && digits.startsWith("0")) return "+91" + digits.drop(1)
            if (digits.length == 12 && digits.startsWith("91") && digits[2] in '6'..'9') return "+$digits"
            if (digits.length == 10 && digits[0] in '6'..'9') return "+91$digits"
        }
        return digits
    }

    /** True if [raw] looks like a number a text can be sent to (at least a short code's worth of digits). */
    fun looksValid(raw: String): Boolean = raw.count { it.isDigit() } >= 5

    /** The same person typed two ways ("+91 98765 43210" and "098765 43210") compares equal. */
    fun sameNumber(a: String, b: String): Boolean = a.filter { it.isDigit() }.takeLast(10) == b.filter { it.isDigit() }.takeLast(10)
}
