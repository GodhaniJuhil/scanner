package com.example.scanner.processing.edge

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.scanner.domain.model.NormPoint
import com.example.scanner.domain.model.Quad
import com.example.scanner.processing.MatScope
import com.example.scanner.processing.OpenCv
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds the largest convex four-sided contour in an image.
 *
 * Camera frames are read from the Y (luma) plane of a YUV_420_888 image, so no colour conversion is
 * needed. Every input is downscaled and rotated upright before detection, and the result is returned
 * in normalized coordinates, so callers don't need to know the resolution or rotation.
 *
 * Not thread-safe; call from a single thread.
 */
class DocumentEdgeDetector {

    /** Live detection on a camera analysis frame. */
    fun detect(image: ImageProxy): Quad? {
        if (!OpenCv.ready) return null
        MatScope().use { scope ->
            val luma = scope.track(lumaToMat(image))
            val small = downscale(luma, scope)
            val upright = rotateUpright(small, image.imageInfo.rotationDegrees, scope)
            return findQuad(upright, scope)
        }
    }

    /** Detection on a still photo (already upright), e.g. when the capture had no live outline. */
    fun detect(bitmap: Bitmap): Quad? {
        if (!OpenCv.ready) return null
        MatScope().use { scope ->
            val rgba = scope.track(Mat())
            Utils.bitmapToMat(bitmap, rgba)
            val gray = scope.track(Mat())
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return findQuad(downscale(gray, scope), scope)
        }
    }

    private fun findQuad(upright: Mat, scope: MatScope): Quad? {
        val w = upright.cols()
        val h = upright.rows()

        val blurred = scope.track(Mat())
        Imgproc.GaussianBlur(upright, blurred, Size(5.0, 5.0), 0.0)

        val edges = scope.track(Mat())
        Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)

        // Close small gaps so a document outline forms one connected contour.
        val kernel = scope.track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        Imgproc.dilate(edges, edges, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = scope.track(Mat())
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val frameArea = w.toDouble() * h
        var bestArea = 0.0
        var bestCorners: Array<Point>? = null

        for (contour in contours) {
            val contour2f = scope.track(MatOfPoint2f(*contour.toArray()))
            contour.release()

            val area = Imgproc.contourArea(contour2f)
            if (area < frameArea * MIN_AREA_RATIO || area > frameArea * MAX_AREA_RATIO) continue
            if (area <= bestArea) continue

            val approx = scope.track(MatOfPoint2f())
            Imgproc.approxPolyDP(contour2f, approx, APPROX_EPSILON * Imgproc.arcLength(contour2f, true), true)
            if (approx.total() != 4L) continue

            val approxInt = scope.track(MatOfPoint(*approx.toArray()))
            if (!Imgproc.isContourConvex(approxInt)) continue

            bestArea = area
            bestCorners = approx.toArray()
        }

        return bestCorners?.let { orderCorners(it, w, h) }
    }

    private fun downscale(src: Mat, scope: MatScope): Mat {
        val scale = minOf(1.0, TARGET_LONG_SIDE / maxOf(src.cols(), src.rows()))
        val dst = scope.track(Mat())
        Imgproc.resize(src, dst, Size(), scale, scale, Imgproc.INTER_AREA)
        return dst
    }

    /** Copies the Y plane into a tightly-packed single-channel Mat, honouring the row stride. */
    private fun lumaToMat(image: ImageProxy): Mat {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride

        val buffer = plane.buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val padded = Mat(height, rowStride, CvType.CV_8UC1)
        padded.put(0, 0, bytes)
        if (rowStride == width) return padded

        // submat is a view over `padded`; clone so we can release the padded buffer.
        val cropped = padded.submat(0, height, 0, width).clone()
        padded.release()
        return cropped
    }

    private fun rotateUpright(src: Mat, rotationDegrees: Int, scope: MatScope): Mat {
        val dst = scope.track(Mat())
        val code = when (rotationDegrees) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> {
                src.copyTo(dst)
                return dst
            }
        }
        Core.rotate(src, dst, code)
        return dst
    }

    /** Orders four arbitrary points as TL, TR, BR, BL and normalizes them to 0..1. */
    private fun orderCorners(pts: Array<Point>, w: Int, h: Int): Quad {
        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.minBy { it.y - it.x }
        val bl = pts.maxBy { it.y - it.x }

        fun Point.norm() = NormPoint(
            (x / w).toFloat().coerceIn(0f, 1f),
            (y / h).toFloat().coerceIn(0f, 1f),
        )
        return Quad(tl.norm(), tr.norm(), br.norm(), bl.norm())
    }

    private companion object {
        const val TARGET_LONG_SIDE = 480.0
        const val CANNY_LOW = 60.0
        const val CANNY_HIGH = 160.0
        const val APPROX_EPSILON = 0.02
        const val MIN_AREA_RATIO = 0.15
        const val MAX_AREA_RATIO = 0.95
    }
}
