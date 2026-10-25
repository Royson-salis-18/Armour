package com.armor.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.core.engine.RideSnapshot
import com.armor.core.engine.RiderView
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.LatLon
import com.armor.core.model.SafetyCode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val RING_STEPS = doubleArrayOf(50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0, 100_000.0)

/**
 * An offline tactical map: north-up, centred on you, auto-scaled to keep everyone in frame, with a compass bezel,
 * range rings, a radar sweep, your recent route as a glowing trail, and the group's boundary. It draws no map tiles
 * on purpose — it must work with no internet and no API key — so it shows relative geometry (who is where, who is
 * in formation, who has fallen out of it) rather than roads.
 */
@Composable
fun RiderMap(snapshot: RideSnapshot, modifier: Modifier = Modifier, trail: List<LatLon> = emptyList()) {
    val measurer = rememberTextMeasurer()
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    val label = TextStyle(color = ArmorColors.OnBg, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    val hud = TextStyle(color = ArmorColors.Cyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    val faint = TextStyle(color = ArmorColors.Cyan.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)

    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF0C2238), ArmorColors.MapBg), center = center, radius = size.maxDimension * 0.7f))
        val grid = ArmorColors.Cyan.copy(alpha = 0.055f)
        val g = 28.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height)); gx += g }
        var gy = 0f
        while (gy < size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy)); gy += g }

        val riders = snapshot.riders
        if (riders.isEmpty()) {
            val msg = if (snapshot.gpsLost) "ACQUIRING GPS…" else "NO RIDERS IN RANGE"
            val m = measurer.measure(msg, hud)
            drawText(measurer, msg, Offset((size.width - m.size.width) / 2f, (size.height - m.size.height) / 2f), hud)
            return@Canvas
        }

        val frame = LocalFrame((snapshot.self ?: riders.first()).position)
        val pts = riders.associate { it.id to frame.toPoint(it.position) }
        var extent = 120.0
        for (p in pts.values) extent = max(extent, max(abs(p.x), abs(p.y)))
        val bezel = min(size.width, size.height) / 2f - 14.dp.toPx()
        val scale = (bezel * 0.80f) / extent
        val c = Offset(size.width / 2f, size.height / 2f)
        fun screen(p: Point) = Offset(c.x + (p.x * scale).toFloat(), c.y - (p.y * scale).toFloat())

        // Range rings
        val step = RING_STEPS.firstOrNull { it >= extent / 3 } ?: RING_STEPS.last()
        var ring = step
        var n = 0
        while (ring <= extent * 1.25 && n < 4) {
            val radius = (ring * scale).toFloat()
            drawCircle(ArmorColors.Cyan.copy(alpha = max(0.1f, 0.3f - n * 0.06f)), radius, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f))))
            drawText(measurer, fmtDistance(ring).uppercase().replace(" ", ""), Offset(c.x + radius * 0.707f + 3.dp.toPx(), c.y - radius * 0.707f - 11.dp.toPx()), faint)
            ring += step; n++
        }

        // Compass bezel
        for (i in 0 until 72) {
            val a = i * 5.0 * PI / 180.0
            val major = i % 6 == 0
            val r1 = bezel
            val r2 = bezel - (if (major) 9 else 5).dp.toPx()
            val d = Offset(sin(a).toFloat(), -cos(a).toFloat())
            drawLine(ArmorColors.Cyan.copy(alpha = if (major) 0.75f else 0.3f), c + d * r1, c + d * r2, strokeWidth = 1.dp.toPx())
        }
        for ((txt, dx, dy) in listOf(Triple("N", 0f, -1f), Triple("E", 1f, 0f), Triple("S", 0f, 1f), Triple("W", -1f, 0f))) {
            val m = measurer.measure(txt, hud)
            val at = c + Offset(dx, dy) * (bezel - 20.dp.toPx())
            drawText(measurer, txt, Offset(at.x - m.size.width / 2f, at.y - m.size.height / 2f), if (txt == "N") hud else faint)
        }

        // Radar sweep: a fading wedge trailing a leading edge.
        val topLeft = Offset(c.x - bezel, c.y - bezel)
        val box = Size(bezel * 2, bezel * 2)
        for (k in 0 until 14) {
            drawArc(ArmorColors.Cyan.copy(alpha = 0.10f * (1f - k / 14f)), sweep - k * 5f - 95f, 5f, true, topLeft, box)
        }

        // Your recent route, fading in towards the present.
        if (trail.size > 1) {
            val tp = trail.map { screen(frame.toPoint(it)) }
            for (i in 1 until tp.size) {
                val a = i.toFloat() / tp.size
                drawLine(ArmorColors.Cyan.copy(alpha = a * 0.22f), tp[i - 1], tp[i], strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
                drawLine(ArmorColors.Cyan.copy(alpha = a), tp[i - 1], tp[i], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        // Group boundaries as glowing holograms; the main group is orange, any other cluster red.
        snapshot.clusters.forEachIndexed { i, cluster ->
            val tint = if (i == snapshot.mainClusterIndex) ArmorColors.Orange else ArmorColors.Red
            val hull = cluster.hull.map { screen(frame.toPoint(it)) }
            val path = Path().apply {
                hull.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                if (hull.size > 2) close()
            }
            val pad = 24.dp.toPx()
            if (hull.size > 2) drawPath(path, tint.copy(alpha = 0.12f), style = Fill)
            if (hull.size == 1) drawCircle(tint.copy(alpha = 0.14f), pad, hull[0])
            else drawPath(path, tint.copy(alpha = 0.12f), style = Stroke(pad * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, tint.copy(alpha = 0.9f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
        }

        // Riders (you last, so you are never hidden).
        for (rider in riders.sortedBy { if (it.isSelf) 1 else 0 }) {
            drawRider(rider, screen(pts.getValue(rider.id)), measurer, label)
        }

        drawText(measurer, "[ TACTICAL MAP ]", Offset(12.dp.toPx(), 10.dp.toPx()), hud)
        val range = "RANGE ${fmtDistance(step * 3).uppercase()}"
        val rm = measurer.measure(range, faint)
        drawText(measurer, range, Offset(size.width - rm.size.width - 12.dp.toPx(), 11.dp.toPx()), faint)
    }
}

private fun hexPath(at: Offset, r: Float): Path = Path().apply {
    for (k in 0 until 6) {
        val a = (-90.0 + 60.0 * k) * PI / 180.0
        val p = Offset(at.x + r * cos(a).toFloat(), at.y + r * sin(a).toFloat())
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun DrawScope.drawRider(rider: RiderView, at: Offset, measurer: TextMeasurer, label: TextStyle) {
    val r = 13.dp.toPx()
    val alarm = rider.safety == SafetyCode.SOS || rider.safety == SafetyCode.CRASH
    val warn = rider.safety == SafetyCode.ALERTED || rider.safety == SafetyCode.CHECKING
    val fill = when {
        rider.isStale -> ArmorColors.Stale
        alarm -> ArmorColors.Red
        warn -> ArmorColors.Amber
        rider.isSelf -> Color(0xFFEAF6FF)
        else -> ArmorColors.Orange
    }

    // Heading cone: where the rider is pointed, fading out ahead of them.
    val heading = rider.headingDeg
    if (heading != null && rider.speedMps > 1.0 && !rider.isStale) {
        rotate(heading.toFloat(), at) {
            val len = 58.dp.toPx()
            val w = 22.dp.toPx()
            val cone = Path().apply {
                moveTo(at.x, at.y); lineTo(at.x - w, at.y - len); lineTo(at.x + w, at.y - len); close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(fill.copy(alpha = 0f), fill.copy(alpha = 0.5f)), startY = at.y - len, endY = at.y))
        }
    }

    if (rider.isSelf) {
        drawCircle(ArmorColors.Cyan.copy(alpha = 0.12f), r * 2.1f, at)
        drawCircle(ArmorColors.Cyan.copy(alpha = 0.8f), r * 1.55f, at, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
    }
    if (alarm) drawCircle(ArmorColors.Red.copy(alpha = 0.25f), r * 2.4f, at)
    drawPath(hexPath(at, r + 6.dp.toPx()), fill.copy(alpha = 0.22f))
    drawPath(hexPath(at, r), fill)
    if (rider.isOutlier && !rider.isStale) drawPath(hexPath(at, r + 4.dp.toPx()), ArmorColors.Red, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

    val initial = if (rider.isSelf) "Y" else rider.name.firstOrNull()?.uppercase() ?: "?"
    val initialStyle = TextStyle(color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
    val im = measurer.measure(initial, initialStyle)
    drawText(measurer, initial, Offset(at.x - im.size.width / 2f, at.y - im.size.height / 2f), initialStyle)

    val text = buildString {
        append(if (rider.isSelf) "YOU" else rider.name)
        rider.distanceFromSelfM?.let { if (rider.isOutlier || rider.isStale) append(" · ${fmtDistance(it)}") }
        if (rider.isStale) append(" (last seen)")
    }
    val m = measurer.measure(text, label)
    val padX = 6.dp.toPx()
    val padY = 1.5.dp.toPx()
    val top = at.y + r + 7.dp.toPx()
    drawRoundRect(
        Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(at.x - m.size.width / 2f - padX, top),
        size = Size(m.size.width + padX * 2, m.size.height + padY * 2),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
    )
    drawText(measurer, text, Offset(at.x - m.size.width / 2f, top + padY), label)
}
