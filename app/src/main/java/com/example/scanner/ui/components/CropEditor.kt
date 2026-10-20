package com.example.scanner.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scanner.domain.model.NormPoint
import com.example.scanner.domain.model.Quad
import com.example.scanner.ui.preview.SampleData
import com.example.scanner.ui.theme.ScannerTheme
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private const val LOUPE_ZOOM = 2.5f

/**
 * Shows [image] with a four-corner outline the user can adjust by dragging the corners.
 *
 * [quad] is in normalized image coordinates. Corners stay inside the image, and a drag that would
 * make the outline non-convex is ignored. Drags start from the corner nearest the finger within
 * a generous touch radius, so the (small) handles are easy to grab.
 *
 * While a corner is being dragged a magnifier ("loupe") floats above it, because the finger
 * covers the spot being adjusted.
 */
@Composable
fun CropEditor(
    image: Bitmap,
    quad: Quad,
    onQuadChange: (Quad) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00E676),
) {
    val imageBitmap = remember(image) { image.asImageBitmap() }
    val currentQuad by rememberUpdatedState(quad)
    val currentOnChange by rememberUpdatedState(onQuadChange)
    // Index of the corner being dragged (0=TL, 1=TR, 2=BR, 3=BL), or -1. Read while drawing the loupe.
    var activeCorner by mutableIntStateOf(-1)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Where the image lands inside this box (it is drawn with ContentScale.Fit, centred).
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / image.width, boxH / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val left = (boxW - w) / 2f
        val top = (boxH - h) / 2f

        Image(
            bitmap = imageBitmap,
            contentDescription = "Photo to crop",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(w, h, left, top) {
                    val touchRadius = 56.dp.toPx()
                    var dragQuad = currentQuad

                    detectDragGestures(
                        onDragStart = { start ->
                            dragQuad = currentQuad
                            val corners = dragQuad.points
                            fun distanceTo(i: Int) =
                                hypot(left + corners[i].x * w - start.x, top + corners[i].y * h - start.y)
                            val nearest = corners.indices.minBy(::distanceTo)
                            activeCorner = if (distanceTo(nearest) <= touchRadius) nearest else -1
                        },
                        onDragEnd = { activeCorner = -1 },
                        onDragCancel = { activeCorner = -1 },
                    ) { change, drag ->
                        val index = activeCorner
                        if (index < 0) return@detectDragGestures
                        change.consume()

                        val p = dragQuad.points[index]
                        val moved = NormPoint(
                            (p.x + drag.x / w).coerceIn(0f, 1f),
                            (p.y + drag.y / h).coerceIn(0f, 1f),
                        )
                        val candidate = dragQuad.withPoint(index, moved)
                        if (candidate.isConvex()) {
                            dragQuad = candidate
                            currentOnChange(candidate)
                        }
                    }
                },
        ) {
            val corners = quad.points.map { Offset(left + it.x * w, top + it.y * h) }
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                for (i in 1 until corners.size) lineTo(corners[i].x, corners[i].y)
                close()
            }

            // Dim everything outside the outline: the image rectangle minus the quad.
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(left, top, left + w, top + h))
                addPath(outline)
            }
            drawPath(dim, Color.Black.copy(alpha = 0.5f))
            drawPath(outline, color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

            corners.forEach { corner ->
                drawCircle(Color.White, radius = 12.dp.toPx(), center = corner)
                drawCircle(color, radius = 12.dp.toPx(), center = corner, style = Stroke(width = 3.dp.toPx()))
            }

            val active = activeCorner
            if (active >= 0) {
                val target = corners[active]
                val radius = 56.dp.toPx()

                // Float the loupe above the finger; if that would leave the top of the view, go below.
                val clearance = radius + 64.dp.toPx()
                val aboveY = target.y - clearance
                val centerY = if (aboveY - radius >= 0f) aboveY else target.y + clearance
                val centerX = target.x.coerceIn(radius, size.width - radius)
                val loupeCenter = Offset(centerX, centerY)

                val circle = Path().apply { addOval(Rect(center = loupeCenter, radius = radius)) }
                drawCircle(Color.Black, radius = radius, center = loupeCenter)
                clipPath(circle) {
                    // Map the dragged corner onto the loupe centre, then magnify around it.
                    withTransform({
                        translate(loupeCenter.x - target.x, loupeCenter.y - target.y)
                        scale(LOUPE_ZOOM, LOUPE_ZOOM, pivot = target)
                    }) {
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                        // Divide by the zoom so the outline keeps the same on-screen thickness.
                        drawPath(outline, color, style = Stroke(width = 2.dp.toPx() / LOUPE_ZOOM, join = StrokeJoin.Round))
                    }
                }
                drawCircle(Color.White, radius = radius, center = loupeCenter, style = Stroke(width = 3.dp.toPx()))

                val arm = 10.dp.toPx()
                val crosshair = 1.5.dp.toPx()
                drawLine(color, loupeCenter - Offset(arm, 0f), loupeCenter + Offset(arm, 0f), crosshair)
                drawLine(color, loupeCenter - Offset(0f, arm), loupeCenter + Offset(0f, arm), crosshair)
            }
        }
    }
}

/** Use Interactive mode, then drag a corner to see the loupe. */
@Preview(showBackground = true, widthDp = 360, heightDp = 560)
@Composable
private fun CropEditorPreview() {
    val photo = remember { SampleData.photoBitmap() }
    var quad by remember { mutableStateOf(SampleData.quad) }
    ScannerTheme {
        CropEditor(image = photo, quad = quad, onQuadChange = { quad = it }, modifier = Modifier.fillMaxSize())
    }
}
