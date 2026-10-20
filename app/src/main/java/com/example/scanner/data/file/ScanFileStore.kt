package com.example.scanner.data.file

import android.content.Context
import android.graphics.Bitmap
import com.example.scanner.util.loadUprightBitmap
import com.example.scanner.util.scaledToMaxSide
import com.example.scanner.util.toSafeFileName
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns every file the app writes, all in app-private storage:
 *
 *  - `files/drafts/`          pages processed in the current scan, not yet saved
 *  - `files/scans/{docId}/`   saved pages, thumbnail and the PDF of one document
 *  - `cache/captures/`        raw camera photos (see CameraViewModel)
 *
 * `files/scans` is what the FileProvider exposes for sharing (see res/xml/file_paths.xml).
 */
class ScanFileStore(context: Context) {

    private val scansRoot = File(context.filesDir, "scans")
    private val draftsDir = File(context.filesDir, "drafts")
    private val captureDir = File(context.cacheDir, "captures")

    fun newDraftFile(): File {
        draftsDir.mkdirs()
        return File(draftsDir, "draft_${UUID.randomUUID()}.jpg")
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = JPEG_QUALITY) {
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Could not write image" }
        }
    }

    private fun documentDir(docId: Long) = File(scansRoot, docId.toString())

    /** Copies a draft into the document's folder as `page_N.jpg`. The draft is left in place. */
    fun copyDraftIntoDocument(draft: File, docId: Long, index: Int): File {
        val dir = documentDir(docId).apply { mkdirs() }
        return draft.copyTo(File(dir, "page_${index + 1}.jpg"), overwrite = true)
    }

    /** The PDF is named after the document so that shared copies have a meaningful file name. */
    fun pdfFile(docId: Long, title: String): File {
        return File(documentDir(docId).apply { mkdirs() }, title.toSafeFileName() + ".pdf")
    }

    fun createThumbnail(firstPage: File, docId: Long): File {
        val bitmap = loadUprightBitmap(firstPage.path, THUMBNAIL_SIDE * 2).scaledToMaxSide(THUMBNAIL_SIDE)
        val file = File(documentDir(docId).apply { mkdirs() }, "thumb.jpg")
        saveJpeg(bitmap, file, quality = 80)
        return file
    }

    fun deleteDocumentFiles(docId: Long) {
        documentDir(docId).deleteRecursively()
    }

    fun deleteFiles(paths: List<String>) {
        paths.forEach { File(it).delete() }
    }

    /** Removes leftovers from a previous run; the in-progress scan is only held in memory. */
    fun clearTemporaryFiles() {
        draftsDir.deleteRecursively()
        captureDir.deleteRecursively()
    }

    private companion object {
        const val JPEG_QUALITY = 90
        const val THUMBNAIL_SIDE = 320
    }
}
