package com.armor.app.sensors

import android.os.SystemClock

/**
 * Epoch-based milliseconds that cannot jump: pinned to the wall clock once at ride start, then advanced by the
 * monotonic clock. A network time sync mid-ride therefore cannot make safety timers fire early or stall, while
 * values stay close enough to real time to compare with other riders' reports.
 */
class RideClock {
    private val wallAtStartMs = System.currentTimeMillis()
    private val elapsedAtStartMs = SystemClock.elapsedRealtime()

    fun now(): Long = wallAtStartMs + (SystemClock.elapsedRealtime() - elapsedAtStartMs)

    /** Converts a sensor/location event timestamp (nanoseconds since boot) onto this clock. */
    fun fromElapsedNanos(nanos: Long): Long = wallAtStartMs + (nanos / 1_000_000L - elapsedAtStartMs)
}
