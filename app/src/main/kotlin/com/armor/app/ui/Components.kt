package com.armor.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cards are a barely-there vertical gradient, which reads as depth without any glow. */
val CARD_FILL = Brush.verticalGradient(listOf(Color(0xFF1E1F23), Color(0xFF121315)))

/** Hairline border that catches light along the top edge and fades to nothing at the bottom, like glass. */
val CARD_EDGE = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)))

@Composable
fun Txt(text: String, style: TextStyle = Type.Body, color: Color = ArmorColors.OnBg, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, style = style, textAlign = align, maxLines = maxLines)
}

/** A rider's circular avatar. [ring] draws the white outline used on the map. */
@Composable
fun Avatar(label: String, color: Color, size: Dp = 44.dp, textColor: Color = Color.White, fontSize: Int = 17) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).background(color, CircleShape))

/** Small rounded status chip floating over the map. */
@Composable
fun MapChip(text: String, dot: Color? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(ArmorColors.S1.copy(alpha = 0.86f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) StatusDot(dot)
        Txt(text, Type.Label, maxLines = 1)
    }
}

/** A selectable rounded label (segmented controls, demo actions). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = ArmorColors.S2,
    contentColor: Color = ArmorColors.OnBg,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape).background(if (selected) ArmorColors.S3 else color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) ArmorColors.OnBg else contentColor.copy(alpha = if (selected) 1f else 0.85f), style = Type.Label, maxLines = 1) }
}

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(CARD_FILL).border(1.dp, CARD_EDGE, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Group heading above a card, in the style of an inset grouped list. */
@Composable
fun SectionTitle(text: String) {
    Row(Modifier.padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 14.dp, height = 3.dp).clip(CircleShape).background(ArmorColors.Orange))
        Txt(text.uppercase(), Type.Eyebrow, ArmorColors.Muted)
    }
}

@Composable
fun ScreenTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArmorLogo(26.dp)
            Txt(text.uppercase(), Type.Hero.copy(fontSize = 40.sp, lineHeight = 42.sp))
        }
        if (sub != null) Txt(sub, Type.Body, ArmorColors.Muted, Modifier.padding(top = 2.dp))
    }
}

@Composable
fun LabeledRow(label: String, value: String, valueColor: Color = ArmorColors.OnBg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ArmorColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Divider1(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(ArmorColors.Line))

/** Filled call-to-action. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Color.White, contentColor: Color = ArmorColors.Bg, arrow: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (arrow) Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(40.dp).background(contentColor, CircleShape), contentAlignment = Alignment.Center) {
            Text("\u2192", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Quieter alternative to [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(ArmorColors.S2).border(1.dp, ArmorColors.Line, RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = ArmorColors.OnBg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

/** Kept for call sites that pass a colour pair. */
@Composable
fun BigButton(text: String, color: Color, contentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PrimaryButton(text, modifier, color, contentColor, onClick = onClick)

/**
 * Press-and-hold SOS. A bump, a glove or a pocket must never trigger it, so it charges while held and fires only when
 * full. [outlined] is the quieter form used on alert screens where the main action is "I'm okay".
 */
@Composable
fun HoldToSosButton(
    modifier: Modifier = Modifier,
    holdMs: Long = 1_500,
    label: String = "Hold for SOS",
    sub: String? = "Press and hold for 1.5 seconds",
    outlined: Boolean = false,
    onTrigger: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(29.dp)
    val base = if (outlined) Modifier.border(1.5.dp, ArmorColors.Red, shape) else Modifier.background(ArmorColors.Red)
    val fg = if (outlined) ArmorColors.Red else Color.White
    Box(
        modifier.fillMaxWidth().height(58.dp).clip(shape).then(base)
            .pointerInput(holdMs) {
                detectTapGestures(
                    onPress = {
                        val start = System.nanoTime()
                        val job = scope.launch {
                            while (progress < 1f) {
                                progress = ((System.nanoTime() - start) / 1_000_000f / holdMs).coerceAtMost(1f)
                                if (progress >= 1f) { onTrigger(); break }
                                delay(16)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        progress = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress).background(if (outlined) ArmorColors.Red.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.22f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).border(2.dp, fg.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                Text("SOS", color = fg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column {
                Text(label, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (sub != null) Text(sub, color = fg.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
}

/** Countdown ring: the arc shrinks as [fraction] (remaining/total) goes from 1 to 0; [content] sits in the middle. */
@Composable
fun CountdownRing(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 224.dp, content: @Composable () -> Unit) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // In the last third the glow throbs, so the urgency is felt without reading the number.
        val throb by androidx.compose.animation.core.rememberInfiniteTransition(label = "throb").animateFloat(
            0.55f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "throb",
        )
        val pulse = if (fraction < 0.34f) throb else 1f
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.30f * pulse), Color.Transparent), c, r * 1.15f), r * 1.15f, c)
            // One dot per 3 degrees; the lit ones are the time remaining.
            val total = 120
            val lit = (total * fraction.coerceIn(0f, 1f)).toInt()
            for (i in 0 until total) {
                val a = (-90.0 + i * 360.0 / total) * Math.PI / 180.0
                val on = i < lit
                val rr = r * 0.94f
                drawCircle(if (on) color else Color.White.copy(alpha = 0.10f), if (on) 3.2.dp.toPx() else 2.dp.toPx(), c + Offset(Math.cos(a).toFloat(), Math.sin(a).toFloat()) * rr)
            }
            drawCircle(color.copy(alpha = 0.10f), r * 0.80f, c, style = Stroke(1.dp.toPx()))
        }
        content()
    }
}

/** One row of a grouped list: leading icon box, title with optional detail, optional trailing text. */
@Composable
fun ListRow(title: String, detail: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, trailing: String? = null, trailingColor: Color = ArmorColors.Muted, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(ArmorColors.S3), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(icon, contentDescription = null, tint = ArmorColors.OnBg, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f)) {
            Txt(title, Type.BodyStrong)
            if (detail != null) Txt(detail, Type.Label, ArmorColors.Muted)
        }
        if (trailing != null) Txt(trailing, Type.Label, trailingColor)
    }
}

/** Container for [ListRow]s with hairline separators. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(CARD_FILL).border(1.dp, CARD_EDGE, shape)) { content() }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

/** Click handler without the ripple, for plain text actions. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
fun FullScreenBg(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(ArmorColors.Bg)) { content() }
