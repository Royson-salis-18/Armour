package com.armor.app.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A tappable cut-corner label; also used as a status indicator when [onClick] is null. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = ArmorColors.SurfaceHigh,
    contentColor: Color = ArmorColors.OnBg,
    onClick: (() -> Unit)? = null,
) {
    val shape = CutShape(8.dp)
    val bg = if (selected) Brush.verticalGradient(listOf(ArmorColors.Cyan, Color(0xFF1AA6C9))) else Brush.verticalGradient(listOf(color, color))
    val fg = if (selected) Color(0xFF022027) else contentColor
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) ArmorColors.Cyan else ArmorColors.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, letterSpacing = 0.5.sp)
    }
}

/** Small outlined status chip: an optional glowing dot and a short text. */
@Composable
fun StatusChip(text: String, dot: Color? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CutShape(6.dp)).background(ArmorColors.Panel).border(1.dp, ArmorColors.Line, CutShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).shadow(6.dp, CircleShape, ambientColor = dot, spotColor = dot).background(dot, CircleShape))
        MonoText(text, size = 10, spacing = 1.0)
    }
}

/** Press-and-hold SOS. A bump, a glove or a pocket must never trigger it, so it charges while held. */
@Composable
fun HoldToSosButton(
    modifier: Modifier = Modifier,
    holdMs: Long = 1_500,
    label: String = "HOLD FOR SOS",
    sub: String? = "PRESS AND HOLD 1.5 SECONDS",
    quiet: Boolean = false,
    onTrigger: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shape = CutShape(16.dp)
    val fill = if (quiet) Brush.linearGradient(listOf(Color(0xFF5A0F1E), Color(0xFF2A0610)))
    else Brush.linearGradient(listOf(Color(0xFFFF3B4E), Color(0xFF9C0F26)))
    Box(
        modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(14.dp, shape, ambientColor = ArmorColors.Red, spotColor = ArmorColors.Red)
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
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(38.dp).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                Text("🛡", fontSize = 17.sp)
            }
            Column {
                Text(label, color = Color.White, fontSize = if (sub == null) 14.sp else 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 9.5.sp, letterSpacing = 1.sp)
            }
        }
        // The charge bar along the bottom edge fills as the button is held.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(Color.White))
    }
}

@Composable
fun BigButton(text: String, color: Color, contentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    HudButton(text, Brush.linearGradient(listOf(color, color.copy(alpha = 0.78f))), contentColor, glow = color, modifier = modifier, onClick = onClick)
}

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    HudPanel(modifier.fillMaxWidth(), padding = 16.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String) {
    HudLabel(text, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
}

@Composable
fun LabeledRow(label: String, value: String, valueColor: Color = ArmorColors.OnBg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ArmorColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** A round badge with a letter or symbol. */
@Composable
fun Avatar(label: String, color: Color, size: Dp = 34.dp, textColor: Color = Color.Black, fontSize: Int = 14) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(label, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

/** Click handler without the ripple, for plain text actions on a coloured background. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
