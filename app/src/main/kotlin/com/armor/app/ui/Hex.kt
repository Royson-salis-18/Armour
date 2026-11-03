package com.armor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.composed
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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
private fun hexPoints(size: Size, inset: Float): List<Offset> {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width / sqrt(3f), size.height / 2f) - inset
    return (0 until 6).map { i ->
        val a = Math.toRadians(60.0 * i - 90.0)
        Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
}

/** A hexagon with softly rounded corners ([corner] as a fraction of the side). */
private fun hexPath(size: Size, inset: Float = 0f, corner: Float = 0.16f): Path {
    val p = hexPoints(size, inset)
    val side = (p[1] - p[0]).getDistance()
    val r = side * corner
    fun toward(from: Offset, to: Offset): Offset { val d = to - from; val l = d.getDistance(); return from + d * (r / l) }
    return Path().apply {
        for (i in 0 until 6) {
            val v = p[i]
            val a = toward(v, p[(i + 5) % 6])
            val b = toward(v, p[(i + 1) % 6])
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticBezierTo(v.x, v.y, b.x, b.y)
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
    // A recessed hexagon: the tile is a well sunk into the surface. Light falls from the top left, so the upper-left
    // inside edge is in deep shadow and a thin lip of light catches the lower-right edge outside. An active tile glows
    // from inside the well.
    val w = hexWidth(height)
    Box(
        modifier.size(w, height)
            .drawBehind {
                // The lip: a light hexagon peeking out below and to the right of the well.
                val lip = hexPath(size)
                translate(1.6.dp.toPx(), 2.2.dp.toPx()) { drawPath(lip, Color.White.copy(alpha = 0.13f)) }
                translate(-1.dp.toPx(), -1.4.dp.toPx()) { drawPath(lip, Color.Black.copy(alpha = 0.55f)) }
            }
            .clip(HexShape)
            .background(Brush.linearGradient(listOf(Color(0xFF0B0B0C), Color(0xFF151618), Color(0xFF1E1F22)), start = Offset.Zero, end = Offset.Infinite))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = contentDescription) { onClick() } else Modifier)
            .drawWithCache {
                val mid = Offset(size.width / 2f, size.height / 2f)
                val face = hexPath(size)
                val glow = Brush.radialGradient(listOf(accent.copy(alpha = 0.50f), accent.copy(alpha = 0.14f), Color.Transparent), center = mid, radius = size.minDimension * 0.66f)
                val floorLight = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), center = Offset(size.width * 0.65f, size.height * 0.72f), radius = size.maxDimension * 0.6f)
                // Inner shadow: stacked strokes along the edge, widest and faintest first, fading toward the middle, with
                // the shadow on the upper-left half only.
                val shadowBrush = Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.95f), 0.5f to Color.Black.copy(alpha = 0.25f), 1f to Color.Transparent,
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val litEdge = Brush.linearGradient(
                    0f to Color.Transparent, 0.55f to Color.Transparent, 1f to (if (active) accent else Color.White).copy(alpha = if (active) 0.75f else 0.20f),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
                val lineA = Offset(mid.x, size.height * 0.10f)
                val lineB = Offset(mid.x, size.height * 0.23f)
                val lineC = Offset(mid.x, size.height * 0.77f)
                val lineD = Offset(mid.x, size.height * 0.90f)
                val wire = Color.White.copy(alpha = if (active) 0.20f else 0.07f)
                val dot = if (active) accent else Color.White.copy(alpha = 0.22f)
                onDrawWithContent {
                    drawPath(face, floorLight)
                    if (active) drawPath(face, glow)
                    drawLine(wire, lineA, lineB, 1.dp.toPx()); drawLine(wire, lineC, lineD, 1.dp.toPx())
                    drawCircle(dot, 1.6.dp.toPx(), lineB); drawCircle(dot, 1.6.dp.toPx(), lineC)
                    drawContent()
                    drawPath(face, shadowBrush, style = Stroke(18.dp.toPx()), alpha = 0.18f)
                    drawPath(face, shadowBrush, style = Stroke(11.dp.toPx()), alpha = 0.35f)
                    drawPath(face, shadowBrush, style = Stroke(5.dp.toPx()), alpha = 0.7f)
                    drawPath(face, litEdge, style = Stroke(2.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** An icon that glows: a soft halo behind it in its own colour. */
@Composable
fun GlowIcon(tint: Color, glow: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.drawBehind {
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = if (glow) 0.45f else 0.10f), Color.Transparent), center = center, radius = size.minDimension * 1.1f), radius = size.minDimension * 1.1f)
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
            val tint = if (active) accent else Color(0xFFD7D8DC)
            GlowIcon(tint, active) { icon(tint) }
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

/**
 * Seven tiles as a flower: one in the middle and six around it, interlocking like the cells of a honeycomb.
 * [tiles] holds exactly seven entries; index 3 is the centre, 0-2 the top row and right, 4-6 the rest, read in rows
 * of 2, 3, 2.
 */
@Composable
fun HexFlower(tileHeight: Dp, gap: Dp = 8.dp, tiles: List<@Composable () -> Unit>) {
    val w = hexWidth(tileHeight)
    val stepX = w + gap
    val stepY = tileHeight * 0.75f + gap * 0.87f
    Box(Modifier.size(stepX * 3 - gap, stepY * 2 + tileHeight)) {
        val spots = listOf(
            0.5f to 0, 1.5f to 0,
            0f to 1, 1f to 1, 2f to 1,
            0.5f to 2, 1.5f to 2,
        )
        spots.forEachIndexed { i, (col, row) ->
            Box(Modifier.offset(x = stepX * col, y = stepY * row)) { tiles[i]() }
        }
    }
}

/**
 * The shape of ARMOR's panels: a rectangle with its corners cut at an angle, so a stack of cards reads as elongated
 * hexagons. [cut] is how far each corner is chamfered.
 */
class HexPanelShape(private val cut: Dp = 18.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = minOf(with(density) { cut.toPx() }, size.height / 2f, size.width / 4f)
        return Outline.Generic(Path().apply {
            moveTo(c, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c)
            lineTo(size.width, size.height - c); lineTo(size.width - c, size.height); lineTo(c, size.height)
            lineTo(0f, size.height - c); lineTo(0f, c); close()
        })
    }
}

/**
 * A dark glass panel with cut corners, a thin warm-gradient border and a soft glow rising from the bottom edge, for
 * every card and text container. [glow] is the accent that rises from the bottom (orange by default).
 */
fun Modifier.hexPanel(glow: Color = ArmorColors.Orange, cut: Dp = 18.dp, strength: Float = 1f): Modifier = composed {
    val shape = HexPanelShape(cut)
    this
        .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = glow.copy(alpha = 0.6f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF17130F), Color(0xFF0C0B0B))))
        .drawBehind {
            // Warm light coming up from the bottom, strongest in the middle of the bottom edge.
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.34f * strength), glow.copy(alpha = 0.10f * strength), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 1.05f), radius = size.width * 0.75f,
                ),
            )
            // A faint highlight along the top.
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.05f), 0.35f to Color.Transparent))
        }
        .border(
            1.dp,
            Brush.linearGradient(listOf(glow.copy(alpha = 0.85f * strength), glow.copy(alpha = 0.18f), glow.copy(alpha = 0.55f * strength)), start = Offset.Zero, end = Offset.Infinite),
            shape,
        )
}

/** A faint scatter of dark hexagons of different sizes, soft shadows, a few thin glowing outlines and solid glowing ones. */
fun Modifier.hexScatter(accent: Color = ArmorColors.Orange): Modifier = drawWithCache {
    data class Cell(val x: Float, val y: Float, val r: Float, val kind: Int) // kind 0 dark, 1 outline, 2 solid glow
    val cells = listOf(
        Cell(0.88f, 0.06f, 0.20f, 0), Cell(0.10f, 0.14f, 0.09f, 0), Cell(0.62f, 0.02f, 0.07f, 1), Cell(0.96f, 0.32f, 0.12f, 1),
        Cell(0.04f, 0.46f, 0.15f, 0), Cell(0.93f, 0.62f, 0.07f, 2), Cell(0.84f, 0.80f, 0.22f, 0), Cell(0.12f, 0.78f, 0.08f, 2),
        Cell(0.30f, 0.94f, 0.12f, 1), Cell(0.58f, 0.99f, 0.09f, 0),
    )
    val w = size.width
    val h = size.height
    val unit = minOf(w, h)
    fun hex(cx: Float, cy: Float, r: Float): Path = Path().apply {
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90.0)
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val built = cells.map { c -> c to hex(c.x * w, c.y * h, c.r * unit * 1.3f) }
    onDrawBehind {
        for ((c, path) in built) {
            val cx = c.x * w; val cy = c.y * h; val r = c.r * unit * 1.3f
            when (c.kind) {
                0 -> {
                    // A recessed well: a light lip on the lower right, a dark floor, and an inner shadow along the upper left.
                    drawPath(hex(cx + r * 0.05f, cy + r * 0.08f, r), Color.White.copy(alpha = 0.10f))
                    drawPath(path, Brush.linearGradient(listOf(Color(0xFF0A0908), Color(0xFF151311), Color(0xFF1C1A18)), start = Offset(cx - r, cy - r), end = Offset(cx + r, cy + r)))
                    clipPath(path) {
                        val shade = Brush.linearGradient(
                            0f to Color.Black.copy(alpha = 0.95f), 0.5f to Color.Black.copy(alpha = 0.2f), 1f to Color.Transparent,
                            start = Offset(cx - r, cy - r), end = Offset(cx + r, cy + r),
                        )
                        drawPath(path, shade, style = Stroke(r * 0.5f), alpha = 0.22f)
                        drawPath(path, shade, style = Stroke(r * 0.3f), alpha = 0.4f)
                        drawPath(path, shade, style = Stroke(r * 0.12f), alpha = 0.8f)
                    }
                }
                1 -> drawPath(path, Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), accent.copy(alpha = 0.15f)), start = Offset(cx - r, cy - r), end = Offset(cx + r, cy + r)), style = Stroke(1.4f * 2))
                else -> {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r * 3f), r * 3f, Offset(cx, cy))
                    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF9A55), accent), startY = cy - r, endY = cy + r))
                }
            }
        }
    }
}
