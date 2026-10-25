package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A tappable rounded label; also used as a status indicator when [onClick] is null. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = ArmorColors.SurfaceHigh,
    contentColor: Color = ArmorColors.OnBg,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) ArmorColors.Orange else color
    val fg = if (selected) Color.Black else contentColor
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Small outlined status chip for the header: an optional coloured dot and a short text. */
@Composable
fun StatusChip(text: String, dot: Color? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(ArmorColors.Surface)
            .border(1.dp, ArmorColors.Line, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, color = ArmorColors.Muted, fontSize = 12.sp, maxLines = 1)
    }
}

/** A round badge with a letter (a rider's initial) or a symbol. */
@Composable
fun Avatar(label: String, color: Color, size: Dp = 34.dp, textColor: Color = Color.Black, fontSize: Int = 14) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(label, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp)
    }
}

/** Countdown ring: the arc shrinks as [fraction] (remaining/total) goes from 1 to 0. [content] sits in the middle. */
@Composable
fun CountdownRing(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier, size: Dp = 200.dp, content: @Composable () -> Unit) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, inset, arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, inset, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/**
 * SOS must be impossible to trigger by accident (a bump, a glove, a pocket) yet reachable in one motion, so it
 * is a press-and-hold with visible progress rather than a tap.
 */
@Composable
fun HoldToSosButton(
    modifier: Modifier = Modifier,
    holdMs: Long = 1_500,
    label: String = "HOLD FOR SOS",
    sub: String? = "Press and hold for 1.5 seconds",
    quiet: Boolean = false,
    onTrigger: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(34.dp)
    val fill = if (quiet) Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f)))
    else Brush.verticalGradient(listOf(Color(0xFFFF4A3F), Color(0xFFE02A20)))
    Box(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(fill)
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
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White.copy(alpha = 0.28f)))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text("🛡", fontSize = 18.sp)
            }
            Column {
                Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BigButton(text: String, color: Color, contentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ArmorColors.Surface)
            .border(1.dp, ArmorColors.Line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = ArmorColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun LabeledRow(label: String, value: String, valueColor: Color = ArmorColors.OnBg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ArmorColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

/** Click handler without the ripple, for plain text actions on a coloured background. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
