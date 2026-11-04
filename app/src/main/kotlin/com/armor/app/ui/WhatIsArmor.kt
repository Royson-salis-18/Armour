package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Pitch(val title: String, val body: String, val art: DrawScope.() -> Unit)

private val orange = ArmorColors.Orange
private val wellColor = Color(0xFF0C0C0E)
private val roadColor = Color(0xFF2B2D33)
private val dotColor = Color(0xFF8B8E97)

private val pitches = listOf(
    Pitch("See everyone live", "One shared map. Spot who is ahead, behind or stopped.") {
        val w = size.width; val h = size.height
        val road = Path().apply { moveTo(-0.03f * w, 0.78f * h); cubicTo(0.25f * w, 0.35f * h, 0.5f * w, 0.9f * h, 1.03f * w, 0.28f * h) }
        drawPath(road, roadColor, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        listOf(0.28f to 0.62f, 0.52f to 0.60f, 0.76f to 0.42f).forEach { (x, y) -> drawCircle(dotColor, 5.dp.toPx(), Offset(x * w, y * h)) }
        drawCircle(orange.copy(alpha = 0.5f), 14.dp.toPx(), Offset(0.4f * w, 0.57f * h), style = Stroke(1.4.dp.toPx()))
        drawCircle(orange, 7.dp.toPx(), Offset(0.4f * w, 0.57f * h))
    },
    Pitch("Stop, chat, talk", "Lead calls a team stop. Message the group or just say “Armor”.") {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFF26282D), Offset(0.12f * w, 0.18f * h), Size(0.52f * w, 0.24f * h), CornerRadius(h))
        drawCircle(dotColor, 3.dp.toPx(), Offset(0.19f * w, 0.30f * h))
        drawRoundRect(orange, Offset(0.36f * w, 0.52f * h), Size(0.52f * w, 0.24f * h), CornerRadius(h))
        drawCircle(Color.White, 3.dp.toPx(), Offset(0.8f * w, 0.64f * h))
        val flag = Path().apply { moveTo(0.78f * w, 0.14f * h); lineTo(0.9f * w, 0.1f * h); lineTo(0.9f * w, 0.34f * h); lineTo(0.78f * w, 0.30f * h); close() }
        drawPath(flag, orange)
    },
    Pitch("Crash? It calls for help", "No reply after a crash or a long stop, and ARMOR sends your SOS for you.") {
        val w = size.width; val h = size.height
        val c = Offset(0.5f * w, 0.5f * h)
        drawCircle(orange.copy(alpha = 0.22f), 0.46f * h, c, style = Stroke(1.2.dp.toPx()))
        drawCircle(orange.copy(alpha = 0.5f), 0.34f * h, c, style = Stroke(1.4.dp.toPx()))
        val tri = Path().apply { moveTo(c.x, c.y - 0.28f * h); lineTo(c.x + 0.26f * h, c.y + 0.2f * h); lineTo(c.x - 0.26f * h, c.y + 0.2f * h); close() }
        drawPath(tri, orange)
        drawRoundRect(wellColor, Offset(c.x - 1.6.dp.toPx(), c.y - 0.1f * h), Size(3.2.dp.toPx(), 0.17f * h), CornerRadius(2.dp.toPx()))
        drawCircle(wellColor, 2.2.dp.toPx(), Offset(c.x, c.y + 0.13f * h))
    },
    Pitch("Texts and calls, no data needed", "Your contacts and the riders near you get your location by SMS, then a call.") {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFF141517), Offset(0.36f * w, 0.1f * h), Size(0.28f * w, 0.8f * h), CornerRadius(10.dp.toPx()))
        drawRoundRect(Color(0xFF34363C), Offset(0.36f * w, 0.1f * h), Size(0.28f * w, 0.8f * h), CornerRadius(10.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        listOf(0.18f, 0.28f).forEachIndexed { i, r ->
            val p = Path().apply { arcTo(androidx.compose.ui.geometry.Rect(Offset(0.5f * w - r * w, 0.34f * h - r * w), Size(2 * r * w, 2 * r * w)), 225f, 90f, true) }
            drawPath(p, orange.copy(alpha = 1f - i * 0.35f), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(Color(0xFF3B3D44), Offset(0.42f * w, 0.62f * h), Offset(0.58f * w, 0.62f * h), 2.4.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFF3B3D44), Offset(0.42f * w, 0.74f * h), Offset(0.52f * w, 0.74f * h), 2.4.dp.toPx(), StrokeCap.Round)
        listOf(0.25f to 0.15f, 0.5f to 0f, 0.75f to -0.15f).forEach { (y, dy) ->
            drawLine(Color(0xFF3B3D44), Offset(0.76f * w, y * h * 2 + dy * h), Offset(0.92f * w, y * h * 2 + dy * h * 1.6f), 2.4.dp.toPx(), StrokeCap.Round)
        }
    },
)

/** The first thing on the home screen: what ARMOR is, in one line, and four swipeable pictures of what it does. */
@Composable
fun WhatIsArmor(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Txt("WHAT IS ARMOR?", Type.Eyebrow.copy(letterSpacing = 2.sp), ArmorColors.Orange)
        Txt(
            "A safety net for group rides. Everyone sees each other live, and if a rider goes down, help is called automatically.",
            Type.BodyStrong.copy(fontSize = 16.sp, lineHeight = 22.sp), ArmorColors.OnBg, Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pitches) { p ->
                Column(Modifier.width(224.dp).panel(radius = 20.dp, neon = false).padding(10.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp)).background(wellColor)) { p.art(this) }
                    Txt(p.title, Type.BodyStrong.copy(fontSize = 14.sp), ArmorColors.OnBg, Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp))
                    Txt(p.body, Type.Caption, ArmorColors.Muted, Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp), maxLines = 3)
                }
            }
        }
    }
}
