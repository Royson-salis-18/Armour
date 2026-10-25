package com.armor.core

import com.armor.core.geo.ConvexHull
import com.armor.core.geo.Geo
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.LatLon
import com.armor.core.support.ORIGIN
import com.armor.core.support.at
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {
    @Test fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, Geo.distanceM(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 50.0)
    }

    @Test fun `distance is symmetric and zero for identical points`() {
        val a = LatLon(12.9, 77.5)
        val b = LatLon(13.1, 77.7)
        assertEquals(Geo.distanceM(a, b), Geo.distanceM(b, a), 1e-6)
        assertEquals(0.0, Geo.distanceM(a, a), 1e-9)
    }

    @Test fun `bearing follows compass directions`() {
        assertEquals(0.0, Geo.bearingDeg(ORIGIN, at(0.0, 500.0)), 0.5)
        assertEquals(90.0, Geo.bearingDeg(ORIGIN, at(500.0, 0.0)), 0.5)
        assertEquals(180.0, Geo.bearingDeg(ORIGIN, at(0.0, -500.0)), 0.5)
        assertEquals(270.0, Geo.bearingDeg(ORIGIN, at(-500.0, 0.0)), 0.5)
    }

    @Test fun `local frame round-trips and agrees with haversine at ride scale`() {
        val frame = LocalFrame(ORIGIN)
        val p = at(1_200.0, -800.0)
        val back = frame.toLatLon(frame.toPoint(p))
        assertEquals(p.lat, back.lat, 1e-9)
        assertEquals(p.lon, back.lon, 1e-9)
        assertEquals(Math.hypot(1_200.0, 800.0), Geo.distanceM(ORIGIN, p), 2.0)
    }

    @Test fun `hull of a square ignores interior and duplicate points`() {
        val pts = listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(5.0, 5.0), Point(0.0, 0.0))
        val hull = ConvexHull.of(pts)
        assertEquals(4, hull.size)
        assertTrue(Point(5.0, 5.0) !in hull)
    }

    @Test fun `hull of degenerate inputs returns the distinct points`() {
        assertEquals(1, ConvexHull.of(listOf(Point(1.0, 1.0), Point(1.0, 1.0))).size)
        assertEquals(2, ConvexHull.of(listOf(Point(0.0, 0.0), Point(3.0, 4.0))).size)
        // collinear points collapse to the two extremes
        assertEquals(2, ConvexHull.of(listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(2.0, 2.0))).size)
    }
}
