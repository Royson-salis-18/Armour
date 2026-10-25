package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A rectangle with its four corners sliced off at 45°, the signature shape of the ARMOR HUD. */
class CutShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Generic(
            Path().apply {
                moveTo(c, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c)
                lineTo(size.width, size.height - c); lineTo(size.width - c, size.height); lineTo(c, size.height)
                lineTo(0f, size.height - c); lineTo(0f, c); close()
            },
        )
    }
}

/** A pointy-top hexagon, used for rider badges and the logo. */
object HexShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Generic(
        Path().apply {
            moveTo(size.width / 2f, 0f); lineTo(size.width, size.height * 0.25f); lineTo(size.width, size.height * 0.75f)
            lineTo(size.width / 2f, size.height); lineTo(0f, size.height * 0.75f); lineTo(0f, size.height * 0.25f); close()
        },
    )
}

/** Deep navy base with a cyan glow top-left, a violet glow bottom-right, a faint grid and a vignette. */
fun Modifier.hudBackground(): Modifier = this.drawBehind {
    drawRect(ArmorColors.Bg)
    drawRect(Brush.radialGradient(listOf(ArmorColors.BgGlow, Color.Transparent), center = Offset(0f, 0f), radius = size.width * 1.1f))
    drawRect(Brush.radialGradient(listOf(ArmorColors.BgGlow2, Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 1.1f))
    val step = 28.dp.toPx()
    val gridColor = ArmorColors.Cyan.copy(alpha = 0.045f)
    var x = 0f
    while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height)); x += step }
    var y = 0f
    while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y)); y += step }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), center = center, radius = size.height * 0.75f))
}

/** Holographic panel: cut corners, a gradient hairline border in the [accent] colour and a soft coloured glow. */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    accent: Color = ArmorColors.Cyan,
    cut: Dp = 14.dp,
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val shape = CutShape(cut)
    Box(
        modifier
            .shadow(10.dp, shape, ambientColor = accent, spotColor = accent)
            .background(Brush.verticalGradient(listOf(ArmorColors.Panel.copy(alpha = 0.94f), ArmorColors.PanelDeep.copy(alpha = 0.96f))), shape)
            .border(1.dp, Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.12f), ArmorColors.Violet.copy(alpha = 0.55f))), shape)
            .padding(padding),
    ) { content() }
}

/** "[ SMALL CAPS LABEL ]" panel heading. */
@Composable
fun HudLabel(text: String, accent: Color = ArmorColors.Cyan, modifier: Modifier = Modifier) {
    Text(
        "[ ${text.uppercase()} ]",
        modifier,
        color = accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp, maxLines = 1,
    )
}

/** Big glowing monospace readout. */
@Composable
fun GlowText(text: String, color: Color, size: Int, modifier: Modifier = Modifier, glow: Float = 22f, weight: FontWeight = FontWeight.ExtraBold, align: TextAlign? = null, mono: Boolean = true, spacing: Int = 0) {
    Text(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp, textAlign = align ?: TextAlign.Unspecified,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            shadow = Shadow(color.copy(alpha = 0.85f), Offset.Zero, glow),
        ),
    )
}

/** Small monospace caption. */
@Composable
fun MonoText(text: String, color: Color = ArmorColors.Muted, size: Int = 11, modifier: Modifier = Modifier, align: TextAlign? = null, spacing: Double = 1.0, weight: FontWeight = FontWeight.Normal) {
    Text(text, modifier, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace, letterSpacing = spacing.sp, textAlign = align, fontWeight = weight)
}

/** Segmented gauge, like a stat bar in a status window: [on] of [total] cells lit. */
@Composable
fun SegmentBar(on: Int, total: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            val lit = i < on
            Box(
                Modifier.weight(1f).height(8.dp)
                    .background(if (lit) color else ArmorColors.Line, CutShape(2.dp))
                    .then(if (lit) Modifier.shadow(6.dp, CutShape(2.dp), ambientColor = color, spotColor = color) else Modifier),
            )
        }
    }
}

/** Hexagonal rider badge with a letter. */
@Composable
fun HexBadge(label: String, color: Color, size: Dp = 34.dp, textColor: Color = Color.Black, glow: Boolean = true) {
    Box(
        Modifier.size(size * 0.9f, size)
            .then(if (glow) Modifier.shadow(10.dp, HexShape, ambientColor = color, spotColor = color) else Modifier)
            .background(color, HexShape),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = textColor, fontWeight = FontWeight.Black, fontSize = (size.value * 0.4f).sp) }
}

/** 4-bar signal indicator with a caption, e.g. GPS / NET / VOICE. */
@Composable
fun SignalBars(level: Int, color: Color, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(14.dp)) {
            listOf(5, 8, 11, 14).forEachIndexed { i, h ->
                Box(Modifier.width(4.dp).height(h.dp).background(if (i < level) color else color.copy(alpha = 0.18f), CutShape(1.dp)))
            }
        }
        Text(caption, color = ArmorColors.Muted, fontSize = 8.5.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Small outlined tag, e.g. "SYSTEM NOTICE". */
@Composable
fun SysTag(text: String, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, accent, CutShape(5.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
    }
}

/** Numbered step row used in "what happens next" panels. */
@Composable
fun StepRow(n: Int, text: String, trailing: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).border(1.5.dp, accent, CutShape(6.dp)), contentAlignment = Alignment.Center) {
            Text("$n", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Text(text, Modifier.weight(1f).padding(horizontal = 10.dp), color = ArmorColors.OnBg, fontSize = 13.sp)
        MonoText(trailing, size = 10)
    }
}

/** Tick-ring countdown: 60 ticks that go dark as time runs out; [content] sits in the middle. */
@Composable
fun TickRing(fraction: Float, accent: Color, modifier: Modifier = Modifier, size: Dp = 210.dp, content: @Composable () -> Unit) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val lit = (fraction.coerceIn(0f, 1f) * 60).toInt()
            for (i in 0 until 60) {
                val a = i * 6.0 * PI / 180.0
                val on = i < lit
                val r1 = r * 0.80f
                val r2 = r * (if (on) 0.95f else 0.90f)
                val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
                val c = this.center
                if (on) drawLine(accent.copy(alpha = 0.3f), c + dir * r1, c + dir * r2, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(if (on) accent else Color.White.copy(alpha = 0.18f), c + dir * r1, c + dir * r2, strokeWidth = (if (on) 3 else 2).dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(accent.copy(alpha = 0.25f), r * 0.66f, this.center, style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 10f))))
        }
        content()
    }
}

/** Filled/outlined action button with cut corners and a glow. */
@Composable
fun HudButton(text: String, brush: Brush, contentColor: Color, glow: Color, modifier: Modifier = Modifier, textSize: Int = 19, vertical: Dp = 20.dp, onClick: () -> Unit) {
    val shape = CutShape(16.dp)
    Box(
        modifier.fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = glow, spotColor = glow)
            .background(brush, shape)
            .clickable(onClick = onClick)
            .padding(vertical = vertical),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = contentColor, fontSize = textSize.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, textAlign = TextAlign.Center) }
}

@Composable
fun Divider1(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

@Composable
fun FillHeight(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier.fillMaxHeight()) { content() }
