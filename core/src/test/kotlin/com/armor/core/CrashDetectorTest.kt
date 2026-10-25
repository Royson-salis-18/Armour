package com.armor.core

import com.armor.core.crash.CrashCandidate
import com.armor.core.crash.CrashConfig
import com.armor.core.crash.CrashDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashDetectorTest {
    private val g = CrashDetector.G
    private val t0 = 1_000_000L

    /** Drives one scripted event, returning the first candidate emitted (if any). */
    private fun CrashDetector.quiet(from: Long, to: Long) {
        var t = from
        while (t < to) { onAccel(t, 0.0, 0.0, g); t += 20 }
    }

    /**
     * Impact at [at]. If [rotate], the phone spins for 600 ms and settles on its side (gravity moves from z to x);
     * if [spin] only, it spins but ends upright; if neither, it just takes the knock.
     */
    private fun CrashDetector.impact(at: Long, peakG: Double, rotate: Boolean, spin: Boolean = rotate): CrashCandidate? {
        var found: CrashCandidate? = null
        onAccel(at, 0.0, 0.0, peakG * g)?.let { found = it }
        var i = 1
        while (i <= 100) {
            val t = at + i * 20L
            val onSide = rotate && i > 30
            val c = if (onSide) onAccel(t, g, 0.0, 0.0) else onAccel(t, 0.0, 0.0, g)
            if (c != null) found = c
            onGyro(t, 0.0, 0.0, if (spin && i < 40) 5.0 else 0.0)
            i++
        }
        return found
    }

    @Test fun `hard impact followed by tumbling is a crash candidate`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        val c = assertNotNull(d.impact(t0, 6.0, rotate = true))
        assertEquals(t0, c.impactTimeMs)
        assertEquals(6.0, c.peakG, 0.01)
        assertTrue(c.rotationRad >= 2.0, "rotation ${c.rotationRad}")
        assertEquals(90.0, assertNotNull(c.tiltDeg), 5.0)
    }

    @Test fun `impact with no orientation change is not a crash`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        assertNull(d.impact(t0, 7.0, rotate = false))
    }

    @Test fun `ordinary road shock below the impact threshold is ignored even when the phone spins`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        assertNull(d.impact(t0, 2.5, rotate = true))
    }

    @Test fun `rotation with no impact is ignored`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        for (i in 0 until 100) { d.onGyro(t0 + i * 20L, 0.0, 0.0, 6.0); d.onAccel(t0 + i * 20L, 0.0, 0.0, g) }
        assertNull(d.poll(t0 + 3_000))
    }

    @Test fun `ending on its side without gyro data still counts via tilt`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        val c = assertNotNull(d.impact(t0, 5.0, rotate = true, spin = false))
        assertEquals(0.0, c.rotationRad, 1e-9)
        assertTrue(c.tiltDeg!! > 60.0)
    }

    @Test fun `without a quiet baseline rotation alone can still qualify`() {
        val d = CrashDetector()
        val c = assertNotNull(d.impact(t0, 6.0, rotate = true, spin = true))
        assertNull(c.tiltDeg)
    }

    @Test fun `a second impact right after a candidate is suppressed by the cooldown`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        assertNotNull(d.impact(t0, 6.0, rotate = true))
        val tAfter = t0 + 100 * 20L
        assertNull(d.impact(tAfter + 1_000, 6.0, rotate = true))
    }

    @Test fun `the window closes on poll if the accelerometer goes quiet`() {
        val d = CrashDetector()
        d.quiet(t0 - 3_000, t0)
        d.onAccel(t0, 0.0, 0.0, 6 * g)
        for (i in 1..40) d.onGyro(t0 + i * 20L, 0.0, 0.0, 5.0)
        assertNull(d.poll(t0 + 1_000))
        assertNotNull(d.poll(t0 + 1_600))
        assertTrue(!d.isObserving)
    }

    @Test fun `thresholds are configurable`() {
        val d = CrashDetector(CrashConfig(impactG = 2.0))
        d.quiet(t0 - 3_000, t0)
        assertNotNull(d.impact(t0, 2.5, rotate = true))
    }
}
