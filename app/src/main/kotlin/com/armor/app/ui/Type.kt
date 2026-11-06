package com.armor.app.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.armor.app.R

/**
 * ARMOR's typefaces, bundled so the app looks the same on every phone (both are SIL Open Font License, see
 * docs/licenses). Barlow Condensed gives headlines and numerals a racing/instrument-cluster character; Inter is the
 * quiet, legible face for everything you read.
 */
val CondensedFamily = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Instrument-panel labels and readouts: small meta text, eyebrows and numbers. */
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** Type scale. Headlines are condensed and set in capitals by the caller; numbers use tabular figures. */
object Type {
    val Hero = TextStyle(fontFamily = CondensedFamily, fontSize = 50.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    val Display = TextStyle(fontFamily = CondensedFamily, fontSize = 92.sp, lineHeight = 88.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
    val Speed = TextStyle(fontFamily = CondensedFamily, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
    val Headline = TextStyle(fontFamily = InterFamily, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
    val Title = TextStyle(fontFamily = InterFamily, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val Eyebrow = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
    /** Numbers on cards: distance, time, counts. */
    val Numeric = TextStyle(fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")
    /** Small monospaced meta text: "3 riders", "v12", "GPS 4 m". */
    val Meta = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp)
    val Body = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal)
}
