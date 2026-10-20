package com.example.scanner.di

import android.content.Context
import androidx.room.Room
import com.example.scanner.data.file.ScanFileStore
import com.example.scanner.data.local.ScannerDatabase
import com.example.scanner.data.repository.BackgroundOcr
import com.example.scanner.data.repository.DocumentRepositoryImpl
import com.example.scanner.domain.repository.DocumentRepository
import com.example.scanner.processing.ocr.TextRecognizer
import com.example.scanner.processing.pdf.PdfGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; one instance lives in [com.example.scanner.ScannerApp]. */
class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, ScannerDatabase::class.java, "scanner.db").build()

    /** For work that must outlive any screen, such as recognizing text after a document is saved. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val fileStore = ScanFileStore(context)
    val backgroundOcr = BackgroundOcr(database.documentDao(), TextRecognizer(), appScope)
    val repository: DocumentRepository =
        DocumentRepositoryImpl(database.documentDao(), fileStore, PdfGenerator(), backgroundOcr)
}
