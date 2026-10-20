package com.example.scanner.processing.filter

import android.graphics.Bitmap
import com.example.scanner.domain.model.ScanFilter
import com.example.scanner.processing.MatScope
import com.example.scanner.processing.OpenCv
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Scan-style image filters. All functions are CPU-heavy: call them off the main thread. */
object ImageFilters {

    /**
     * Returns a filtered copy of [src]. For [ScanFilter.ORIGINAL] (or if OpenCV failed to load) it
     * returns [src] itself, so callers must not recycle the input and output blindly.
     */
    fun apply(src: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL || !OpenCv.ready) return src
        MatScope().use { scope ->
            val rgb = OpenCv.rgbOf(src, scope)
            val result = when (filter) {
                ScanFilter.MAGIC_COLOR -> magicColor(rgb, scope)
                ScanFilter.BLACK_WHITE -> blackWhite(rgb, scope)
                ScanFilter.GRAYSCALE -> grayscale(rgb, scope)
                ScanFilter.ORIGINAL -> rgb
            }
            return OpenCv.toBitmap(result)
        }
    }

    private fun grayscale(rgb: Mat, scope: MatScope): Mat {
        val gray = scope.track(Mat())
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        return gray
    }

    /** Adaptive threshold: crisp black text on white regardless of uneven lighting. */
    private fun blackWhite(rgb: Mat, scope: MatScope): Mat {
        val gray = grayscale(rgb, scope)
        Imgproc.medianBlur(gray, gray, 3)

        // Block size scales with the page so text stroke width is handled at any resolution.
        var block = maxOf(gray.cols(), gray.rows()) / 40
        if (block % 2 == 0) block++
        block = block.coerceAtLeast(15)

        val bw = scope.track(Mat())
        Imgproc.adaptiveThreshold(
            gray, bw, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 12.0,
        )
        return bw
    }

    /**
     * Colour-preserving "scanner" look: estimates the paper/lighting background and divides it out,
     * which whitens the page and removes shadows, then adds a little contrast.
     */
    private fun magicColor(rgb: Mat, scope: MatScope): Mat {
        // Estimate the background on a small copy (fast), then scale it back up.
        val tiny = scope.track(Mat())
        val shrink = 1.0 / 8.0
        Imgproc.resize(rgb, tiny, Size(), shrink, shrink, Imgproc.INTER_AREA)

        // Dilation wipes out dark text so only paper colour / lighting remains.
        val kernel = scope.track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0)))
        Imgproc.dilate(tiny, tiny, kernel)
        Imgproc.GaussianBlur(tiny, tiny, Size(9.0, 9.0), 0.0)

        val background = scope.track(Mat())
        Imgproc.resize(tiny, background, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)

        // result = rgb / background * 255
        val flat = scope.track(Mat())
        Core.divide(rgb, background, flat, 255.0)

        val boosted = scope.track(Mat())
        flat.convertTo(boosted, -1, 1.12, -12.0)
        return boosted
    }
}
