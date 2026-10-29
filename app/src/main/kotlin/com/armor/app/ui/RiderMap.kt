package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.core.engine.RideSnapshot
import com.armor.core.engine.RiderView
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.LatLon
import com.armor.core.model.SafetyCode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val RING_STEPS = doubleArrayOf(50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0, 100_000.0)

/**
 * The default ride map: north-up, centred on you, auto-scaled to keep everyone in frame, on OpenStreetMap street tiles
 * that need no API key (see MapTiles). Tiles are cached, and with none available it still shows who is where, who is
 * with the group and who has fallen away (range rings, the group's area, your route, each rider as an avatar), so
 * nothing safety-related ever waits on the network.
 */
@Composable
fun RiderMap(snapshot: RideSnapshot, modifier: Modifier = Modifier, trail: List<LatLon> = emptyList(), bottomInsetFraction: Float = 0f) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    val faint = TextStyle(color = Color.Black.copy(alpha = 0.5f), fontSize = 10.sp)

    Canvas(modifier) {
        MapTiles.version // repaint when a tile arrives
        // Light street map: the land colour of the tiles, so the map still looks right while tiles load or offline.
        drawRect(Color(0xFFEDEAE3))
        val ink = Color.Black

        val riders = snapshot.riders
        if (riders.isEmpty()) {
            val msg = if (snapshot.gpsLost) "Finding your location…" else "No riders yet"
            val st = TextStyle(color = Color(0xFF55575C), fontSize = 14.sp)
            val m = measurer.measure(msg, st)
            drawText(measurer, msg, Offset((size.width - m.size.width) / 2f, (size.height - m.size.height) / 2f), st)
            return@Canvas
        }

        val frame = LocalFrame((snapshot.self ?: riders.first()).position)
        val pts = riders.associate { it.id to frame.toPoint(it.position) }
        var extent = 120.0
        for (p in pts.values) extent = max(extent, max(abs(p.x), abs(p.y)))
        val usableH = size.height * (1f - bottomInsetFraction)
        val c = Offset(size.width / 2f, usableH / 2f + 20.dp.toPx())
        val half = min(size.width / 2f - 56.dp.toPx(), usableH / 2f - 56.dp.toPx())
        val scale = half / extent
        fun screen(p: Point) = Offset(c.x + (p.x * scale).toFloat(), c.y - (p.y * scale).toFloat())

        // Street tiles under everything else.
        run {
            val lat0 = frame.origin.lat
            val mpp = 156543.03392 * kotlin.math.cos(Math.toRadians(lat0))
            val z = Math.round(kotlin.math.ln(mpp * scale) / kotlin.math.ln(2.0)).toInt().coerceIn(3, 18)
            val tl = frame.toLatLon(Point(-c.x / scale.toDouble(), c.y / scale.toDouble()))
            val br = frame.toLatLon(Point((size.width - c.x) / scale.toDouble(), -(size.height - c.y) / scale.toDouble()))
            val x0 = MapTiles.tileX(tl.lon, z); val x1 = MapTiles.tileX(br.lon, z)
            val y0 = MapTiles.tileY(tl.lat, z); val y1 = MapTiles.tileY(br.lat, z)
            if ((x1 - x0 + 1) * (y1 - y0 + 1) <= 40) {
                for (tx in x0..x1) for (ty in y0..y1) {
                    val a = screen(frame.toPoint(LatLon(MapTiles.lat(ty, z), MapTiles.lon(tx, z))))
                    val b = screen(frame.toPoint(LatLon(MapTiles.lat(ty + 1, z), MapTiles.lon(tx + 1, z))))
                    val img = MapTiles.get(z, tx, ty) ?: continue
                    drawImage(
                        img, dstOffset = IntOffset(Math.round(a.x), Math.round(a.y)),
                        dstSize = IntSize(Math.round(b.x - a.x) + 1, Math.round(b.y - a.y) + 1),
                    )
                }
            }
        }

        // Range rings
        val step = RING_STEPS.firstOrNull { it >= extent / 3 } ?: RING_STEPS.last()
        var ring = step
        var n = 0
        while (ring <= extent * 1.3 && n < 3) {
            val radius = (ring * scale).toFloat()
            drawCircle(ink.copy(alpha = 0.16f - n * 0.04f), radius, c, style = Stroke(1.dp.toPx()))
            drawText(measurer, fmtDistance(ring), Offset(c.x + radius * 0.707f + 4.dp.toPx(), c.y - radius * 0.707f - 14.dp.toPx()), faint)
            ring += step; n++
        }

        // The group's area: a soft rounded blob around the main group, orange; any other cluster is red.
        snapshot.clusters.forEachIndexed { i, cluster ->
            val tint = if (i == snapshot.mainClusterIndex) ArmorColors.Orange else ArmorColors.Red
            val hull = cluster.hull.map { screen(frame.toPoint(it)) }
            val path = Path().apply {
                hull.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                if (hull.size > 2) close()
            }
            val pad = 26.dp.toPx()
            if (hull.size == 1) {
                drawCircle(tint.copy(alpha = 0.14f), pad, hull[0])
                drawCircle(tint.copy(alpha = 0.55f), pad, hull[0], style = Stroke(1.5.dp.toPx()))
            } else {
                if (hull.size > 2) drawPath(path, tint.copy(alpha = 0.14f), style = Fill)
                drawPath(path, tint.copy(alpha = 0.14f), style = Stroke(pad * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Your route: a glowing orange line, like a navigation route.
        if (trail.size > 1) {
            val tp = Path().apply {
                trail.map { screen(frame.toPoint(it)) }.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            drawPath(tp, ArmorColors.Orange.copy(alpha = 0.22f), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(tp, ArmorColors.Orange, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Riders first, then every label on top so no name is ever hidden behind another rider.
        val ordered = riders.sortedBy { if (it.isSelf) 1 else 0 }
        for (rider in ordered) drawRiderMarker(rider, screen(pts.getValue(rider.id)), measurer)
        for (rider in ordered) drawRiderLabel(rider, screen(pts.getValue(rider.id)), measurer, label)
    }
}

private fun DrawScope.drawRiderMarker(rider: RiderView, at: Offset, measurer: TextMeasurer) {
    val alarm = rider.safety == SafetyCode.SOS || rider.safety == SafetyCode.CRASH
    val warn = rider.safety == SafetyCode.ALERTED || rider.safety == SafetyCode.CHECKING

    if (rider.isSelf) {
        // Orange "you" marker with an accuracy halo and, when moving, a heading cone.
        val heading = rider.headingDeg
        drawCircle(ArmorColors.Blue.copy(alpha = 0.16f), 30.dp.toPx(), at)
        if (heading != null && rider.speedMps > 1.0) {
            rotate(heading.toFloat(), at) {
                val len = 54.dp.toPx()
                val w = 20.dp.toPx()
                drawPath(Path().apply { moveTo(at.x, at.y); lineTo(at.x - w, at.y - len); lineTo(at.x + w, at.y - len); close() }, ArmorColors.Blue.copy(alpha = 0.26f))
            }
        }
        drawCircle(Color.White, 11.dp.toPx(), at)
        drawCircle(ArmorColors.Blue, 8.dp.toPx(), at)
        return
    }

    val color = if (rider.isStale) ArmorColors.Dim else ArmorColors.riderColor(rider.id)
    drawCircle(Color.Black.copy(alpha = 0.35f), 18.dp.toPx(), at + Offset(0f, 2.dp.toPx()))
    drawCircle(Color.White, 18.dp.toPx(), at)
    drawCircle(color, 15.5f.dp.toPx(), at)
    val initial = rider.name.firstOrNull()?.uppercase() ?: "?"
    val st = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val im = measurer.measure(initial, st)
    drawText(measurer, initial, Offset(at.x - im.size.width / 2f, at.y - im.size.height / 2f), st)

    if (rider.isOutlier || alarm || warn) {
        val bc = if (warn && !alarm && !rider.isOutlier) ArmorColors.Amber else ArmorColors.Red
        val b = Offset(at.x + 13.dp.toPx(), at.y - 13.dp.toPx())
        drawCircle(Color.White, 9.dp.toPx(), b)
        drawCircle(bc, 7.dp.toPx(), b)
        val bs = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        val bm = measurer.measure("!", bs)
        drawText(measurer, "!", Offset(b.x - bm.size.width / 2f, b.y - bm.size.height / 2f), bs)
    }
}

private fun DrawScope.drawRiderLabel(rider: RiderView, at: Offset, measurer: TextMeasurer, label: TextStyle) {
    val text = buildString {
        append(if (rider.isSelf) "You" else rider.name)
        if (rider.isSelf && rider.speedMps < 1.0 && rider.isOutlier) append(" · stopped")
        rider.distanceFromSelfM?.let { if (rider.isOutlier || rider.isStale) append(" · ${fmtDistance(it)}") }
        if (rider.isStale) append(" (last seen)")
    }
    val m = measurer.measure(text, label)
    val padX = 8.dp.toPx()
    val padY = 2.dp.toPx()
    val top = at.y + (if (rider.isSelf) 17 else 22).dp.toPx()
    drawRoundRect(
        Color(0xFF0E1013).copy(alpha = 0.78f),
        topLeft = Offset(at.x - m.size.width / 2f - padX, top),
        size = Size(m.size.width + padX * 2, m.size.height + padY * 2),
        cornerRadius = CornerRadius(50f, 50f),
    )
    drawText(measurer, text, Offset(at.x - m.size.width / 2f, top + padY), label)
}
