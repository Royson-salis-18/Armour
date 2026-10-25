package com.armor.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens. Neutral, tone-based dark surfaces (each step a little lighter, no glows or gradients), a single
 * orange brand accent, and red / amber / green used only to mean something about safety.
 *
 * Spacing follows an 8-pt grid (4, 8, 12, 16, 20, 24); radii are 10 (controls), 20 (cards), 28 (sheets, buttons).
 */
object ArmorColors {
    // Surfaces, darkest to lightest: warm-neutral charcoal
    val Bg = Color(0xFF0A0A0B)
    val S1 = Color(0xFF151618)
    val S2 = Color(0xFF1D1E21)
    val S3 = Color(0xFF28292D)
    val Line = Color(0xFF2B2C30)

    // Content
    val OnBg = Color(0xFFF4F4F5)
    val Muted = Color(0xFFA3A5AB)
    val Dim = Color(0xFF6E7076)

    // The whole palette is orange, red, black and grey. Orange is the brand and "attention"; red is danger; white and
    // grey are calm and neutral. "Fine" states are drawn in near-white rather than a separate green.
    val Orange = Color(0xFFFF6B1A)
    val Red = Color(0xFFE5342F)
    val Ok = Color(0xFFEDEDEF)

    val MapBg = Color(0xFF0F1011)

    // Names used across the screens
    val Surface = S1
    val SurfaceHigh = S2
    val Stale = Dim
    val Green = Ok
    val Amber = Orange
    val Blue = Orange

    /** Other riders are light greys with a white ring; you are always the orange marker. */
    val RiderPalette = listOf(
        Color(0xFF8E9197), Color(0xFF7A7D84), Color(0xFF9DA0A6), Color(0xFF6B6E75), Color(0xFFA9ACB1), Color(0xFF85888E),
    )

    fun riderColor(id: String): Color = RiderPalette[(id.hashCode() and Int.MAX_VALUE) % RiderPalette.size]
}

private val scheme = darkColorScheme(
    primary = ArmorColors.Orange,
    onPrimary = Color(0xFF1A0B00),
    primaryContainer = Color(0xFF3A1A08),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = ArmorColors.Amber,
    onSecondary = Color.Black,
    background = ArmorColors.Bg,
    onBackground = ArmorColors.OnBg,
    surface = ArmorColors.S1,
    onSurface = ArmorColors.OnBg,
    surfaceVariant = ArmorColors.S2,
    onSurfaceVariant = ArmorColors.Muted,
    error = ArmorColors.Red,
    onError = Color.White,
    outline = ArmorColors.Line,
)

private val typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = InterFamily), bodyMedium = bodyMedium.copy(fontFamily = InterFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFamily), labelLarge = labelLarge.copy(fontFamily = InterFamily),
        labelMedium = labelMedium.copy(fontFamily = InterFamily), labelSmall = labelSmall.copy(fontFamily = InterFamily),
        titleMedium = titleMedium.copy(fontFamily = InterFamily), titleSmall = titleSmall.copy(fontFamily = InterFamily),
    )
}

@Composable
fun ArmorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
