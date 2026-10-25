package com.armor.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark with orange highlights, as in the design brief; red and amber are reserved for safety states. */
object ArmorColors {
    val Bg = Color(0xFF0B0B0D)
    val BgGlow = Color(0xFF1B130D)
    val Line = Color(0xFF2A2A31)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF202026)
    val MapBg = Color(0xFF0F0F13)
    val Grid = Color(0xFF2D2D36)
    val Orange = Color(0xFFFF7A1A)
    val OrangeDim = Color(0xFF8A4310)
    val Red = Color(0xFFFF3B30)
    val Amber = Color(0xFFFFC107)
    val Green = Color(0xFF34C759)
    val OnBg = Color(0xFFF2F2F5)
    val Muted = Color(0xFF9A9AA5)
    val Stale = Color(0xFF6B6B75)
}

private val scheme = darkColorScheme(
    primary = ArmorColors.Orange,
    onPrimary = Color.Black,
    primaryContainer = ArmorColors.OrangeDim,
    onPrimaryContainer = Color.White,
    secondary = ArmorColors.Amber,
    onSecondary = Color.Black,
    background = ArmorColors.Bg,
    onBackground = ArmorColors.OnBg,
    surface = ArmorColors.Surface,
    onSurface = ArmorColors.OnBg,
    surfaceVariant = ArmorColors.SurfaceHigh,
    onSurfaceVariant = ArmorColors.Muted,
    error = ArmorColors.Red,
    onError = Color.White,
    outline = Color(0xFF3A3A42),
)

@Composable
fun ArmorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
