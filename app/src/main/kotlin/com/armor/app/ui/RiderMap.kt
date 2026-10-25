package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.core.engine.RideSnapshot
import com.armor.core.engine.RiderView
import com.armor.core.geo.LocalFrame
import com.armor.core.geo.Point
import com.armor.core.model.SafetyCode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val RING_STEPS = doubleArrayOf(50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0, 100_000.0)

/**
 * An offline "radar" view: north-up, centred on the rider, auto-scaled to keep everyone in frame. It draws no
 * map tiles on purpose — it must work with no internet and no API key — so it shows relative geometry
 * (who is where, which riders form the group, who has fallen out of it) rather than roads.
 */
@Composable
fun RiderMap(snapshot: RideSnapshot, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = ArmorColors.OnBg, fontSize = 11.sp)
    val faint = TextStyle(color = ArmorColors.Muted, fontSize = 10.sp)

    Canvas(modifier) {
        drawRect(ArmorColors.MapBg)
        val riders = snapshot.riders
        if (riders.isEmpty()) {
            val msg = if (snapshot.gpsLost) "Waiting for GPS…" else "No riders yet"
            val m = measurer.measure(msg, label)
            drawText(measurer, msg, Offset((size.width - m.size.width) / 2f, (size.height - m.size.height) / 2f), label)
            return@Canvas
        }

        val frame = LocalFrame((snapshot.self ?: riders.first()).position)
        val pts = riders.associate { it.id to frame.toPoint(it.position) }
        var extent = 120.0
        for (p in pts.values) extent = max(extent, max(abs(p.x), abs(p.y)))
        val half = min(size.width, size.height) / 2f * 0.78f
        val scale = half / extent
        val c = Offset(size.width / 2f, size.height / 2f)
        fun screen(p: Point) = Offset(c.x + (p.x * scale).toFloat(), c.y - (p.y * scale).toFloat())

        // Range rings
        val step = RING_STEPS.firstOrNull { it >= extent / 3 } ?: RING_STEPS.last()
        var r = step
        while (r <= extent * 1.25) {
            val radius = (r * scale).toFloat()
            drawCircle(ArmorColors.Grid, radius, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))))
            drawText(measurer, fmtDistance(r), Offset(c.x + 4.dp.toPx(), c.y - radius - 12.dp.toPx()), faint)
            r += step
        }
        drawCircle(Color.Black.copy(alpha = 0.4f), 14.dp.toPx(), Offset(size.width - 26.dp.toPx(), 26.dp.toPx()))
        drawText(measurer, "N", Offset(size.width - 26.dp.toPx() - 4.dp.toPx(), 18.dp.toPx()), label.copy(color = ArmorColors.Orange))

        // Group boundaries: a thick round-joined stroke around the hull inflates it into a soft blob.
        snapshot.clusters.forEachIndexed { i, cluster ->
            val main = i == snapshot.mainClusterIndex
            val tint = if (main) ArmorColors.Orange else ArmorColors.Red
            val hull = cluster.hull.map { screen(frame.toPoint(it)) }
            val path = Path().apply {
                hull.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                if (hull.size > 2) close()
            }
            val pad = 26.dp.toPx()
            if (hull.size > 2) drawPath(path, tint.copy(alpha = 0.12f), style = Fill)
            if (hull.size == 1) drawCircle(tint.copy(alpha = 0.14f), pad, hull[0]) else {
                drawPath(path, tint.copy(alpha = 0.14f), style = Stroke(pad * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawPath(path, tint.copy(alpha = if (main) 0.8f else 0.5f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }

        // Riders
        for (rider in riders.sortedBy { if (it.isSelf) 1 else 0 }) {
            drawRider(rider, screen(pts.getValue(rider.id)), measurer, label)
        }
    }
}

private fun DrawScope.drawRider(rider: RiderView, at: Offset, measurer: TextMeasurer, label: TextStyle) {
    val radius = 9.dp.toPx()
    val alarm = rider.safety == SafetyCode.SOS || rider.safety == SafetyCode.CRASH
    val warn = rider.safety == SafetyCode.ALERTED || rider.safety == SafetyCode.CHECKING
    val fill = when {
        rider.isStale -> ArmorColors.Stale
        alarm -> ArmorColors.Red
        warn -> ArmorColors.Amber
        rider.isSelf -> Color.White
        else -> ArmorColors.Orange
    }

    if (alarm) drawCircle(ArmorColors.Red.copy(alpha = 0.25f), radius * 2.2f, at)
    drawCircle(fill, radius, at)
    if (rider.isSelf) drawCircle(ArmorColors.Orange, radius + 3.dp.toPx(), at, style = Stroke(2.dp.toPx()))
    if (rider.isOutlier && !rider.isStale) drawCircle(ArmorColors.Red, radius + 3.dp.toPx(), at, style = Stroke(2.dp.toPx()))

    val heading = rider.headingDeg
    if (heading != null && rider.speedMps > 1.0 && !rider.isStale) {
        rotate(heading.toFloat(), at) {
            val tip = Path().apply {
                moveTo(at.x, at.y - radius - 9.dp.toPx())
                lineTo(at.x - 4.dp.toPx(), at.y - radius - 2.dp.toPx())
                lineTo(at.x + 4.dp.toPx(), at.y - radius - 2.dp.toPx())
                close()
            }
            drawPath(tip, fill)
        }
    }

    val initial = (if (rider.isSelf) "Y" else rider.name.firstOrNull()?.uppercase() ?: "?")
    val initialStyle = TextStyle(color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    val im = measurer.measure(initial, initialStyle)
    drawText(measurer, initial, Offset(at.x - im.size.width / 2f, at.y - im.size.height / 2f), initialStyle)

    val text = buildString {
        append(if (rider.isSelf) "You" else rider.name)
        rider.distanceFromSelfM?.let { if (rider.isOutlier || rider.isStale) append(" · ${fmtDistance(it)}") }
        if (rider.isStale) append(" (last seen)")
    }
    val m = measurer.measure(text, label)
    val padX = 7.dp.toPx()
    val padY = 2.dp.toPx()
    val top = at.y + radius + 5.dp.toPx()
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(at.x - m.size.width / 2f - padX, top),
        size = Size(m.size.width + padX * 2, m.size.height + padY * 2),
        cornerRadius = CornerRadius(50f, 50f),
    )
    drawText(measurer, text, Offset(at.x - m.size.width / 2f, top + padY), label)
}
