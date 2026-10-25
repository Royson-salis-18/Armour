package com.armor.core

import com.armor.core.motion.StationaryDetector
import com.armor.core.support.at
import com.armor.core.support.fix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StationaryDetectorTest {
    private val t0 = 1_000_000L

    @Test fun `time stopped accumulates from the first fix at the spot`() {
        val d = StationaryDetector()
        for (s in 0..60) d.onFix(fix(t0 + s * 1000L, at(0.0, 0.0)))
        assertEquals(60_000, d.stationaryForMs(t0 + 60_000))
        assertFalse(d.isMoving(t0 + 60_000))
    }

    @Test fun `GPS jitter inside the radius does not reset the stop`() {
        val d = StationaryDetector()
        for (s in 0..60) d.onFix(fix(t0 + s * 1000L, at((s % 3) * 6.0, (s % 2) * 6.0)))
        assertEquals(60_000, d.stationaryForMs(t0 + 60_000))
    }

    @Test fun `one wild fix is ignored but two consecutive displaced fixes mean movement`() {
        val d = StationaryDetector()
        for (s in 0..30) d.onFix(fix(t0 + s * 1000L, at(0.0, 0.0)))
        d.onFix(fix(t0 + 31_000, at(0.0, 80.0)))
        d.onFix(fix(t0 + 32_000, at(0.0, 0.0)))
        assertEquals(32_000, d.stationaryForMs(t0 + 32_000))

        d.onFix(fix(t0 + 33_000, at(0.0, 80.0)))
        d.onFix(fix(t0 + 34_000, at(0.0, 160.0)))
        assertTrue(d.isMoving(t0 + 34_000))
        assertEquals(0, d.stationaryForMs(t0 + 34_000))
    }

    @Test fun `ground speed above the threshold means moving even without displacement`() {
        val d = StationaryDetector()
        d.onFix(fix(t0, at(0.0, 0.0)))
        d.onFix(fix(t0 + 1000, at(1.0, 0.0), speed = 5.0))
        assertTrue(d.isMoving(t0 + 1000))
    }

    @Test fun `poor accuracy widens the radius`() {
        val d = StationaryDetector()
        d.onFix(fix(t0, at(0.0, 0.0), acc = 40.0))
        d.onFix(fix(t0 + 1000, at(0.0, 50.0), acc = 40.0))
        d.onFix(fix(t0 + 2000, at(0.0, 45.0), acc = 40.0))
        assertFalse(d.isMoving(t0 + 2000))
        assertEquals(2000, d.stationaryForMs(t0 + 2000))
    }

    @Test fun `lost GPS is unknown rather than stationary`() {
        val d = StationaryDetector()
        for (s in 0..20) d.onFix(fix(t0 + s * 1000L, at(0.0, 0.0)))
        assertTrue(d.gpsLost(t0 + 20_000 + 11_000))
        assertEquals(0, d.stationaryForMs(t0 + 20_000 + 11_000))
        assertFalse(d.isMoving(t0 + 20_000 + 11_000))
    }

    @Test fun `no fix yet is GPS lost`() {
        assertTrue(StationaryDetector().gpsLost(t0))
    }
}
