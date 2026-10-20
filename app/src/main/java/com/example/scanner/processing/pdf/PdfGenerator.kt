package com.example.scanner.processing.pdf

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.scanner.util.loadUprightBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Builds a PDF from page images using the platform [PdfDocument], with one image per page.
 *
 * Every page is 595pt wide (A4 width) and as tall as the image's aspect ratio needs, so nothing is
 * cropped or letterboxed. Pages are decoded one at a time to keep memory use flat.
 * The pages are images only: the PDF has no selectable text layer.
 */
class PdfGenerator {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Blocking; call from a background dispatcher. */
    fun generate(pages: List<File>, output: File) {
        require(pages.isNotEmpty()) { "A PDF needs at least one page" }

        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, file ->
                val bitmap = loadUprightBitmap(file.path, MAX_IMAGE_SIDE)
                try {
                    val height = (PAGE_WIDTH * bitmap.height / bitmap.width.toFloat()).roundToInt().coerceAtLeast(1)
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, height, index + 1).create())
                    page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, PAGE_WIDTH.toFloat(), height.toFloat()), paint)
                    pdf.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val MAX_IMAGE_SIDE = 2400
    }
}
