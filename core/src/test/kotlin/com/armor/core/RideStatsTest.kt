package com.armor.core

import com.armor.core.engine.RideStats
import com.armor.core.engine.RideSummary
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RideStatsTest {
    private val frame = LocalFrame(LatLon(12.0, 77.0))
    private fun fix(t: Long, eastM: Double, speed: Double, acc: Double = 5.0) =
        LocationFix(t, frame.toLatLon(Point(eastM, 0.0)), speed, 90.0, acc)

    @Test fun `accumulates distance moving time and top speed`() {
        val s = RideStats()
        for (i in 0..10) s.onFix(fix(i * 1000L, i * 20.0, 20.0))
        val r = s.summary(10_000)
        assertEquals(200.0, r.distanceM, 3.0)
        assertEquals(10_000, r.movingMs)
        assertEquals(20.0, r.maxSpeedMps, 1e-9)
        assertEquals(20.0, r.avgMovingSpeedMps, 0.5)
    }

    @Test fun `gps jitter while stopped adds no distance`() {
        val s = RideStats()
        for (i in 0..30) s.onFix(fix(i * 1000L, if (i % 2 == 0) 0.0 else 2.0, 0.2, acc = 8.0))
        assertEquals(0.0, s.summary(30_000).distanceM, 1e-9)
        assertEquals(0L, s.summary(30_000).movingMs)
    }

    @Test fun `poor fixes and teleports are ignored`() {
        val s = RideStats()
        s.onFix(fix(0, 0.0, 15.0))
        s.onFix(fix(1000, 15.0, 15.0, acc = 120.0))
        s.onFix(fix(2000, 50_000.0, 15.0))
        val r = s.summary(2000)
        assertEquals(0.0, r.distanceM, 1e-9)
        assertTrue(r.maxSpeedMps <= 90.0)
    }

    @Test fun `empty before any fix`() {
        assertEquals(RideSummary.EMPTY, RideStats().summary(5000))
    }
}
