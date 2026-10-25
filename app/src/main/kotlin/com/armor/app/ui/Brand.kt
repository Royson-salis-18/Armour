package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.core.engine.RideSnapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** The ARMOR mark: a shield with two forward chevrons. */
@Composable
fun ArmorLogo(height: Dp, color: Color = ArmorColors.Orange, chevron: Color = ArmorColors.Bg) {
    val shield = remember { svgPath(Art.LOGO_SHIELD) }
    val chevrons = remember { Art.LOGO_CHEVRONS.map(::svgPath) }
    Canvas(Modifier.size(height * 24f / 28f, height)) {
        val s = size.height / 28f
        scale(s, s, Offset.Zero) {
            drawPath(shield, color)
            chevrons.forEach { drawPath(it, chevron, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

@Composable
fun Wordmark(height: Dp = 22.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ArmorLogo(height)
        Text("ARMOR", color = ArmorColors.OnBg, style = Type.Title.copy(fontSize = (height.value * 0.95f).sp, letterSpacing = 3.sp, fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * A rider on a motorcycle in rim-lit silhouette: the start screen's hero. Drawn in two passes — orange outlines
 * first, then dark fills over them — so only the outer silhouette picks up the orange edge light.
 */
@Composable
fun BikeHero(modifier: Modifier = Modifier) {
    val parts = remember { Art.BIKE_PARTS.map { it to svgPath(it.d) } }
    Canvas(modifier) {
        val s = min(size.width / 240f, size.height / 130f)
        val ox = (size.width - 240f * s) / 2f
        val oy = (size.height - 130f * s) / 2f

        // Warm pool of light on the ground.
        drawOval(
            Brush.radialGradient(listOf(ArmorColors.Orange.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width / 2f, oy + 116f * s), radius = 120f * s),
            topLeft = Offset(size.width / 2f - 130f * s, oy + 104f * s), size = Size(260f * s, 26f * s),
        )
        translate(ox, oy) {
            scale(s, s, Offset.Zero) {
                Art.BIKE_WHEELS.forEach { (x, y, r) ->
                    drawCircle(Color(0xFF1B1C20), r, Offset(x, y), style = Stroke(9f))
                    drawCircle(ArmorColors.Orange.copy(alpha = 0.55f), r - 4.5f, Offset(x, y), style = Stroke(1.2f))
                    drawCircle(Color(0xFF2A2B30), 6f, Offset(x, y))
                }
                parts.forEach { (p, path) ->
                    if (p.fill) drawPath(path, ArmorColors.Orange, style = Stroke(2.6f, join = StrokeJoin.Round))
                    else drawPath(path, ArmorColors.Orange, style = Stroke(p.w + 2.6f, cap = StrokeCap.Round))
                }
                val fillBrush = Brush.verticalGradient(listOf(Color(0xFF2A2B30), Color(0xFF131417)), startY = 20f, endY = 110f)
                parts.forEach { (p, path) ->
                    if (p.fill) drawPath(path, fillBrush)
                    else drawPath(path, Color(0xFF1B1C20), style = Stroke(p.w, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/**
 * A tachometer-style dial: tick marks every 5 km/h around a 270° sweep, lit orange up to your speed. The last stretch
 * (100 km/h and over) is the red zone.
 */
@Composable
fun SpeedDial(kmh: Double, size: Dp = 112.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            for (i in 0..24) {
                val value = i * 5
                val a = (135.0 + i * (270.0 / 24.0)) * PI / 180.0
                val major = i % 4 == 0
                val lit = value <= kmh
                val color = when {
                    !lit -> Color.White.copy(alpha = 0.14f)
                    value >= 100 -> ArmorColors.Red
                    else -> ArmorColors.Orange
                }
                val inner = r * (if (major) 0.80f else 0.87f)
                val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(color, c + d * inner, c + d * (r * 0.98f), strokeWidth = (if (major) 3f else 2f).dp.toPx(), cap = StrokeCap.Round)
            }
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kmh.roundToInt().toString(), color = ArmorColors.OnBg, style = Type.Speed.copy(fontSize = (size.value * 0.40f).sp))
            Text("KM/H", color = ArmorColors.Dim, style = Type.Eyebrow.copy(fontSize = 11.sp, letterSpacing = 1.5.sp))
        }
    }
}

/**
 * The convoy: every rider placed along the direction of travel, so you can see at a glance who is leading, who is
 * sweeping, and exactly where the gap opened. Riders who have fallen away get a red dashed link back to the group.
 */
@Composable
fun ConvoyStrip(s: RideSnapshot, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val name = TextStyle(color = ArmorColors.OnBg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val alert = TextStyle(color = ArmorColors.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val dim = TextStyle(color = ArmorColors.Dim, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
    val initialStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val self = s.self
        val heading = Math.toRadians(self?.headingDeg ?: 0.0)
        // Position along the line of travel: positive is ahead of you.
        class Item(val id: String, val label: String, val initial: String, val axis: Double, val isSelf: Boolean, val outlier: Boolean, val stale: Boolean, val dist: Double?)
        val items = ArrayList<Item>()
        if (self != null) items += Item(self.id, "You", "Y", 0.0, true, self.isOutlier, false, null)
        for (r in s.riders) if (!r.isSelf) {
            val d = r.distanceFromSelfM ?: 0.0
            val b = Math.toRadians(r.bearingFromSelfDeg ?: 0.0)
            items += Item(r.id, r.name.take(6), r.name.firstOrNull()?.uppercase() ?: "?", d * cos(b - heading), false, r.isOutlier, r.isStale, d)
        }
        if (items.isEmpty()) return@Canvas

        val pad = 30.dp.toPx()
        val minA = items.minOf { it.axis }
        val maxA = items.maxOf { it.axis }
        val span = max(maxA - minA, 160.0)
        val usable = size.width - pad * 2
        val trackY = 34.dp.toPx()
        val gap = 36.dp.toPx()
        val xs = items.map { pad + ((it.axis - minA) / span * usable).toFloat() }.toMutableList()
        val order = items.indices.sortedBy { xs[it] }
        for (k in 1 until order.size) if (xs[order[k]] - xs[order[k - 1]] < gap) xs[order[k]] = xs[order[k - 1]] + gap
        val over = xs.max() - (size.width - pad)
        if (over > 0) for (i in xs.indices) xs[i] -= over

        // Road line
        drawLine(ArmorColors.S3, Offset(pad - 14.dp.toPx(), trackY), Offset(size.width - pad + 14.dp.toPx(), trackY), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        // The formation: the stretch between the leading and trailing rider who are with the group.
        val inGroup = items.indices.filter { !items[it].outlier && !items[it].stale }
        if (inGroup.size > 1) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(xs[inGroup.minBy { xs[it] }], trackY), Offset(xs[inGroup.maxBy { xs[it] }], trackY), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
        // Dashed red link from each stray rider to the nearest rider in the group.
        for (i in items.indices) if (items[i].outlier && !items[i].isSelf || (items[i].isSelf && items[i].outlier)) {
            val nearest = inGroup.filter { it != i }.minByOrNull { abs(xs[it] - xs[i]) } ?: continue
            drawLine(ArmorColors.Red, Offset(xs[i], trackY), Offset(xs[nearest], trackY), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f)))
        }

        for (i in items.indices) {
            val it = items[i]
            val at = Offset(xs[i], trackY)
            val rr = 14.dp.toPx()
            if (it.isSelf) drawCircle(ArmorColors.Orange.copy(alpha = 0.22f), rr * 1.7f, at)
            drawCircle(Color.White, rr + 2.dp.toPx(), at)
            drawCircle(if (it.isSelf) ArmorColors.Orange else if (it.stale) ArmorColors.Dim else ArmorColors.riderColor(it.id), rr, at)
            if (it.outlier && !it.isSelf) drawCircle(ArmorColors.Red, rr + 4.dp.toPx(), at, style = Stroke(2.dp.toPx()))
            val im = measurer.measure(it.initial, initialStyle)
            drawText(measurer, it.initial, Offset(at.x - im.size.width / 2f, at.y - im.size.height / 2f), initialStyle)
            val txt = if (it.outlier && it.dist != null) "${it.label} · ${fmtDistance(it.dist)}" else it.label
            val m = measurer.measure(txt, if (it.outlier && !it.isSelf) alert else name)
            drawText(measurer, txt, Offset((at.x - m.size.width / 2f).coerceIn(0f, size.width - m.size.width), trackY + rr + 8.dp.toPx()), if (it.outlier && !it.isSelf) alert else name)
        }
        val left = measurer.measure("BEHIND", dim)
        drawText(measurer, "BEHIND", Offset(0f, size.height - left.size.height), dim)
        val right = measurer.measure("AHEAD →", dim)
        drawText(measurer, "AHEAD →", Offset(size.width - right.size.width, size.height - right.size.height), dim)
    }
}

/**
 * Slide-to-start: the thumb must be dragged most of the way across, so a ride can't be started (or a pocket can't
 * start one) by a stray tap. Releasing early snaps back.
 */
@Composable
fun SlideToStart(label: String, onComplete: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(ArmorColors.Orange)) {
        val thumb = 52.dp
        val inset = 6.dp
        val travel = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth - thumb - inset * 2).toPx() }
        var x by remember { mutableFloatStateOf(0f) }
        val progress = if (travel > 0f) (x / travel).coerceIn(0f, 1f) else 0f

        Text(label, Modifier.align(Alignment.Center).padding(start = 36.dp).alpha(1f - progress), color = Color(0xFF1A0B00), style = Type.Title.copy(fontSize = 22.sp, letterSpacing = 1.sp))
        Text("›››", Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).alpha(0.65f * (1f - progress)), color = Color(0xFF1A0B00), style = Type.Title.copy(fontSize = 26.sp))
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((x + inset.toPx()).roundToInt(), 0) }
                .size(thumb).clip(CircleShape).background(Color(0xFF1A0B00))
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (x > travel * 0.85f) onComplete(); x = 0f },
                        onDragCancel = { x = 0f },
                        onHorizontalDrag = { change, dx -> change.consume(); x = (x + dx).coerceIn(0f, travel) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) { ArmorLogo(26.dp) }
    }
}

/** A soft pool of coloured light falling from the top of the screen: the ambient glow behind every ARMOR screen. */
fun Modifier.warmGlow(color: Color = ArmorColors.Orange, strength: Float = 0.30f): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.35f), Color.Transparent),
            center = Offset(size.width * 0.5f, -size.height * 0.04f), radius = size.width * 1.15f,
        ),
    )
}

/**
 * Concentric rings of dots that thin out toward the edge, like a sound-wave or radar bloom. Purely decorative: it
 * gives the start screen and the countdowns a focal point.
 */
@Composable
fun DotOrb(modifier: Modifier = Modifier, color: Color = ArmorColors.Orange) {
    Canvas(modifier) {
        val c = center
        val maxR = size.minDimension / 2f
        for (ring in 1..9) {
            val r = maxR * ring / 9f
            val count = (ring * 11).coerceAtMost(90)
            val alpha = 0.60f * (1f - (ring - 1) / 10f)
            for (k in 0 until count) {
                val a = 2.0 * PI * k / count + ring * 0.37
                // Gentle radial wobble so the rings read as a living waveform rather than perfect circles.
                val rr = r * (1f + 0.07f * sin(a * 3 + ring).toFloat())
                drawCircle(color.copy(alpha = alpha), 1.1.dp.toPx(), c + Offset(cos(a).toFloat(), sin(a).toFloat()) * rr)
            }
        }
    }
}

/** Floating glass pill holding the main tabs, with the active one lit orange. */
@Composable
fun FloatingNav(labels: List<String>, icons: List<androidx.compose.ui.graphics.vector.ImageVector>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier.padding(horizontal = 28.dp, vertical = 14.dp).fillMaxWidth().height(64.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF01F2024), Color(0xF0141517))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (on) Brush.verticalGradient(listOf(Color(0xFFFF8A3D), ArmorColors.Orange)) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickableNoRipple { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(icons[i], contentDescription = label, tint = if (on) Color(0xFF1A0B00) else ArmorColors.Muted, modifier = Modifier.size(24.dp))
            }
        }
    }
}
