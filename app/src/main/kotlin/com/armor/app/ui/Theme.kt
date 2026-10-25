package com.armor.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ARMOR "system" look: a deep navy-black base, electric-cyan HUD chrome, ARMOR orange for riders, and red/amber/green
 * reserved for safety states so they always mean something.
 */
object ArmorColors {
    val Bg = Color(0xFF04060C)
    val BgGlow = Color(0xFF0B3350)
    val BgGlow2 = Color(0xFF241A55)
    val Panel = Color(0xFF0D1A2E)
    val PanelDeep = Color(0xFF050B16)
    val Line = Color(0xFF17304A)
    val Cyan = Color(0xFF35E0FF)
    val Violet = Color(0xFF7B61FF)
    val Orange = Color(0xFFFF7A1A)
    val Red = Color(0xFFFF3B4E)
    val Amber = Color(0xFFFFC24A)
    val Green = Color(0xFF3DFFA0)
    val OnBg = Color(0xFFE8F4FF)
    val Muted = Color(0xFF7C93A8)
    val Stale = Color(0xFF44566A)

    // Kept so older call sites read naturally.
    val Surface = Panel
    val SurfaceHigh = Color(0xFF0B1A2B)
    val MapBg = Color(0xFF050A14)
    val Grid = Color(0xFF35E0FF)
    val OrangeDim = Color(0xFF8A4310)
}

private val scheme = darkColorScheme(
    primary = ArmorColors.Cyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0A4A5C),
    onPrimaryContainer = Color.White,
    secondary = ArmorColors.Amber,
    onSecondary = Color.Black,
    background = ArmorColors.Bg,
    onBackground = ArmorColors.OnBg,
    surface = ArmorColors.Panel,
    onSurface = ArmorColors.OnBg,
    surfaceVariant = ArmorColors.SurfaceHigh,
    onSurfaceVariant = ArmorColors.Muted,
    error = ArmorColors.Red,
    onError = Color.White,
    outline = ArmorColors.Line,
)

@Composable
fun ArmorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
