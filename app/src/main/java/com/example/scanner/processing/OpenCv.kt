package com.example.scanner.processing

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** One-time OpenCV native library loading plus Bitmap <-> Mat helpers. */
object OpenCv {
    val ready: Boolean by lazy { OpenCVLoader.initLocal() }

    /** Converts an ARGB_8888 bitmap to a 3-channel RGB Mat, tracked by [scope]. */
    fun rgbOf(bitmap: Bitmap, scope: MatScope): Mat {
        val rgba = scope.track(Mat())
        Utils.bitmapToMat(bitmap, rgba)
        val rgb = scope.track(Mat())
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        return rgb
    }

    /** Converts a 1-, 3- (RGB) or 4-channel 8-bit Mat to a new ARGB_8888 bitmap. */
    fun toBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
}

/** Collects native Mats so they are all released together, even if processing throws. */
class MatScope : AutoCloseable {
    private val mats = ArrayList<Mat>()

    fun <T : Mat> track(mat: T): T {
        mats += mat
        return mat
    }

    override fun close() {
        mats.forEach { it.release() }
        mats.clear()
    }
}
