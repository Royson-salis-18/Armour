package com.armor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ARMOR's hexagon design language: lit, bevelled hex tiles that cast a shadow, glow orange when active, and sit on a
 * faint honeycomb backdrop. Hexagons are pointy-top: a vertex at the top and bottom, flat sides left and right.
 */
private fun hexPath(size: Size, inset: Float = 0f): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width / sqrt(3f), size.height / 2f) - inset
    return Path().apply {
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90.0)
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Pointy-top hexagon, centred and as large as fits. */
object HexShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Generic(hexPath(size))
}

/** Width of a pointy-top hexagon whose height is [height]. */
fun hexWidth(height: Dp): Dp = height * (sqrt(3f) / 2f)

/**
 * A 3D hexagon tile: a drop shadow underneath, a face lit from above (lighter at the top, darker at the bottom), a
 * bright bevel on the upper edges and a dark one on the lower edges, and a soft orange halo when [active].
 */
@Composable
fun HexTile(
    height: Dp,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Color = ArmorColors.Orange,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val w = hexWidth(height)
    val face = if (active) listOf(lighten(accent, 0.28f), accent, darken(accent, 0.22f))
    else listOf(Color(0xFF34363C), Color(0xFF232428), Color(0xFF17181B))
    Box(
        modifier.size(w, height)
            // Halo first, outside the clip, so it spills around the tile.
            .drawBehind {
                if (active) drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), center = center, radius = size.minDimension * 0.85f),
                    radius = size.minDimension * 0.85f,
                )
            }
            .shadow(if (active) 12.dp else 8.dp, HexShape, ambientColor = Color.Black, spotColor = if (active) accent else Color.Black)
            .clip(HexShape)
            .background(Brush.verticalGradient(face))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = contentDescription) { onClick() } else Modifier)
            .drawWithContent {
                drawContent()
                val outer = hexPath(size, inset = 1.dp.toPx())
                // A glossy sheen across the upper half, as if lit from above.
                drawPath(outer, Brush.verticalGradient(0f to Color.White.copy(alpha = if (active) 0.28f else 0.12f), 0.5f to Color.Transparent))
                // Bevel: light catches the top three edges, the bottom three sit in shadow.
                val r = minOf(size.width / sqrt(3f), size.height / 2f) - 1.5.dp.toPx()
                val pts = (0 until 6).map { i ->
                    val a = Math.toRadians(60.0 * i - 90.0)
                    Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                }
                val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val top = Path().apply { moveTo(pts[4].x, pts[4].y); lineTo(pts[5].x, pts[5].y); lineTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y) }
                val bottom = Path().apply { moveTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y); lineTo(pts[3].x, pts[3].y); lineTo(pts[4].x, pts[4].y) }
                drawPath(top, Color.White.copy(alpha = if (active) 0.55f else 0.22f), style = stroke)
                drawPath(bottom, Color.Black.copy(alpha = 0.45f), style = stroke)
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A hex tile with an icon or glyph inside and a caption under it, for rows of quick actions. */
@Composable
fun HexAction(
    label: String,
    height: Dp = 60.dp,
    active: Boolean = false,
    accent: Color = ArmorColors.Orange,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    Column(modifier.width(hexWidth(height) + 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HexTile(height, active = active, accent = accent, contentDescription = label, onClick = onClick) {
            icon(if (active) Color(0xFF1A0B00) else ArmorColors.OnBg)
        }
        Txt(label, Type.Caption, if (active) accent else ArmorColors.Muted, Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

/**
 * Lays tiles out as a honeycomb: rows of [perRow], every other row shifted half a tile and pulled up so the hexagons
 * interlock.
 */
@Composable
fun <T> Honeycomb(items: List<T>, perRow: Int, tileHeight: Dp, gap: Dp = 6.dp, tile: @Composable (T) -> Unit) {
    val w = hexWidth(tileHeight)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        items.chunked(perRow).forEachIndexed { row, chunk ->
            Row(
                Modifier.offset(x = if (row % 2 == 1) (w + gap) / 2 else 0.dp, y = -(tileHeight / 4 - gap / 2) * row),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) { chunk.forEach { tile(it) } }
        }
    }
}

/**
 * A faint honeycomb across a surface, brightest near the top centre like light falling on it. Drawn behind content.
 */
fun Modifier.hexBackdrop(color: Color = Color.White, strength: Float = 0.06f, cell: Dp = 34.dp): Modifier = drawWithCache {
    // One path for the whole grid, built once per size; a radial brush does the lighting.
    val r = cell.toPx() / 2f
    val w = sqrt(3f) * r
    val rowH = 1.5f * r
    val grid = Path()
    var row = 0
    var y = 0f
    while (y < size.height + r) {
        var x = if (row % 2 == 0) 0f else w / 2f
        while (x < size.width + w) {
            for (i in 0 until 6) {
                val ang = Math.toRadians(60.0 * i - 90.0)
                val px = x + r * cos(ang).toFloat()
                val py = y + r * sin(ang).toFloat()
                if (i == 0) grid.moveTo(px, py) else grid.lineTo(px, py)
            }
            grid.close()
            x += w
        }
        y += rowH
        row++
    }
    val light = Brush.radialGradient(
        listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.35f), color.copy(alpha = strength * 0.12f)),
        center = Offset(size.width / 2f, -size.height * 0.05f), radius = maxOf(size.width, size.height) * 1.1f,
    )
    val stroke = Stroke(1f)
    onDrawBehind { drawPath(grid, light, style = stroke) }
}

private fun lighten(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)
private fun darken(c: Color, f: Float) = Color(c.red * (1 - f), c.green * (1 - f), c.blue * (1 - f), c.alpha)
