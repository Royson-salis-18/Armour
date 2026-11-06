package com.armor.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/*
 * The "instrument panel" layer of ARMOR's look: a dotted drafting grid behind every screen, graphite glass cards, and
 * orange used like a single warning light: glowing connector rails between steps, one solid accent card per screen,
 * one highlighted bar in a chart. Everything else stays grey and quiet.
 */

/** A faint grid of dots, like drafting paper, fading out toward the bottom of the screen. */
fun Modifier.dotGrid(color: Color = Color.White, alpha: Float = 0.08f, step: Dp = 22.dp): Modifier = drawWithCache {
    val s = step.toPx()
    val rows = ArrayList<Pair<Float, List<Offset>>>()
    var y = s / 2
    while (y < size.height) {
        val k = (1f - y / (size.height * 0.85f)).coerceIn(0f, 1f)
        if (k > 0f) {
            val row = ArrayList<Offset>()
            var x = s / 2
            while (x < size.width) { row += Offset(x, y); x += s }
            rows += alpha * k to row
        }
        y += s
    }
    onDrawBehind {
        for ((a, row) in rows) drawPoints(row, PointMode.Points, color.copy(alpha = a), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** The one loud card on a screen: solid orange, dark text, a soft orange bloom beneath it. */
fun Modifier.accentPanel(radius: Dp = 22.dp, color: Color = ArmorColors.Orange): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    this.shadow(20.dp, shape, ambientColor = color, spotColor = color)
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFFFF8A3D), color, Color(0xFFE5520A)), start = Offset.Zero, end = Offset.Infinite))
        .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), endY = size.height * 0.5f)) }
}

/** Text colour on an [accentPanel]. */
val OnAccent = Color(0xFF1A0B00)

/** State of one stop on a [Rail]. */
enum class RailState { DONE, ACTIVE, TODO, WARN }

/**
 * A vertical connector with a node per row, like a route timeline: done and active stretches glow orange, the road
 * still ahead is a dashed grey line. [rows] are drawn beside it, one per [states] entry.
 */
@Composable
fun Rail(states: List<RailState>, modifier: Modifier = Modifier, nodeTop: Dp = 12.dp, gap: Dp = 14.dp, row: @Composable (Int) -> Unit) {
    Column(modifier) {
        states.forEachIndexed { i, st ->
            val prevLit = i > 0 && states[i - 1] == RailState.DONE
            val nextLit = st == RailState.DONE
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier.width(26.dp).fillMaxHeight().drawBehind {
                        val cx = size.width / 2
                        val ny = nodeTop.toPx()
                        fun seg(y0: Float, y1: Float, lit: Boolean) {
                            if (y1 <= y0) return
                            if (lit) {
                                drawLine(ArmorColors.Orange.copy(alpha = 0.18f), Offset(cx, y0), Offset(cx, y1), 6.dp.toPx())
                                drawLine(ArmorColors.Orange, Offset(cx, y0), Offset(cx, y1), 1.6.dp.toPx())
                            } else {
                                drawLine(Color.White.copy(alpha = 0.18f), Offset(cx, y0), Offset(cx, y1), 1.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                            }
                        }
                        if (i > 0) seg(0f, ny - 7.dp.toPx(), prevLit)
                        if (i < states.lastIndex) seg(ny + 7.dp.toPx(), size.height, nextLit)
                        val c = Offset(cx, ny)
                        when (st) {
                            RailState.DONE -> {
                                drawCircle(ArmorColors.Orange.copy(alpha = 0.22f), 9.dp.toPx(), c)
                                drawCircle(ArmorColors.Orange, 5.dp.toPx(), c)
                            }
                            RailState.ACTIVE -> {
                                drawCircle(Color.White.copy(alpha = 0.16f), 10.dp.toPx(), c)
                                drawCircle(Color.White, 5.dp.toPx(), c)
                            }
                            RailState.WARN -> {
                                drawCircle(ArmorColors.Red.copy(alpha = 0.2f), 9.dp.toPx(), c)
                                drawCircle(ArmorColors.Red, 5.dp.toPx(), c, style = Stroke(1.8.dp.toPx()))
                            }
                            RailState.TODO -> drawCircle(Color.White.copy(alpha = 0.35f), 4.5.dp.toPx(), c, style = Stroke(1.4.dp.toPx()))
                        }
                    },
                )
                Box(Modifier.weight(1f).padding(start = 10.dp, bottom = if (i < states.lastIndex) gap else 0.dp)) { row(i) }
            }
        }
    }
}

/** A row of bars rising to a peak, like a signal or level meter; [lit] of them (from the left) glow. */
@Composable
fun PeakBars(modifier: Modifier = Modifier, bars: Int = 13, lit: Float = 1f, color: Color = OnAccent, dim: Color = OnAccent.copy(alpha = 0.25f), live: Boolean = true) {
    val t by rememberInfiniteTransition(label = "bars").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier) {
        val w = size.width / bars
        for (i in 0 until bars) {
            val u = (i - (bars - 1) / 2f) / (bars / 2f)
            val wobble = if (live) 0.08f * sin((t * 2 * PI + i * 0.7).toFloat()) else 0f
            val hgt = (0.18f + 0.82f * exp(-u * u * 2.2f) + wobble).coerceIn(0.1f, 1f) * size.height
            val on = i < (bars * lit).toInt()
            drawRoundRect(if (on) color else dim, Offset(i * w + w * 0.28f, size.height - hgt), Size(w * 0.44f, hgt), CornerRadius(w))
        }
    }
}

/** Weekday-style bars with one highlighted in orange, the rest graphite (ride summaries, history). */
@Composable
fun HighlightBars(values: List<Float>, highlight: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = values.size.coerceAtLeast(1)
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(0.001f)
        val w = size.width / n
        values.forEachIndexed { i, v ->
            val h = (v / max).coerceIn(0.06f, 1f) * size.height
            val x = i * w + w * 0.16f
            val bw = w * 0.68f
            if (i == highlight) {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE2CC), ArmorColors.Orange, ArmorColors.Orange), startY = size.height - h, endY = size.height),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(8.dp.toPx()))
            } else {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A3B40), Color(0xFF232427)), startY = size.height - h, endY = size.height),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(8.dp.toPx()))
            }
        }
    }
}

/** A small floating card for the home hero: label, value, and whatever sits below. */
@Composable
private fun FloatCard(modifier: Modifier, light: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
            .background(if (light) Brush.verticalGradient(listOf(Color(0xFFE9E9EC), Color(0xFFCFCFD4))) else Brush.verticalGradient(listOf(Color(0xE6303034), Color(0xE61C1C1F))))
            .border(1.dp, Color.White.copy(alpha = if (light) 0.5f else 0.12f), shape)
            .padding(12.dp),
    ) { content() }
}

/**
 * The home screen's centrepiece: a tall card with the ARMOR mark and a road fading into orange light, with three small
 * cards floating over its edges showing what is ready. Laid out like a product shot, with the cards overlapping.
 */
@Composable
fun HomeHero(ridersLabel: String, contactsLabel: String, ready: Int, total: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(330.dp)) {
        // The centre card.
        val shape = RoundedCornerShape(26.dp)
        Box(
            Modifier.align(Alignment.Center).padding(horizontal = 44.dp).fillMaxWidth().height(250.dp)
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF26262A), Color(0xFF3A1D10), Color(0xFF8A3A12))))
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // A road running to the horizon, its centre line glowing.
                val w = size.width; val h = size.height
                val road = Path().apply { moveTo(w * 0.18f, h); lineTo(w * 0.47f, h * 0.48f); lineTo(w * 0.53f, h * 0.48f); lineTo(w * 0.82f, h); close() }
                drawPath(road, Brush.verticalGradient(listOf(Color(0x00111113), Color(0xCC111113)), startY = h * 0.48f, endY = h))
                drawLine(ArmorColors.Orange.copy(alpha = 0.25f), Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h), 7.dp.toPx())
                drawLine(Color(0xFFFFB27A), Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h), 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
                drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, h * 0.48f), Offset(w, h * 0.48f), 1.dp.toPx())
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ArmorLogo(64.dp)
                Txt("PROTECTED RIDES", Type.Eyebrow, Color.White.copy(alpha = 0.55f), Modifier.padding(top = 10.dp))
            }
        }
        // Floating cards over the edges.
        FloatCard(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).width(132.dp).rotate(2f), light = true) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Group", Type.Label, Color(0xFF151517), Modifier.weight(1f))
                    Box(Modifier.clip(CircleShape).background(ArmorColors.Orange).padding(horizontal = 7.dp, vertical = 1.dp)) { Txt("LIVE", Type.Meta.copy(fontSize = 9.sp), OnAccent) }
                }
                Txt(ridersLabel, Type.Meta, Color(0xFF4A4B50), Modifier.padding(top = 4.dp))
            }
        }
        FloatCard(Modifier.align(Alignment.CenterStart).padding(top = 40.dp).width(136.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Crash watch", Type.Label, ArmorColors.OnBg, Modifier.weight(1f))
                    Txt("→", Type.Label, ArmorColors.Muted)
                }
                Txt("SOS", Type.Hero.copy(fontSize = 34.sp, lineHeight = 36.sp), ArmorColors.OnBg, Modifier.padding(top = 4.dp).fillMaxWidth(), align = TextAlign.Center)
                Txt(contactsLabel, Type.Meta, ArmorColors.Muted)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).offset(y = (-4).dp).padding(end = 10.dp).width(150.dp).accentPanel(20.dp).padding(12.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("Readiness", Type.Label, OnAccent, Modifier.weight(1f))
                    Txt("→", Type.Label, OnAccent)
                }
                PeakBars(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(34.dp), lit = if (total == 0) 1f else ready / total.toFloat())
                Txt("$ready of $total ready", Type.Meta, OnAccent.copy(alpha = 0.75f))
            }
        }
    }
}
