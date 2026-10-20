package com.example.scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.scanner.domain.model.Quad

/**
 * Draws [quad] (normalized coordinates) over a box of the same aspect ratio as the image the
 * quad was detected in.
 */
@Composable
fun QuadOverlay(
    quad: Quad?,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00E676),
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (quad == null) return@Canvas

        val pts = quad.points.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }

        drawPath(path, color.copy(alpha = 0.18f))
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
        pts.forEach { drawCircle(color, radius = 6.dp.toPx(), center = it) }
    }
}
