package com.example.scanner.ui.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.scanner.domain.model.DraftPage
import com.example.scanner.domain.model.NormPoint
import com.example.scanner.domain.model.Quad
import com.example.scanner.domain.model.ScanPage
import com.example.scanner.domain.model.ScannedDocument

/**
 * Fake content for Compose `@Preview`s, so screens can be viewed in Android Studio without a
 * camera, a database or real files. Not used at runtime.
 */
internal object SampleData {

    /** A slightly skewed page, as edge detection might report it. */
    val quad = Quad(
        topLeft = NormPoint(0.14f, 0.12f),
        topRight = NormPoint(0.86f, 0.08f),
        bottomRight = NormPoint(0.90f, 0.90f),
        bottomLeft = NormPoint(0.10f, 0.93f),
    )

    private const val JAN_1_2026 = 1_767_225_600_000L
    private const val DAY = 86_400_000L

    /** A flat, cropped page: heading, paragraphs and a signature line, drawn as grey bars. */
    fun pageBitmap(width: Int = 900, height: Int = 1200): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 248, 242))
        drawText(canvas, RectF(width * 0.1f, height * 0.08f, width * 0.9f, height * 0.9f), width)
        return bitmap
    }

    /** A photo of a tilted page on a wooden table, matching [quad]. */
    fun photoBitmap(width: Int = 900, height: Int = 1200): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(120, 86, 58))

        val corners = quad.points
        val page = Path().apply {
            moveTo(corners[0].x * width, corners[0].y * height)
            for (i in 1 until corners.size) lineTo(corners[i].x * width, corners[i].y * height)
            close()
        }
        canvas.drawPath(page, Paint().apply { color = Color.rgb(250, 248, 242); isAntiAlias = true })

        val bounds = RectF(width * 0.2f, height * 0.2f, width * 0.78f, height * 0.8f)
        drawText(canvas, bounds, width)
        return bitmap
    }

    private fun drawText(canvas: Canvas, area: RectF, width: Int) {
        val bar = Paint().apply { color = Color.rgb(70, 70, 76); isAntiAlias = true }
        val lineHeight = width * 0.022f
        // Heading
        canvas.drawRoundRect(area.left, area.top, area.left + area.width() * 0.55f, area.top + lineHeight * 2, 6f, 6f, bar)
        var y = area.top + lineHeight * 5
        var row = 0
        while (y < area.bottom - lineHeight * 4) {
            // Every sixth row is short, which makes the bars look like paragraphs.
            val length = if (row % 6 == 5) 0.4f else 1f - (row % 3) * 0.04f
            canvas.drawRoundRect(area.left, y, area.left + area.width() * length, y + lineHeight, 4f, 4f, bar)
            y += lineHeight * 1.9f
            if (row % 6 == 5) y += lineHeight * 1.5f
            row++
        }
    }

    fun draftPages(count: Int = 3): List<DraftPage> =
        List(count) { DraftPage(imagePath = "/sample/page_${it + 1}.jpg", ocrText = null) }

    private val sampleText = """
        Invoice #2026-0142
        Date: 1 October 2026

        Consulting services ........ 1,200.00
        Travel expenses ............. 148.50
        Total due ................... 1,348.50
    """.trimIndent()

    fun pages(): List<ScanPage> = listOf(
        ScanPage(index = 0, imagePath = "/sample/page_1.jpg", ocrText = sampleText),
        ScanPage(index = 1, imagePath = "/sample/page_2.jpg", ocrText = ""),
        ScanPage(index = 2, imagePath = "/sample/page_3.jpg", ocrText = null),
    )

    fun document(title: String = "Scan 2026-10-02 10-30", pages: List<ScanPage> = pages()) = ScannedDocument(
        id = 1,
        title = title,
        createdAt = JAN_1_2026 + 274 * DAY,
        pageCount = pages.size,
        pdfPath = "/sample/$title.pdf",
        thumbnailPath = null,
        pages = pages,
    )

    fun documents(): List<ScannedDocument> = listOf(
        document("Rental agreement").copy(id = 3, createdAt = JAN_1_2026 + 275 * DAY),
        document("Scan 2026-10-02 10-30", pages().take(1)).copy(id = 2, createdAt = JAN_1_2026 + 274 * DAY),
        document("Electricity bill September with a very long title that should be cut off").copy(
            id = 1,
            createdAt = JAN_1_2026 + 260 * DAY,
        ),
    )
}
