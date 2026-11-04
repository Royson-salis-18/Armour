package com.armor.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Whether the walkthrough is on screen. Opened automatically on first launch and from Settings > How ARMOR works. */
object Tour {
    val show = MutableStateFlow(false)
}

private class Slide(val title: String, val body: String, val art: (DrawScope.(t: Float) -> Unit)?)

private val red = ArmorColors.Red
private val amber = ArmorColors.Amber
private val well = Color(0xFF0C0C0E)
private val road = Color(0xFF2B2D33)
private val dim = Color(0xFF8B8E97)

/** A point [u] (0..1) along the demo road. */
private fun DrawScope.roadPath(): Path {
    val w = size.width; val h = size.height
    return Path().apply { moveTo(0.04f * w, 0.70f * h); cubicTo(0.30f * w, 0.18f * h, 0.62f * w, 0.95f * h, 0.96f * w, 0.30f * h) }
}

private fun Path.at(u: Float): Offset {
    val m = PathMeasure(); m.setPath(this, false)
    return m.getPosition(m.length * u.coerceIn(0f, 1f))
}

private fun pulse(t: Float, speed: Float = 1f): Float = ((t * speed) % 1f)

private val slides = listOf(
    Slide("See everyone live", "One shared map shows where every rider is, who is ahead, who is behind and who has stopped.") { t ->
        val p = roadPath()
        drawPath(p, road, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        listOf(0f, 0.12f, 0.27f, 0.44f).forEachIndexed { i, off ->
            val u = (0.18f + off + t * 0.38f) % 1f
            val at = p.at(u)
            if (i == 2) { drawCircle(ArmorColors.Orange.copy(alpha = 0.4f), 16.dp.toPx(), at, style = Stroke(1.4.dp.toPx())); drawCircle(ArmorColors.Orange, 8.dp.toPx(), at) }
            else drawCircle(dim, 6.dp.toPx(), at)
        }
    },
    Slide("Stop, chat, talk", "The lead calls a team stop. Chat with the group, or just talk to ARMOR and it does the tapping for you.") { t ->
        val w = size.width; val h = size.height
        val a = (sin(t * 2 * PI.toFloat()) * 0.5f + 0.5f)
        drawRoundRect(Color(0xFF26282D), Offset(0.10f * w, 0.14f * h), Size(0.55f * w, 0.20f * h), CornerRadius(h))
        drawCircle(dim, 3.dp.toPx(), Offset(0.17f * w, 0.24f * h))
        drawRoundRect(ArmorColors.Orange, Offset(0.34f * w, 0.42f * h), Size(0.56f * w, 0.20f * h), CornerRadius(h))
        drawCircle(Color.White, 3.dp.toPx(), Offset(0.83f * w, 0.52f * h))
        // A stop sign that breathes, and a mic with a ring that grows and fades.
        val flag = Path().apply { moveTo(0.74f * w, 0.08f * h); lineTo(0.88f * w, 0.04f * h); lineTo(0.88f * w, 0.30f * h); lineTo(0.74f * w, 0.26f * h); close() }
        drawPath(flag, ArmorColors.Orange.copy(alpha = 0.5f + 0.5f * a))
        val c = Offset(0.2f * w, 0.78f * h)
        drawCircle(ArmorColors.Orange.copy(alpha = 0.5f * (1 - pulse(t))), 8.dp.toPx() + 22.dp.toPx() * pulse(t), c, style = Stroke(1.5.dp.toPx()))
        drawCircle(ArmorColors.Orange, 9.dp.toPx(), c)
    },
    Slide("Help comes by itself", "After a crash, or a long stop with no answer, ARMOR texts your location and rings your contacts and the nearest rider.") { t ->
        val c = Offset(size.width * 0.5f, size.height * 0.5f)
        val h = size.height
        listOf(0f, 0.5f).forEach { off ->
            val k = pulse(t + off)
            drawCircle(red.copy(alpha = 0.5f * (1 - k)), 0.2f * h + 0.3f * h * k, c, style = Stroke(1.5.dp.toPx()))
        }
        val tri = Path().apply { moveTo(c.x, c.y - 0.26f * h); lineTo(c.x + 0.24f * h, c.y + 0.2f * h); lineTo(c.x - 0.24f * h, c.y + 0.2f * h); close() }
        drawPath(tri, red)
        drawRoundRect(well, Offset(c.x - 1.6.dp.toPx(), c.y - 0.08f * h), Size(3.2.dp.toPx(), 0.16f * h), CornerRadius(2.dp.toPx()))
        drawCircle(well, 2.2.dp.toPx(), Offset(c.x, c.y + 0.13f * h))
    },
)

private val demoCaptions = listOf(
    "Everyone rides together. ARMOR watches the group.",
    "One rider stops, away from the others.",
    "ARMOR asks: are you okay?",
    "No answer. SOS texts, with the location, go to contacts and nearby riders.",
    "The nearest rider is called and heads over. Help is on the way.",
)

/** The auto-playing demo: a ride, a rider who stops, the "are you okay?" check, the SOS, and help arriving. */
private fun DrawScope.demo(t: Float) {
    val w = size.width; val h = size.height
    val p = roadPath()
    drawPath(p, road, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
    // Phase boundaries as fractions of the loop.
    val stopAt = 0.30f; val askAt = 0.50f; val sosAt = 0.66f; val helpAt = 0.84f
    fun ease(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    // Riders: A stops at 0.5 along the road; C rides on; B races back to A once help is called.
    val uA = if (t < stopAt) 0.1f + t / stopAt * 0.4f else 0.5f
    val uC = 0.2f + t * 0.8f
    val uB = when { t < sosAt -> 0.05f + t / sosAt * 0.08f; t < helpAt -> 0.13f + 0f; else -> 0.13f + ease(helpAt, 0.97f) * 0.34f }
    val pa = p.at(uA)
    drawCircle(dim, 6.dp.toPx(), p.at(uC))
    drawCircle(dim, 6.dp.toPx(), p.at((uC - 0.14f).coerceIn(0f, 1f)))
    drawCircle(if (t >= helpAt) ArmorColors.Green else dim, 6.dp.toPx(), p.at(uB))

    // Rider A's state colour: orange while riding, amber when stopped, red once the SOS is out.
    val stateColor = when { t >= sosAt -> red; t >= stopAt -> amber; else -> ArmorColors.Orange }
    if (t >= stopAt) {
        val k = pulse(t * 6)
        drawCircle(stateColor.copy(alpha = 0.55f * (1 - k)), 8.dp.toPx() + 22.dp.toPx() * k, pa, style = Stroke(1.5.dp.toPx()))
    }
    drawCircle(stateColor, 8.dp.toPx(), pa)

    // "Are you okay?" countdown ring around A.
    if (t in askAt..sosAt) {
        val k = ease(askAt, sosAt)
        drawArc(amber, -90f, 360f * (1 - k), false, Offset(pa.x - 20.dp.toPx(), pa.y - 20.dp.toPx()), Size(40.dp.toPx(), 40.dp.toPx()), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
    // SOS: envelopes fly from A to three contacts on the right edge.
    if (t in sosAt..helpAt + 0.06f) {
        val k = ease(sosAt, helpAt)
        listOf(0.18f, 0.40f, 0.62f).forEachIndexed { i, y ->
            val to = Offset(0.93f * w, y * h)
            drawCircle(dim, 7.dp.toPx(), to)
            drawCircle(Color(0xFF0C0C0E), 3.dp.toPx(), to)
            val e = ((k * 1.2f) - i * 0.12f).coerceIn(0f, 1f)
            val at = Offset(pa.x + (to.x - pa.x) * e, pa.y + (to.y - pa.y) * e)
            if (e in 0.01f..0.99f) drawRoundRect(red, Offset(at.x - 6.dp.toPx(), at.y - 4.dp.toPx()), Size(12.dp.toPx(), 8.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
fun Onboarding(onFinish: () -> Unit, onSetup: () -> Unit) {
    // 0 welcome, 1-3 the pictures, 4 the demo, 5 get ready.
    val pages = 6
    val state = rememberPagerState(pageCount = { pages })
    val scope = rememberCoroutineScope()
    val t by rememberInfiniteTransition(label = "tour").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val demoT by rememberInfiniteTransition(label = "demo").animateFloat(0f, 1f, infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Restart), label = "dt")
    val last = state.currentPage == pages - 1

    Box(Modifier.fillMaxSize().background(ArmorColors.Bg).warmGlow(strength = 0.22f, scatter = false).statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(state, Modifier.fillMaxSize()) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 56.dp, bottom = 132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (page) {
                    0 -> {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ArmorLogo(120.dp) }
                        Txt("ARMOR", Type.Hero, ArmorColors.OnBg, align = TextAlign.Center)
                        Txt("Advanced Rider Monitoring & Orientation Reasoning System", Type.Caption, ArmorColors.Dim, Modifier.padding(top = 6.dp), TextAlign.Center)
                        Txt("Ride together. Come home together.", Type.BodyStrong.copy(fontSize = 18.sp), ArmorColors.Orange, Modifier.padding(top = 18.dp, bottom = 8.dp), TextAlign.Center)
                        Txt("A safety net for group rides. Here is how it works, in under a minute.", Type.Body, ArmorColors.Muted, align = TextAlign.Center)
                    }
                    in 1..3 -> {
                        val s = slides[page - 1]
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxWidth().height(230.dp).panel(radius = 26.dp).background(well)) { s.art?.invoke(this, t) }
                        }
                        Txt(s.title, Type.Title, ArmorColors.OnBg, Modifier.padding(top = 24.dp), TextAlign.Center)
                        Txt(s.body, Type.Body, ArmorColors.Muted, Modifier.padding(top = 8.dp), TextAlign.Center)
                    }
                    4 -> {
                        Txt("WATCH IT WORK", Type.Eyebrow.copy(letterSpacing = 2.sp), ArmorColors.Orange)
                        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxWidth().height(260.dp).panel(radius = 26.dp).background(well).semantics { contentDescription = "Animated demo of a ride, a stopped rider, an SOS and help arriving" }) { demo(demoT) }
                        }
                        val phase = when { demoT < 0.30f -> 0; demoT < 0.50f -> 1; demoT < 0.66f -> 2; demoT < 0.84f -> 3; else -> 4 }
                        Crossfade(phase, label = "cap") { i ->
                            Txt(demoCaptions[i], Type.BodyStrong.copy(fontSize = 17.sp), if (i >= 3) ArmorColors.OnBg else ArmorColors.OnBg, Modifier.padding(top = 20.dp).height(72.dp), TextAlign.Center)
                        }
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(5) { i -> Box(Modifier.size(width = if (i == phase) 22.dp else 8.dp, height = 4.dp).clip(CircleShape).background(if (i == phase) (if (i >= 3) ArmorColors.Red else ArmorColors.Orange) else ArmorColors.S3)) }
                        }
                    }
                    else -> {
                        Txt("Get ready in three steps", Type.Title, ArmorColors.OnBg, align = TextAlign.Center)
                        Column(Modifier.padding(top = 24.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(
                                "Allow protection" to "Location all the time, SMS and calls, so ARMOR can help with the screen off.",
                                "Add an emergency contact" to "Pick someone from your contacts. They get your location if you go down.",
                                "Create or join a ride" to "Make a ride and share the 6-letter code, or enter a friend's code.",
                            ).forEachIndexed { i, (a, b) ->
                                Row(Modifier.fillMaxWidth().panel(radius = 18.dp).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(ArmorColors.Orange), contentAlignment = Alignment.Center) { Txt("${i + 1}", Type.BodyStrong, Color(0xFF1A0B00)) }
                                    Column { Txt(a, Type.BodyStrong); Txt(b, Type.Caption, ArmorColors.Muted) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Skip (top right) and the controls at the bottom.
        if (!last) Txt("Skip", Type.BodyStrong, ArmorColors.Muted, Modifier.align(Alignment.TopEnd).padding(20.dp).clip(RoundedCornerShape(12.dp)).clickable { onFinish() }.padding(horizontal = 12.dp, vertical = 8.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages) { i -> Box(Modifier.size(width = if (i == state.currentPage) 22.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i == state.currentPage) ArmorColors.Orange else ArmorColors.S3)) }
            }
            if (last) {
                PrimaryButton("Set up protection", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00)) { onSetup() }
                SecondaryButton("Start using ARMOR") { onFinish() }
            } else {
                PrimaryButton(if (state.currentPage == 4) "Got it" else "Next", color = ArmorColors.Orange, contentColor = Color(0xFF1A0B00)) {
                    scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                }
            }
        }
    }
}
