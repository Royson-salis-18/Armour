package com.armor.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.armor.core.model.QuickStatus
import kotlin.math.roundToInt

fun fmtDistance(meters: Double): String =
    if (meters < 1_000) "${meters.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1_000)

fun fmtSpeed(mps: Double): String = "${(mps * 3.6).roundToInt()} km/h"

fun fmtMmSs(ms: Long): String {
    val s = (ms / 1_000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
}

fun fmtClock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

private val POINTS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

fun compass(bearingDeg: Double): String = POINTS[(((bearingDeg + 22.5) % 360) / 45).toInt().coerceIn(0, 7)]


fun QuickStatus.shortLabel(): String = when (this) {
    QuickStatus.FUEL -> "Fuel"
    QuickStatus.BREAK -> "Break"
    QuickStatus.MECHANICAL -> "Repair"
    QuickStatus.TRAFFIC -> "Wait"
    QuickStatus.PHOTO -> "Photo"
}

/** Elapsed time as m:ss under an hour, h:mm:ss after. */
fun fmtHms(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
