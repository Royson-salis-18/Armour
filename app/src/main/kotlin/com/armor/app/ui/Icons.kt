package com.armor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.armor.core.model.QuickStatus

/**
 * Monochrome line icons for the quick statuses, drawn here rather than using emoji so every icon takes the app's
 * colours (grey at rest, orange when selected) instead of the system's multicolour glyphs.
 */
@Composable
fun StatusIcon(status: QuickStatus, tint: Color, size: Dp = 24.dp) {
    if (status == QuickStatus.MECHANICAL) {
        Icon(Icons.Filled.Build, contentDescription = status.label, tint = tint, modifier = Modifier.size(size))
        return
    }
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val st = Stroke(1.8f * u, cap = StrokeCap.Round)
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        when (status) {
            QuickStatus.FUEL -> {
                drawRoundRect(tint, o(4f, 3f), Size(10f * u, 18f * u), CornerRadius(2f * u), style = st)
                drawRoundRect(tint, o(6.5f, 6f), Size(5f * u, 4f * u), CornerRadius(1f * u), style = st)
                drawLine(tint, o(14f, 9f), o(18f, 9f), 1.8f * u, StrokeCap.Round)
                drawLine(tint, o(18f, 9f), o(18f, 17f), 1.8f * u, StrokeCap.Round)
                drawLine(tint, o(2.5f, 21f), o(15.5f, 21f), 1.8f * u, StrokeCap.Round)
            }
            QuickStatus.BREAK -> {
                drawRoundRect(tint, o(3f, 8f), Size(12f * u, 11f * u), CornerRadius(2.5f * u), style = st)
                drawArc(tint, -90f, 180f, false, o(13f, 10f), Size(6f * u, 6f * u), style = st)
                drawLine(tint, o(6f, 3.5f), o(6f, 5.5f), 1.8f * u, StrokeCap.Round)
                drawLine(tint, o(9f, 3f), o(9f, 5.5f), 1.8f * u, StrokeCap.Round)
                drawLine(tint, o(12f, 3.5f), o(12f, 5.5f), 1.8f * u, StrokeCap.Round)
            }
            QuickStatus.TRAFFIC -> {
                drawRoundRect(tint, o(7f, 2.5f), Size(10f * u, 19f * u), CornerRadius(3f * u), style = st)
                drawCircle(tint, 1.6f * u, o(12f, 7f), style = st)
                drawCircle(tint, 1.6f * u, o(12f, 12f), style = st)
                drawCircle(tint, 1.6f * u, o(12f, 17f), style = st)
            }
            QuickStatus.PHOTO -> {
                drawRoundRect(tint, o(2.5f, 6.5f), Size(19f * u, 13f * u), CornerRadius(3f * u), style = st)
                drawCircle(tint, 3.6f * u, o(12f, 13f), style = st)
                drawLine(tint, o(8.5f, 6.5f), o(10f, 4f), 1.8f * u, StrokeCap.Round)
                drawLine(tint, o(10f, 4f), o(14f, 4f), 1.8f * u, StrokeCap.Round)
                drawLine(tint, o(14f, 4f), o(15.5f, 6.5f), 1.8f * u, StrokeCap.Round)
            }
            QuickStatus.MECHANICAL -> Unit
        }
    }
}
