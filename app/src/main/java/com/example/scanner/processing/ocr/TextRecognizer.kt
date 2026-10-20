package com.example.scanner.processing.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspending wrapper around the on-device ML Kit Latin text recognizer.
 * Keep one instance for the app's lifetime and call [close] when done with it.
 *
 * The bitmap must stay valid (not recycled) until [recognize] returns.
 */
class TextRecognizer : AutoCloseable {

    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Returns the recognized text in reading order, or an empty string if none was found. */
    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    override fun close() = client.close()
}
