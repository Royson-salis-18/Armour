package com.armor.core.geo

import com.armor.core.model.LatLon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres. */
    fun distanceM(a: LatLon, b: LatLon): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing from [from] to [to], degrees clockwise from true north in [0, 360). */
    fun bearingDeg(from: LatLon, to: LatLon): Double {
        val p1 = Math.toRadians(from.lat)
        val p2 = Math.toRadians(to.lat)
        val dl = Math.toRadians(to.lon - from.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

data class Point(val x: Double, val y: Double)

/**
 * Local east/north tangent-plane projection around [origin] (equirectangular). Accurate to well
 * under a metre at the few-kilometre scales a riding group spans, and trivially invertible.
 */
class LocalFrame(val origin: LatLon) {
    private val mPerDegLat = Math.toRadians(1.0) * Geo.EARTH_RADIUS_M
    private val mPerDegLon = mPerDegLat * cos(Math.toRadians(origin.lat))

    /** x = metres east of origin, y = metres north of origin. */
    fun toPoint(p: LatLon) = Point((p.lon - origin.lon) * mPerDegLon, (p.lat - origin.lat) * mPerDegLat)

    fun toLatLon(p: Point) = LatLon(origin.lat + p.y / mPerDegLat, origin.lon + p.x / mPerDegLon)

    fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}

object ConvexHull {
    /** Andrew's monotone chain. Returns hull vertices counter-clockwise; degenerate inputs return their unique points. */
    fun of(points: List<Point>): List<Point> {
        val pts = points.distinct().sortedWith(compareBy<Point> { it.x }.thenBy { it.y })
        if (pts.size <= 2) return pts
        fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Point>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = ArrayList<Point>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }
}
