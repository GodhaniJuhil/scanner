package com.example.scanner.processing.edge

import android.graphics.Bitmap
import com.example.scanner.domain.model.NormPoint
import com.example.scanner.domain.model.Quad
import com.example.scanner.processing.MatScope
import com.example.scanner.processing.OpenCv
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Straightens the area inside a [Quad] into a flat, rectangular page. */
object PerspectiveCropper {

    /**
     * @param quad corners in normalized coordinates of [src]
     * @param maxSide cap on the longer output side, to bound memory use
     * @return a new bitmap; [src] is left untouched. Returns [src] itself if OpenCV is unavailable.
     */
    fun crop(src: Bitmap, quad: Quad, maxSide: Int = 3000): Bitmap {
        if (!OpenCv.ready) return src

        val corners = quad.points.map { it.toPixel(src.width, src.height) }
        val (tl, tr, br, bl) = corners

        var outW = maxOf(distance(tl, tr), distance(bl, br))
        var outH = maxOf(distance(tl, bl), distance(tr, br))
        val shrink = minOf(1.0, maxSide / maxOf(outW, outH))
        outW *= shrink
        outH *= shrink
        val width = outW.roundToInt().coerceAtLeast(1)
        val height = outH.roundToInt().coerceAtLeast(1)

        MatScope().use { scope ->
            val source = OpenCv.rgbOf(src, scope)
            val from = scope.track(MatOfPoint2f(*corners.toTypedArray()))
            val to = scope.track(
                MatOfPoint2f(
                    Point(0.0, 0.0),
                    Point(width - 1.0, 0.0),
                    Point(width - 1.0, height - 1.0),
                    Point(0.0, height - 1.0),
                ),
            )
            val transform: Mat = scope.track(Imgproc.getPerspectiveTransform(from, to))
            val warped = scope.track(Mat())
            Imgproc.warpPerspective(source, warped, transform, Size(width.toDouble(), height.toDouble()), Imgproc.INTER_LINEAR)
            return OpenCv.toBitmap(warped)
        }
    }

    private fun NormPoint.toPixel(w: Int, h: Int) = Point(x.toDouble() * w, y.toDouble() * h)

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
