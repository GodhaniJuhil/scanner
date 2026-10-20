package com.example.scanner.data.repository

import android.util.Log
import com.example.scanner.data.local.dao.DocumentDao
import com.example.scanner.processing.ocr.TextRecognizer
import com.example.scanner.util.loadUprightBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Recognizes text on saved pages in the background and stores it on the page rows.
 *
 * A page whose `ocrText` is null has not been processed yet; a page with no text gets an empty
 * string. That lets [resumePending] pick up work that was interrupted, for example if the app was
 * closed right after saving. Documents are processed one at a time.
 */
class BackgroundOcr(
    private val dao: DocumentDao,
    private val recognizer: TextRecognizer,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    fun schedule(documentId: Long) {
        scope.launch { lock.withLock { process(documentId) } }
    }

    /** Processes every document that still has unprocessed pages. */
    fun resumePending() {
        scope.launch {
            lock.withLock { dao.documentIdsWithPendingOcr().forEach { process(it) } }
        }
    }

    private suspend fun process(documentId: Long) {
        for (page in dao.getPages(documentId)) {
            if (page.ocrText != null) continue
            try {
                val bitmap = withContext(Dispatchers.IO) { loadUprightBitmap(page.imagePath, MAX_SIDE) }
                try {
                    dao.updateOcrText(page.id, recognizer.recognize(bitmap))
                } finally {
                    bitmap.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the page unprocessed so it is retried at the next start; keep going with the rest.
                Log.w(TAG, "OCR failed for page ${page.id} of document $documentId", e)
            }
        }
    }

    private companion object {
        const val TAG = "BackgroundOcr"
        const val MAX_SIDE = 2400
    }
}
