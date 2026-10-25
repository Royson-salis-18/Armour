package com.armor.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
