package com.example.scanner.data.repository

import com.example.scanner.data.file.ScanFileStore
import com.example.scanner.data.local.dao.DocumentDao
import com.example.scanner.data.local.entity.DocumentEntity
import com.example.scanner.data.local.entity.DocumentWithPages
import com.example.scanner.data.local.entity.PageEntity
import com.example.scanner.domain.model.DraftPage
import com.example.scanner.domain.model.ScanPage
import com.example.scanner.domain.model.ScannedDocument
import com.example.scanner.domain.repository.DocumentRepository
import com.example.scanner.processing.pdf.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DocumentRepositoryImpl(
    private val dao: DocumentDao,
    private val files: ScanFileStore,
    private val pdfGenerator: PdfGenerator,
    private val backgroundOcr: BackgroundOcr,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<ScannedDocument>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDocument(id: Long): Flow<ScannedDocument?> =
        dao.observeWithPages(id).map { it?.toDomain() }

    override suspend fun createDocument(title: String, drafts: List<DraftPage>): ScannedDocument =
        withContext(Dispatchers.IO) {
            require(drafts.isNotEmpty()) { "Cannot create an empty document" }

            // The row comes first because its id names the document's folder.
            val id = dao.insertDocument(
                DocumentEntity(
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    pageCount = drafts.size,
                    pdfPath = "",
                    thumbnailPath = null,
                ),
            )
            try {
                val pageFiles = drafts.mapIndexed { index, draft ->
                    files.copyDraftIntoDocument(File(draft.imagePath), id, index)
                }
                val pdf = files.pdfFile(id, title)
                pdfGenerator.generate(pageFiles, pdf)
                val thumbnail = files.createThumbnail(pageFiles.first(), id)

                dao.insertPages(
                    pageFiles.mapIndexed { index, file ->
                        PageEntity(
                            documentId = id,
                            pageIndex = index,
                            imagePath = file.absolutePath,
                            ocrText = drafts[index].ocrText,
                        )
                    },
                )
                val saved = DocumentEntity(
                    id = id,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    pageCount = drafts.size,
                    pdfPath = pdf.absolutePath,
                    thumbnailPath = thumbnail.absolutePath,
                )
                dao.updateDocument(saved)

                // Drafts are only removed once everything succeeded, so a failure can be retried.
                files.deleteFiles(drafts.map { it.imagePath })

                // Text recognition runs after saving so the user isn't kept waiting for it.
                backgroundOcr.schedule(id)
                saved.toDomain()
            } catch (e: Throwable) {
                dao.deleteById(id)
                files.deleteDocumentFiles(id)
                throw e
            }
        }

    override suspend fun renameDocument(id: Long, title: String) {
        val newTitle = title.trim()
        require(newTitle.isNotEmpty()) { "Title cannot be empty" }

        withContext(Dispatchers.IO) {
            val document = dao.getById(id) ?: return@withContext
            val oldPdf = File(document.pdfPath)
            val newPdf = files.pdfFile(id, newTitle)

            val movePdf = oldPdf.exists() && oldPdf.absolutePath != newPdf.absolutePath
            if (movePdf && !oldPdf.renameTo(newPdf)) throw IOException("Could not rename the PDF file")
            try {
                dao.updateTitleAndPdfPath(id, newTitle, newPdf.absolutePath)
            } catch (e: Throwable) {
                if (movePdf) newPdf.renameTo(oldPdf)
                throw e
            }
        }
    }

    override suspend fun deleteDocument(id: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteById(id)
            files.deleteDocumentFiles(id)
        }
    }

    private fun DocumentEntity.toDomain() = ScannedDocument(
        id = id,
        title = title,
        createdAt = createdAt,
        pageCount = pageCount,
        pdfPath = pdfPath,
        thumbnailPath = thumbnailPath,
    )

    private fun DocumentWithPages.toDomain() = document.toDomain().copy(
        pages = pages.sortedBy { it.pageIndex }
            .map { ScanPage(index = it.pageIndex, imagePath = it.imagePath, ocrText = it.ocrText) },
    )
}
