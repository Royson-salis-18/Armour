package com.armor.core

import com.armor.core.geo.FixSmoother
import com.armor.core.geo.Geo
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.LatLon
import com.armor.core.model.LocationFix
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixSmootherTest {
    private val home = LatLon(12.9716, 77.5946)
    private val frame = LocalFrame(home)
    private fun fix(t: Long, x: Double, y: Double, speed: Double, acc: Double) = LocationFix(t * 1000, frame.toLatLon(Point(x, y)), speed, null, acc)

    @Test fun `parked phone with wandering fixes stays put`() {
        val s = FixSmoother()
        val rnd = Random(7)
        val out = (0 until 120).mapNotNull { t ->
            s.smooth(fix(t.toLong(), rnd.nextDouble(-12.0, 12.0), rnd.nextDouble(-12.0, 12.0), rnd.nextDouble(0.0, 1.0), rnd.nextDouble(8.0, 20.0)))
        }
        // The dot never jumps; it only creeps toward the true spot as better fixes arrive.
        val step = out.zipWithNext { a, b -> Geo.distanceM(a.position, b.position) }.max()
        assertTrue(step < 3.0, "largest jump between fixes was $step m")
        val end = Geo.distanceM(home, out.last().position)
        assertTrue(end < 8.0, "settles near the true spot, was $end m off")
        assertTrue(out.all { it.speedMps == 0.0 }, "parked speed reads 0")
    }

    @Test fun `riding in a straight line is followed closely`() {
        val s = FixSmoother()
        var last: LocationFix? = null
        for (t in 0..60) last = s.smooth(fix(t.toLong(), t * 15.0, 0.0, 15.0, 5.0)) ?: last
        val p = frame.toPoint(last!!.position)
        assertTrue(kotlin.math.abs(p.x - 900.0) < 20.0, "close to the true spot, was ${p.x}")
        assertEquals(15.0, last.speedMps)
    }

    @Test fun `a wild fix right after good ones is ignored`() {
        val s = FixSmoother()
        for (t in 0..10) s.smooth(fix(t.toLong(), 0.0, 0.0, 0.0, 5.0))
        assertNull(s.smooth(fix(11, 400.0, 0.0, 0.0, 150.0)))
    }

    @Test fun `setting off again leaves the parked spot`() {
        val s = FixSmoother()
        for (t in 0..20) s.smooth(fix(t.toLong(), 0.0, 0.0, 0.0, 6.0))
        var last: LocationFix? = null
        for (t in 21..40) last = s.smooth(fix(t.toLong(), (t - 20) * 10.0, 0.0, 10.0, 6.0)) ?: last
        assertTrue(frame.toPoint(last!!.position).x > 150.0)
    }
}
