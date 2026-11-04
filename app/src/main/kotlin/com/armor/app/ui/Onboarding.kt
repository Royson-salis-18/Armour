package com.armor.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Whether the walkthrough is on screen. Opened automatically on first launch and from Settings > How ARMOR works. */
object Tour {
    val show = MutableStateFlow(false)
}

private val red = ArmorColors.Red
private val amber = ArmorColors.Amber
private val green = ArmorColors.Green
private val orange = ArmorColors.Orange
private val well = Color(0xFF0C0C0E)
private val road = Color(0xFF2B2D33)
private val dim = Color(0xFF8B8E97)
private val faint = Color(0xFF3A3C43)

// ---- the story format -------------------------------------------------------------------------

/** One moment in a situation: what is happening ([risk]) or what ARMOR does about it. */
private class Beat(val at: Float, val text: String, val risk: Boolean)

/**
 * A real situation on a ride, played as a short looping film: what goes wrong, then what ARMOR does.
 * [overlay] adds the cards a rider would actually see (alerts, texts) on top of the drawing.
 */
private class Scene(
    val eyebrow: String,
    val title: String,
    val durationMs: Int,
    val beats: List<Beat>,
    val helps: List<String>,
    val height: Dp = 230.dp,
    val draw: DrawScope.(Float) -> Unit,
    val overlay: @Composable BoxScope.(Float) -> Unit = {},
)

private fun ease(a: Float, b: Float, t: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun pulse(t: Float, speed: Float = 1f): Float = (t * speed) % 1f

/** A curvy road across the drawing. */
private fun DrawScope.roadPath(): Path {
    val w = size.width; val h = size.height
    return Path().apply { moveTo(0.04f * w, 0.70f * h); cubicTo(0.30f * w, 0.18f * h, 0.62f * w, 0.95f * h, 0.96f * w, 0.30f * h) }
}

private fun Path.at(u: Float): Offset {
    val m = PathMeasure(); m.setPath(this, false)
    return m.getPosition(m.length * u.coerceIn(0f, 1f))
}

private fun DrawScope.rider(at: Offset, color: Color, ring: Boolean = false, t: Float = 0f) {
    if (ring) {
        val k = pulse(t * 7)
        drawCircle(color.copy(alpha = 0.55f * (1 - k)), 8.dp.toPx() + 20.dp.toPx() * k, at, style = Stroke(1.5.dp.toPx()))
    }
    drawCircle(color, 8.dp.toPx(), at)
    drawCircle(well, 2.5.dp.toPx(), at)
}

private fun DrawScope.countdown(at: Offset, k: Float, color: Color = amber) =
    drawArc(color, -90f, 360f * (1 - k), false, Offset(at.x - 20.dp.toPx(), at.y - 20.dp.toPx()), Size(40.dp.toPx(), 40.dp.toPx()), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

private fun DrawScope.envelope(at: Offset, color: Color, alpha: Float = 1f) {
    val w = 14.dp.toPx(); val h = 10.dp.toPx()
    drawRoundRect(color.copy(alpha = alpha), Offset(at.x - w / 2, at.y - h / 2), Size(w, h), CornerRadius(2.dp.toPx()))
    val flap = Path().apply { moveTo(at.x - w / 2, at.y - h / 2); lineTo(at.x, at.y + 1.dp.toPx()); lineTo(at.x + w / 2, at.y - h / 2) }
    drawPath(flap, well.copy(alpha = alpha), style = Stroke(1.2.dp.toPx()))
}

private fun DrawScope.ripples(c: Offset, t: Float, color: Color, max: Float) = listOf(0f, 0.5f).forEach { off ->
    val k = pulse(t * 4 + off)
    drawCircle(color.copy(alpha = 0.5f * (1 - k)), 10.dp.toPx() + max * k, c, style = Stroke(1.5.dp.toPx()))
}

/** A person on the contact list: lit green once reached. */
private fun DrawScope.person(c: Offset, lit: Boolean) {
    val col = if (lit) green else dim
    drawCircle(col, 4.dp.toPx(), Offset(c.x, c.y - 4.dp.toPx()))
    drawArc(col, 180f, 180f, true, Offset(c.x - 7.dp.toPx(), c.y + 1.dp.toPx()), Size(14.dp.toPx(), 12.dp.toPx()))
}

// ---- 1. a crash with nobody around ------------------------------------------------------------

private fun DrawScope.crash(t: Float) {
    val w = size.width; val h = size.height
    val top = 0.40f * h; val rh = 0.28f * h; val cy = top + rh / 2
    drawRect(road, Offset(0f, top), Size(w, rh))
    val hit = 0.28f
    // Lane markings slide past while riding and freeze when the bike goes down.
    val period = 0.14f * w
    var x = -((min(t, hit) / hit * 3f * period) % period)
    while (x < w) { drawRect(Color(0xFF4A4D55), Offset(x, cy - 1.dp.toPx()), Size(0.08f * w, 2.dp.toPx())); x += period }
    val ry = cy + rh * 0.24f
    val hitX = 0.42f * w; val stopX = 0.54f * w
    drawOval(Color(0xFF141518), Offset(hitX - 10.dp.toPx(), ry - 4.dp.toPx()), Size(20.dp.toPx(), 8.dp.toPx()))
    val rx = if (t < hit) 0.10f * w + (hitX - 0.10f * w) * (t / hit) else hitX + (stopX - hitX) * ease(hit, 0.36f, t)
    if (t > hit) drawLine(Color(0xFF5A5D66), Offset(hitX, ry), Offset(rx, ry), 3.dp.toPx(), StrokeCap.Round)
    if (t in hit..0.42f) {
        val k = ease(hit, 0.42f, t)
        repeat(8) { i ->
            val a = i * PI.toFloat() / 4
            val d = Offset(cos(a), sin(a))
            val r1 = 12.dp.toPx() + 16.dp.toPx() * k
            drawLine(red.copy(alpha = 1 - k), Offset(hitX, ry) + d * r1, Offset(hitX, ry) + d * (r1 + 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
    val col = when { t >= 0.74f -> red; t >= hit -> amber; else -> orange }
    rider(Offset(rx, ry), col, ring = t >= hit, t = t)
    if (t in 0.48f..0.74f) countdown(Offset(rx, ry), ease(0.48f, 0.74f, t))

    // The phone's motion sensor: calm, one hard spike, then nothing (the bike has stopped).
    val sTop = 0.78f * h; val sH = 0.16f * h; val base = sTop + sH / 2
    drawLine(faint, Offset(0.04f * w, base), Offset(0.96f * w, base), 1.dp.toPx())
    val n = 90
    val trace = Path()
    for (i in 0..n) {
        val u = i / n.toFloat()
        if (u > t) break
        var amp = sin(i * 1.7f) * 0.14f + sin(i * 0.63f) * 0.10f
        if (u > hit + 0.06f) amp *= 0.12f
        if (u in hit - 0.012f..hit + 0.022f) amp = if (i % 2 == 0) 1f else -0.85f
        val px = 0.04f * w + 0.92f * w * u; val py = base - amp * sH / 2
        if (i == 0) trace.moveTo(px, py) else trace.lineTo(px, py)
    }
    drawPath(trace, if (t > hit) red.copy(alpha = 0.85f) else dim, style = Stroke(1.5.dp.toPx()))

    if (t >= 0.74f) {
        ripples(Offset(rx, ry), t, red, 40.dp.toPx())
        val k = ease(0.74f, 1f, t)
        listOf(0.10f, 0.20f, 0.30f).forEachIndexed { i, y -> person(Offset(0.92f * w, y * h + 6.dp.toPx()), k > 0.15f + i * 0.28f) }
    }
}

@Composable
private fun BoxScope.crashCards(t: Float) {
    Pop(t in 0.48f..0.74f, Alignment.TopStart) {
        val left = (30 - ease(0.48f, 0.74f, t) * 30).toInt().coerceAtLeast(1)
        InfoCard("Are you okay?", "Sending SOS in $left s", amber, "I'm okay")
    }
    Pop(t >= 0.74f, Alignment.TopStart) {
        InfoCard("SOS texted to Dad, Mum, Arjun", if (t < 0.9f) "Calling Dad…" else "Dad answered", red)
    }
}

// ---- 2. left behind the group -----------------------------------------------------------------

private fun DrawScope.behind(t: Float) {
    val w = size.width; val h = size.height
    val p = roadPath()
    drawPath(p, road, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
    val stopAt = 0.30f; val askAt = 0.50f; val sosAt = 0.66f; val helpAt = 0.84f
    val uA = if (t < stopAt) 0.1f + t / stopAt * 0.4f else 0.5f
    val uC = 0.2f + t * 0.8f
    val uB = when { t < sosAt -> 0.05f + t / sosAt * 0.08f; t < helpAt -> 0.13f; else -> 0.13f + ease(helpAt, 0.97f, t) * 0.34f }
    val pa = p.at(uA)
    drawCircle(dim, 6.dp.toPx(), p.at(uC))
    drawCircle(dim, 6.dp.toPx(), p.at((uC - 0.14f).coerceIn(0f, 1f)))
    drawCircle(if (t >= helpAt) green else dim, 6.dp.toPx(), p.at(uB))
    // A bend that hides the stopped rider from the riders ahead.
    drawCircle(Color(0xFF1C2A1F), 26.dp.toPx(), Offset(0.47f * w, 0.30f * h))
    drawCircle(Color(0xFF1C2A1F), 18.dp.toPx(), Offset(0.56f * w, 0.36f * h))
    val col = when { t >= sosAt -> red; t >= stopAt -> amber; else -> orange }
    rider(pa, col, ring = t >= stopAt, t = t)
    if (t in askAt..sosAt) countdown(pa, ease(askAt, sosAt, t))
    if (t in sosAt..helpAt + 0.06f) {
        val k = ease(sosAt, helpAt, t)
        listOf(0.62f, 0.76f, 0.90f).forEachIndexed { i, y ->
            val to = Offset(0.93f * w, y * h)
            person(to, k > 0.85f)
            val e = ((k * 1.2f) - i * 0.12f).coerceIn(0f, 1f)
            if (e in 0.01f..0.99f) envelope(Offset(pa.x + (to.x - pa.x) * e, pa.y + (to.y - pa.y) * e), red)
        }
    }
}

@Composable
private fun BoxScope.behindCards(t: Float) {
    Pop(t in 0.50f..0.66f, Alignment.TopStart) { InfoCard("Rohan stopped, 400 m behind", "Asking: are you okay?", amber) }
    Pop(t in 0.66f..0.84f, Alignment.TopStart) { InfoCard("Group alerted", "SOS texts sent with the spot", red) }
    Pop(t >= 0.84f, Alignment.TopStart) { InfoCard("Arjun: on my way", "2 min away", green) }
}

// ---- 3. no network ----------------------------------------------------------------------------

private fun DrawScope.hills(t: Float) {
    val w = size.width; val h = size.height
    val far = Path().apply { moveTo(0f, 0.62f * h); lineTo(0.18f * w, 0.30f * h); lineTo(0.34f * w, 0.52f * h); lineTo(0.55f * w, 0.20f * h); lineTo(0.78f * w, 0.16f * h); lineTo(1f * w, 0.48f * h); lineTo(w, h); lineTo(0f, h); close() }
    drawPath(far, Color(0xFF17181C))
    val near = Path().apply { moveTo(0f, 0.80f * h); lineTo(0.25f * w, 0.55f * h); lineTo(0.48f * w, 0.74f * h); lineTo(0.70f * w, 0.50f * h); lineTo(w, 0.72f * h); lineTo(w, h); lineTo(0f, h); close() }
    drawPath(near, Color(0xFF1F2025))
    // A lone tower far away on the ridge.
    val tw = Offset(0.78f * w, 0.16f * h)
    drawLine(dim, tw, Offset(tw.x, tw.y - 18.dp.toPx()), 2.dp.toPx())
    val towerLit = t >= 0.78f
    listOf(1f, 2f).forEach { r -> drawArc(if (towerLit) green else faint, -150f, 120f, false, Offset(tw.x - 8.dp.toPx() * r, tw.y - 18.dp.toPx() - 8.dp.toPx() * r), Size(16.dp.toPx() * r, 16.dp.toPx() * r), style = Stroke(1.5.dp.toPx())) }
    val p = Path().apply { moveTo(0.02f * w, 0.92f * h); cubicTo(0.30f * w, 0.60f * h, 0.45f * w, 0.98f * h, 0.66f * w, 0.70f * h) }
    drawPath(p, road, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
    val stop = 0.45f
    val r = p.at(if (t < stop) t / stop * 0.8f else 0.8f)
    rider(r, if (t >= stop) red else orange, ring = t >= stop, t = t)
    // First text attempt fails halfway; the retry gets through to the tower, then to family.
    if (t in 0.55f..0.68f) {
        val k = ease(0.55f, 0.66f, t)
        val at = Offset(r.x + (tw.x - r.x) * k * 0.5f, r.y + (tw.y - r.y) * k * 0.5f)
        envelope(at, amber, 1f - ease(0.64f, 0.68f, t))
    }
    if (t in 0.70f..0.80f) { val k = ease(0.70f, 0.78f, t); envelope(Offset(r.x + (tw.x - r.x) * k, r.y + (tw.y - r.y) * k), orange) }
    val home = Offset(0.93f * w, 0.62f * h)
    if (t in 0.80f..0.90f) { val k = ease(0.80f, 0.88f, t); envelope(Offset(tw.x + (home.x - tw.x) * k, tw.y + (home.y - tw.y) * k), orange) }
    house(home, t >= 0.88f)
}

private fun DrawScope.house(c: Offset, lit: Boolean) {
    val s = 10.dp.toPx()
    val col = if (lit) green else dim
    val roof = Path().apply { moveTo(c.x - s, c.y); lineTo(c.x, c.y - s); lineTo(c.x + s, c.y); close() }
    drawPath(roof, col)
    drawRect(col, Offset(c.x - s * 0.7f, c.y), Size(s * 1.4f, s * 0.9f))
}

@Composable
private fun BoxScope.hillCards(t: Float) {
    Pop(true, Alignment.TopStart) { Chip("No internet · 1 bar", red) }
    Pop(t in 0.58f..0.72f, Alignment.BottomStart) { Chip("Text failed · retrying in 20 s", amber) }
    Pop(t >= 0.86f, Alignment.BottomStart) { Chip("SOS delivered with map link", green) }
}

// ---- 4. phone in the pocket -------------------------------------------------------------------

private fun DrawScope.pocket(t: Float) {
    val w = size.width; val h = size.height
    val cx = 0.5f * w
    val ph = 0.86f * h; val pw = ph * 0.5f
    val top = 0.07f * h
    val alarm = t >= 0.40f
    drawRoundRect(Color(0xFF1A1B1F), Offset(cx - pw / 2, top), Size(pw, ph), CornerRadius(14.dp.toPx()))
    val screen = if (alarm) red.copy(alpha = 0.55f + 0.35f * (sin(t * 60f) * 0.5f + 0.5f)) else well
    drawRoundRect(screen, Offset(cx - pw / 2 + 4.dp.toPx(), top + 4.dp.toPx()), Size(pw - 8.dp.toPx(), ph - 8.dp.toPx()), CornerRadius(11.dp.toPx()))
    val ring = Offset(cx, top + 0.20f * h)
    if (!alarm) {
        drawCircle(dim, 6.dp.toPx(), Offset(ring.x, ring.y - 5.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        drawRoundRect(dim, Offset(ring.x - 8.dp.toPx(), ring.y - 2.dp.toPx()), Size(16.dp.toPx(), 12.dp.toPx()), CornerRadius(2.dp.toPx()))
    } else {
        drawCircle(Color.White.copy(alpha = 0.9f), 16.dp.toPx(), ring, style = Stroke(2.dp.toPx()))
        if (t >= 0.55f) drawArc(Color.White, -90f, 360f * (1 - ease(0.55f, 1f, t)), false, Offset(ring.x - 24.dp.toPx(), ring.y - 24.dp.toPx()), Size(48.dp.toPx(), 48.dp.toPx()), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
    // ARMOR's dot in the status bar: on for the whole ride, screen off or not.
    val k = sin(t * 30f) * 0.5f + 0.5f
    drawCircle(orange.copy(alpha = 0.5f + 0.5f * k), 3.dp.toPx(), Offset(cx - pw / 2 + 14.dp.toPx(), top + 14.dp.toPx()))
    if (alarm) {
        listOf(-1f, 1f).forEach { side ->
            val x0 = cx + side * (pw / 2 + 10.dp.toPx())
            val z = Path().apply {
                moveTo(x0, top + 0.08f * h)
                for (i in 1..6) lineTo(x0 + side * (if (i % 2 == 0) 0f else 6.dp.toPx()) + side * sin(t * 80f) * 1.5f, top + 0.08f * h + i * 0.045f * h)
            }
            drawPath(z, red, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (t >= 0.55f) listOf(-1f, 1f).forEach { side ->
            listOf(0f, 0.33f, 0.66f).forEach { off ->
                val q = pulse(t * 3 + off)
                val r = 20.dp.toPx() + 60.dp.toPx() * q
                val c = Offset(cx + side * pw / 2, top + 0.18f * h)
                drawArc(red.copy(alpha = 0.6f * (1 - q)), if (side < 0) 150f else -30f, 60f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(2.dp.toPx()))
            }
        }
    }
    // Denim pocket over the lower half of the phone.
    val pocketTop = 0.44f * h
    drawRoundRect(Color(0xFF1D2738), Offset(cx - pw * 1.1f, pocketTop), Size(pw * 2.2f, h - pocketTop + 20.dp.toPx()), CornerRadius(18.dp.toPx()))
    drawRoundRect(Color(0xFF5B6E8C), Offset(cx - pw * 1.1f + 6.dp.toPx(), pocketTop + 6.dp.toPx()), Size(pw * 2.2f - 12.dp.toPx(), h - pocketTop), CornerRadius(14.dp.toPx()),
        style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

@Composable
private fun BoxScope.pocketCards(t: Float) {
    Pop(t < 0.40f, Alignment.BottomStart) { Chip("Screen off · ARMOR still on", orange) }
    Pop(t >= 0.55f, Alignment.BottomStart) { Chip("Full volume · even on silent", red) }
}

// ---- 5. hands on the bars ---------------------------------------------------------------------

private fun DrawScope.voice(t: Float) {
    val w = size.width; val h = size.height
    val speaking = listOf(0.14f to 0.24f, 0.42f to 0.52f, 0.70f to 0.80f).any { (a, b) -> t in a..b }
    val n = 26
    val bw = w * 0.6f / n
    val base = h - 26.dp.toPx()
    for (i in 0 until n) {
        val amp = if (speaking) (sin(t * 90f + i * 0.9f) * 0.5f + 0.5f) * (0.4f + 0.6f * sin(i / n.toFloat() * PI.toFloat())) else 0.08f
        val bh = 4.dp.toPx() + 18.dp.toPx() * amp
        drawRoundRect(if (speaking) orange else faint, Offset(w * 0.2f + i * bw, base - bh / 2), Size(bw * 0.55f, bh), CornerRadius(bw))
    }
}

@Composable
private fun BoxScope.voiceCards(t: Float) {
    val lines = listOf(
        Triple(0.14f, "“Armor, fuel stop”", true), Triple(0.26f, "Fuel stop shared with the group.", false),
        Triple(0.42f, "“Armor, call Arjun”", true), Triple(0.54f, "Calling Arjun.", false),
        Triple(0.70f, "“Armor, I'm okay”", true), Triple(0.82f, "Glad you're okay. Check cancelled.", false),
    )
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { i, (at, text, mine) ->
            val until = lines.getOrNull(i + 4)?.first ?: 2f
            AnimatedVisibility(t >= at && t < until, Modifier.align(if (mine) Alignment.End else Alignment.Start), enter = fadeIn(tween(220)) + expandVertically(), exit = fadeOut(tween(160)) + shrinkVertically()) {
                Box(Modifier.widthIn(max = 230.dp).clip(RoundedCornerShape(14.dp)).background(if (mine) orange else Color(0xFF26282D)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Txt(text, Type.Label.copy(fontSize = 13.sp), if (mine) Color(0xFF1A0B00) else ArmorColors.OnBg)
                }
            }
        }
    }
}

// ---- 6. battery and running late --------------------------------------------------------------

private fun DrawScope.battery(t: Float) {
    val w = size.width; val h = size.height
    val bx = 0.07f * w; val by = 0.16f * h; val bw = 0.30f * w; val bh = 0.22f * h
    val level = 0.62f - 0.50f * ease(0f, 0.40f, t)
    val col = when { level < 0.16f -> red; level < 0.35f -> amber; else -> green }
    drawRoundRect(dim, Offset(bx, by), Size(bw, bh), CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
    drawRoundRect(dim, Offset(bx + bw, by + bh * 0.3f), Size(5.dp.toPx(), bh * 0.4f), CornerRadius(2.dp.toPx()))
    drawRoundRect(col, Offset(bx + 4.dp.toPx(), by + 4.dp.toPx()), Size((bw - 8.dp.toPx()) * level, bh - 8.dp.toPx()), CornerRadius(3.dp.toPx()))
    // The group below gets the low-battery note.
    listOf(0.10f, 0.22f, 0.34f).forEach { x ->
        val c = Offset(x * w, 0.56f * h)
        if (t in 0.40f..0.62f) ripples(c, t, orange, 14.dp.toPx())
        drawCircle(if (t >= 0.40f) orange else dim, 6.dp.toPx(), c)
    }
    // The clock: the hand sweeps past the expected arrival time.
    val c = Offset(0.72f * w, 0.36f * h); val r = 0.22f * h
    drawCircle(Color(0xFF1C1D21), r, c)
    drawCircle(faint, r, c, style = Stroke(2.dp.toPx()))
    drawLine(orange, Offset(c.x, c.y - r), Offset(c.x, c.y - r + 8.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    val a = ((-90f + (min(t, 0.75f) - 0.58f) * 300f) * PI / 180f).toFloat()
    drawLine(if (t >= 0.58f) red else ArmorColors.OnBg, c, Offset(c.x + cos(a) * r * 0.82f, c.y + sin(a) * r * 0.82f), 2.5.dp.toPx(), StrokeCap.Round)
    drawCircle(ArmorColors.OnBg, 3.dp.toPx(), c)
    val home = Offset(0.72f * w, 0.84f * h)
    if (t in 0.70f..0.82f) { val k = ease(0.70f, 0.80f, t); envelope(Offset(c.x, c.y + r + (home.y - c.y - r) * k), amber) }
    if (t >= 0.80f && t < 0.88f) ripples(home, t, amber, 16.dp.toPx())
    house(home, t >= 0.90f)
}

@Composable
private fun BoxScope.batteryCards(t: Float) {
    Pop(t in 0.40f..0.66f, Alignment.BottomStart) { Chip("Battery 15% · group told", amber) }
    Pop(t in 0.72f..0.90f, Alignment.BottomStart) { Chip("Late · family texted your location", amber) }
    Pop(t >= 0.90f, Alignment.BottomStart) { Chip("Reached home · family told", green) }
}

// ---- 7. stops that are not emergencies --------------------------------------------------------

private fun DrawScope.calm(t: Float) {
    val w = size.width; val h = size.height
    val top = 0.48f * h; val rh = 0.26f * h; val cy = top + rh / 2
    drawRect(road, Offset(0f, top), Size(w, rh))
    // Traffic light.
    val lx = 0.34f * w
    drawLine(faint, Offset(lx, top), Offset(lx, top - 0.16f * h), 3.dp.toPx())
    drawRoundRect(Color(0xFF16171A), Offset(lx - 8.dp.toPx(), top - 0.16f * h - 30.dp.toPx()), Size(16.dp.toPx(), 32.dp.toPx()), CornerRadius(4.dp.toPx()))
    val redOn = t in 0.08f..0.36f
    drawCircle(if (redOn) red else Color(0xFF3A1A1A), 4.5.dp.toPx(), Offset(lx, top - 0.16f * h - 22.dp.toPx()))
    drawCircle(if (!redOn) green else Color(0xFF16301E), 4.5.dp.toPx(), Offset(lx, top - 0.16f * h - 8.dp.toPx()))
    // Fuel pump.
    val fx = 0.80f * w
    drawRoundRect(Color(0xFF2A2C31), Offset(fx - 10.dp.toPx(), top - 34.dp.toPx()), Size(20.dp.toPx(), 34.dp.toPx()), CornerRadius(3.dp.toPx()))
    drawRect(orange.copy(alpha = 0.8f), Offset(fx - 6.dp.toPx(), top - 30.dp.toPx()), Size(12.dp.toPx(), 7.dp.toPx()))
    val hose = Path().apply { moveTo(fx + 10.dp.toPx(), top - 26.dp.toPx()); quadraticBezierTo(fx + 20.dp.toPx(), top - 10.dp.toPx(), fx + 14.dp.toPx(), top) }
    drawPath(hose, dim, style = Stroke(2.dp.toPx()))

    fun xAt(lead: Float): Float = when {
        t < 0.14f -> 0.05f * w + (0.29f * w - 0.05f * w) * (t / 0.14f)
        t < 0.36f -> 0.29f * w
        t < 0.52f -> 0.29f * w + (0.74f * w - 0.29f * w) * ease(0.36f, 0.52f, t)
        else -> 0.74f * w
    } - lead
    val ry = cy + rh * 0.2f
    rider(Offset(xAt(0f), ry), orange)
    drawCircle(dim, 6.dp.toPx(), Offset(xAt(0.07f * w), ry - rh * 0.36f))
    drawCircle(dim, 6.dp.toPx(), Offset(xAt(0.14f * w), ry))
    val stopped = t in 0.16f..0.36f || t >= 0.54f
    if (stopped) {
        val c = Offset(xAt(0f), ry - 22.dp.toPx())
        drawCircle(green, 8.dp.toPx(), c)
        val tick = Path().apply { moveTo(c.x - 4.dp.toPx(), c.y); lineTo(c.x - 1.dp.toPx(), c.y + 3.dp.toPx()); lineTo(c.x + 4.dp.toPx(), c.y - 3.dp.toPx()) }
        drawPath(tick, well, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
    if (t >= 0.58f) {
        val c = Offset(0.90f * w, 0.16f * h)
        drawCircle(faint, 14.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        drawArc(green, -90f, 360f * ease(0.58f, 1f, t), false, Offset(c.x - 14.dp.toPx(), c.y - 14.dp.toPx()), Size(28.dp.toPx(), 28.dp.toPx()), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BoxScope.calmCards(t: Float) {
    Pop(t < 0.12f, Alignment.TopStart) { Chip("Safety checks passed · ride on", green) }
    Pop(t in 0.18f..0.40f, Alignment.TopStart) { Chip("Stopped with the group · all normal", green) }
    Pop(t in 0.58f..0.86f, Alignment.TopStart) { Chip("Fuel stop · checks paused 15 min", orange) }
    Pop(t >= 0.86f, Alignment.TopStart) { Chip("No alarm. Nothing sent.", green) }
}

// ---- 8. what your family receives -------------------------------------------------------------

private fun DrawScope.mapPin(t: Float) {
    val w = size.width; val h = size.height
    var x = 0f
    while (x < w) { drawLine(Color(0xFF17181C), Offset(x, 0f), Offset(x, h), 1.dp.toPx()); x += 26.dp.toPx() }
    var y = 0f
    while (y < h) { drawLine(Color(0xFF17181C), Offset(0f, y), Offset(w, y), 1.dp.toPx()); y += 26.dp.toPx() }
    val p = Path().apply { moveTo(0.55f * w, h); cubicTo(0.70f * w, 0.60f * h, 0.78f * w, 0.50f * h, w, 0.20f * h) }
    drawPath(p, road, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    val pin = Offset(0.82f * w, 0.42f * h)
    ripples(pin, t, red, 26.dp.toPx())
    drawCircle(red, 9.dp.toPx(), pin)
    drawCircle(well, 3.5.dp.toPx(), pin)
}

@Composable
private fun BoxScope.familyCards(t: Float) {
    Column(Modifier.align(Alignment.TopStart).padding(10.dp).widthIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(t >= 0.04f, enter = fadeIn(tween(240)) + expandVertically()) {
            Sms("ARMOR SOS: Rohan may have crashed and is not responding. Loc: maps.google.com/?q=12.9716,77.5946 (within 6 m, 3 s old)")
        }
        AnimatedVisibility(t >= 0.36f, enter = fadeIn(tween(240)) + expandVertically()) {
            Sms("ARMOR medical, Rohan: Blood O+. Allergies: penicillin.")
        }
        AnimatedVisibility(t >= 0.66f, enter = fadeIn(tween(240)) + expandVertically()) {
            InfoCard("Rohan is calling…", "Rings until someone answers", green)
        }
    }
}

@Composable
private fun Sms(text: String) =
    Box(Modifier.clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp)).background(Color(0xF2232529)).padding(horizontal = 11.dp, vertical = 8.dp)) {
        Txt(text, Type.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp), ArmorColors.OnBg)
    }

// ---- the scenes -------------------------------------------------------------------------------

private val scenes = listOf(
    Scene(
        "A CRASH WITH NOBODY AROUND", "You go down. Nobody saw it.", 15000,
        listOf(
            Beat(0f, "Riding home alone, late at night. Nobody behind you.", true),
            Beat(0.28f, "A pothole. You go down and can't reach your phone.", true),
            Beat(0.36f, "ARMOR feels the hard impact, then sees the bike stop dead.", false),
            Beat(0.48f, "An alarm: are you okay? You have 30 seconds to cancel.", false),
            Beat(0.74f, "No answer. Your exact spot is texted out, then your contacts are called.", false),
        ),
        listOf("Impact plus a sudden stop, not just a bump: potholes and a dropped phone don't set it off.", "Cancel with one tap, or just say “I'm okay”.", "Calls go down your list until someone answers."),
        draw = { crash(it) }, overlay = { crashCards(it) },
    ),
    Scene(
        "FALLING BEHIND THE GROUP", "One rider stops. Nobody notices.", 16000,
        listOf(
            Beat(0f, "Six riders on one road. Nobody can see the back of the group.", true),
            Beat(0.30f, "Rohan stops on a bend, out of everyone's sight.", true),
            Beat(0.50f, "Stopped away from the group? ARMOR asks: are you okay?", false),
            Beat(0.66f, "No answer. The group is alerted and SOS texts go out with the spot.", false),
            Beat(0.84f, "The nearest rider is called and heads straight there.", false),
        ),
        listOf("Everyone on one live map: who's ahead, who's behind, who's stopped.", "Stopping with the group is normal. Stopping alone is what gets checked.", "Riders reply “On my way” so everyone knows it's handled."),
        draw = { behind(it) }, overlay = { behindCards(it) },
    ),
    Scene(
        "NO NETWORK", "Ghat roads. No data. One bar.", 15000,
        listOf(
            Beat(0f, "Up in the hills: no mobile data, one bar of signal at best.", true),
            Beat(0.45f, "Something goes wrong where internet apps go silent.", true),
            Beat(0.55f, "GPS works without internet, so ARMOR still knows exactly where you are.", false),
            Beat(0.62f, "The SOS goes as a plain SMS. If it fails, it is sent again every 20 s.", false),
            Beat(0.80f, "One bar is enough. Your family gets a map link to the spot.", false),
        ),
        listOf("Location comes from GPS satellites, not the internet.", "SOS texts don't need mobile data.", "Failed texts are retried by themselves."),
        draw = { hills(it) }, overlay = { hillCards(it) },
    ),
    Scene(
        "PHONE IN YOUR POCKET", "Screen off. Ringer on silent.", 12000,
        listOf(
            Beat(0f, "Phone in your pocket, screen locked, ringer on silent.", true),
            Beat(0.22f, "That's exactly when you can't reach it after a fall.", true),
            Beat(0.40f, "ARMOR keeps watching in the background for the whole ride.", false),
            Beat(0.55f, "The alarm takes over the lock screen at full alarm volume.", false),
            Beat(0.78f, "People nearby hear it and see it's an emergency.", false),
        ),
        listOf("Runs with the screen off while a ride is on.", "Alarm plays at full volume, even with the ringer on silent.", "Full-screen alert over the lock screen."),
        draw = { pocket(it) }, overlay = { pocketCards(it) },
    ),
    Scene(
        "HANDS ON THE BARS", "Gloves on. No tapping.", 14000,
        listOf(
            Beat(0f, "Gloves on, hands on the bars. Tapping a phone isn't an option.", true),
            Beat(0.14f, "Say “Armor” and what you need. It tells the group for you.", false),
            Beat(0.42f, "Call or text anyone on your list by name.", false),
            Beat(0.70f, "Cancel a check without stopping: “I'm okay”.", false),
        ),
        listOf("Team stops, status and messages by voice.", "“Armor, SOS” sends for help hands-free.", "Tap the mic, or turn on the wake word in Settings."),
        height = 240.dp, draw = { voice(it) }, overlay = { voiceCards(it) },
    ),
    Scene(
        "DEAD BATTERY, RUNNING LATE", "Nobody knows until you go dark.", 15000,
        listOf(
            Beat(0f, "Long ride, phone dying. The group won't know until you go quiet.", true),
            Beat(0.40f, "At 15% ARMOR tells the group, before the phone dies.", false),
            Beat(0.58f, "You were due home an hour ago.", true),
            Beat(0.70f, "Past your expected arrival, family gets a text with your last location.", false),
            Beat(0.90f, "And when you do get there, a “reached safely” text.", false),
        ),
        listOf("Low battery warning to the whole group.", "Set where you're heading and when you'll get there.", "Family hears when you're late, and when you're home."),
        draw = { battery(it) }, overlay = { batteryCards(it) },
    ),
    Scene(
        "NO FALSE ALARMS", "Normal stops stay normal.", 15000,
        listOf(
            Beat(0f, "Before a ride starts: location, GPS, SMS, alerts and a contact must be ready.", false),
            Beat(0.12f, "Red light. Everyone stops.", true),
            Beat(0.20f, "Stopping with the group, or briefly, is normal. No alarm.", false),
            Beat(0.52f, "Fuel and chai: a fifteen-minute stop.", true),
            Beat(0.60f, "Tap Fuel stop, or say it, and checks pause while you're off the bike.", false),
            Beat(0.86f, "Nothing runs with no ride on, and alarms wait until you've moved off.", false),
        ),
        listOf("A ride only starts when the safety checks pass.", "Fuel, food and photo stops pause the checks for 15 min.", "Every alarm can be cancelled before anything is sent."),
        draw = { calm(it) }, overlay = { calmCards(it) },
    ),
    Scene(
        "WHAT YOUR FAMILY GETS", "Clear, exact, impossible to miss.", 13000,
        listOf(
            Beat(0f, "A text with your exact spot. One tap opens it in Maps.", false),
            Beat(0.36f, "A second text with blood group and allergies, for first responders.", false),
            Beat(0.66f, "Then your phone rings them, so it can't be missed.", false),
        ),
        listOf("Add medical info once, in Settings.", "Pick contacts straight from your phone's Contacts.", "Indian numbers are fixed up for you (+91)."),
        height = 250.dp, draw = { mapPin(it) }, overlay = { familyCards(it) },
    ),
)

// ---- small pieces -----------------------------------------------------------------------------

@Composable
private fun BoxScope.Pop(visible: Boolean, align: Alignment, content: @Composable () -> Unit) =
    AnimatedVisibility(visible, Modifier.align(align).padding(10.dp), enter = fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.92f), exit = fadeOut(tween(180))) { content() }

@Composable
private fun InfoCard(title: String, sub: String, accent: Color, action: String? = null) {
    Row(
        Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF2141417)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(accent, 8.dp)
        Column(Modifier.weight(1f, fill = false)) {
            Txt(title, Type.Label.copy(fontSize = 13.sp), ArmorColors.OnBg, maxLines = 1)
            Txt(sub, Type.Caption.copy(fontSize = 11.sp), ArmorColors.Muted, maxLines = 1)
        }
        if (action != null) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 9.dp, vertical = 5.dp)) {
            Txt(action, Type.Label.copy(fontSize = 11.sp), Color(0xFF1A0B00), maxLines = 1)
        }
    }
}

@Composable
private fun Chip(text: String, accent: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xF2141417)).border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatusDot(accent, 6.dp)
        Txt(text, Type.Label.copy(fontSize = 12.sp), ArmorColors.OnBg, maxLines = 1)
    }
}

/** Plays a scene from the start each time its page comes into view, with a short pause between loops. */
@Composable
private fun sceneClock(active: Boolean, durationMs: Int, restart: Int): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(active, restart) {
        a.snapTo(0f)
        if (!active) return@LaunchedEffect
        while (true) {
            a.animateTo(1f, tween(durationMs, easing = LinearEasing))
            delay(1400)
            a.snapTo(0f)
        }
    }
    return a.value
}

@Composable
private fun ScenePage(index: Int, scene: Scene, active: Boolean) {
    var restart by remember { mutableIntStateOf(0) }
    val t = sceneClock(active, scene.durationMs, restart)
    val beat = scene.beats.indexOfLast { t >= it.at }.coerceAtLeast(0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 60.dp, bottom = 140.dp)) {
        Txt("SITUATION ${index + 1} OF ${scenes.size}  ·  ${scene.eyebrow}", Type.Eyebrow.copy(letterSpacing = 1.5.sp, fontSize = 11.sp), amber, maxLines = 1)
        Txt(scene.title, Type.Title.copy(fontSize = 24.sp), ArmorColors.OnBg, Modifier.padding(top = 6.dp))
        Box(
            Modifier.padding(top = 14.dp).fillMaxWidth().height(scene.height).panel(radius = 24.dp).clip(RoundedCornerShape(24.dp)).background(well)
                .clickable { restart++ }.semantics { contentDescription = "${scene.title} Animated example. Tap to replay." },
        ) {
            Canvas(Modifier.fillMaxSize()) { scene.draw(this, t) }
            scene.overlay(this, t)
        }
        // What is happening right now: the risk in red, ARMOR's answer in orange.
        Crossfade(beat, Modifier.padding(top = 14.dp), label = "beat") { i ->
            val b = scene.beats[i]
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background((if (b.risk) red else orange).copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Txt(if (b.risk) "RISK" else "ARMOR", Type.Label.copy(fontSize = 11.sp, letterSpacing = 1.sp), if (b.risk) red else orange, maxLines = 1)
                }
                Txt(b.text, Type.BodyStrong.copy(fontSize = 16.sp, lineHeight = 21.sp), ArmorColors.OnBg)
            }
        }
        // The film's timeline: one segment per moment, coloured by what it is.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            scene.beats.forEachIndexed { i, b ->
                val end = scene.beats.getOrNull(i + 1)?.at ?: 1f
                val fill = ease(b.at, end, t)
                Box(Modifier.weight((end - b.at).coerceAtLeast(0.05f)).height(4.dp).clip(CircleShape).background(ArmorColors.S3)) {
                    Box(Modifier.fillMaxWidth(fill).height(4.dp).background(if (b.risk) red else orange))
                }
            }
        }
        Column(Modifier.padding(top = 18.dp).fillMaxWidth().panel(radius = 18.dp, neon = false).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("HOW ARMOR HELPS", Type.Eyebrow.copy(letterSpacing = 1.5.sp, fontSize = 11.sp), orange)
            scene.helps.forEach { h ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(orange))
                    Txt(h, Type.Body.copy(fontSize = 14.sp, lineHeight = 19.sp), ArmorColors.Muted)
                }
            }
        }
    }
}

@Composable
fun Onboarding(onFinish: () -> Unit, onSetup: () -> Unit) {
    // 0 welcome, then one page per situation, then get ready.
    val pages = scenes.size + 2
    val state = rememberPagerState(pageCount = { pages })
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages - 1

    Box(Modifier.fillMaxSize().background(ArmorColors.Bg).warmGlow(strength = 0.22f, scatter = false).statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(state, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 60.dp, bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArmorLogo(60.dp)
                    Txt("ARMOR", Type.Hero, ArmorColors.OnBg, Modifier.padding(top = 8.dp), TextAlign.Center)
                    Txt("Advanced Rider Monitoring & Orientation Reasoning System", Type.Caption, ArmorColors.Dim, Modifier.padding(top = 4.dp), TextAlign.Center)
                    Txt("Ride together. Come home together.", Type.BodyStrong.copy(fontSize = 18.sp), orange, Modifier.padding(top = 14.dp), TextAlign.Center)
                    Txt("Most rides are fine. These are the ones that aren't, and what ARMOR does when they happen.", Type.Body, ArmorColors.Muted, Modifier.padding(top = 8.dp), TextAlign.Center)
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth().panel(radius = 18.dp, neon = false).padding(vertical = 4.dp)) {
                        scenes.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { scope.launch { state.animateScrollToPage(i + 1) } }.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(orange.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Txt("${i + 1}", Type.Label.copy(fontSize = 12.sp), orange) }
                                Txt(s.eyebrow.lowercase().replaceFirstChar { it.uppercase() }, Type.BodyStrong.copy(fontSize = 15.sp), ArmorColors.OnBg, Modifier.weight(1f), maxLines = 1)
                                Txt("›", Type.Title, ArmorColors.Dim)
                            }
                        }
                    }
                }
                pages - 1 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 60.dp, bottom = 160.dp)) {
                    Txt("READY IN FOUR STEPS", Type.Eyebrow.copy(letterSpacing = 1.5.sp, fontSize = 11.sp), orange)
                    Txt("Set it up once. It looks after every ride.", Type.Title.copy(fontSize = 24.sp), ArmorColors.OnBg, Modifier.padding(top = 6.dp))
                    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            "Allow protection" to "Location, SMS, calls and alerts, so ARMOR can help with the screen off.",
                            "Add who to tell" to "Pick emergency contacts from your phone, and add your medical info.",
                            "Create or join a ride" to "Make a ride and share the 6-letter code, or enter a friend's code. Solo works too.",
                            "Slide to ride" to "ARMOR checks location, GPS, SMS and contacts first. If something is missing it tells you what.",
                        ).forEachIndexed { i, (a, b) ->
                            Row(Modifier.fillMaxWidth().panel(radius = 18.dp, neon = false).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(orange), contentAlignment = Alignment.Center) { Txt("${i + 1}", Type.BodyStrong, Color(0xFF1A0B00)) }
                                Column { Txt(a, Type.BodyStrong); Txt(b, Type.Caption, ArmorColors.Muted) }
                            }
                        }
                    }
                }
                else -> ScenePage(page - 1, scenes[page - 1], active = state.currentPage == page)
            }
        }

        if (!last) Txt("Skip", Type.BodyStrong, ArmorColors.Muted, Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(12.dp)).clickable { onFinish() }.padding(horizontal = 12.dp, vertical = 8.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(ArmorColors.Bg.copy(alpha = 0.92f)).padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(pages) { i -> Box(Modifier.size(width = if (i == state.currentPage) 20.dp else 7.dp, height = 7.dp).clip(CircleShape).background(if (i == state.currentPage) orange else ArmorColors.S3)) }
            }
            if (last) {
                PrimaryButton("Set up protection", color = orange, contentColor = Color(0xFF1A0B00)) { onSetup() }
                SecondaryButton("Start using ARMOR") { onFinish() }
            } else {
                PrimaryButton(if (state.currentPage == 0) "Show me" else "Next", color = orange, contentColor = Color(0xFF1A0B00)) {
                    scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                }
            }
        }
    }
}
