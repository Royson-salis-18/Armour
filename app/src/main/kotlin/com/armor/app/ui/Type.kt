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

/** Type scale. Headlines are condensed and set in capitals by the caller; numbers use tabular figures. */
object Type {
    val Hero = TextStyle(fontFamily = CondensedFamily, fontSize = 50.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    val Display = TextStyle(fontFamily = CondensedFamily, fontSize = 92.sp, lineHeight = 88.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
    val Speed = TextStyle(fontFamily = CondensedFamily, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
    val Headline = TextStyle(fontFamily = CondensedFamily, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    val Title = TextStyle(fontFamily = CondensedFamily, fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val Eyebrow = TextStyle(fontFamily = CondensedFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    val Body = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val BodyStrong = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal)
}
