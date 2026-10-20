package com.example.scanner.domain.repository

import com.example.scanner.domain.model.DraftPage
import com.example.scanner.domain.model.ScannedDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    /** All documents, newest first. Pages are not loaded. */
    fun observeDocuments(): Flow<List<ScannedDocument>>

    /** One document with its pages, or null if it doesn't exist (or was deleted). */
    fun observeDocument(id: Long): Flow<ScannedDocument?>

    /** Builds the PDF and thumbnail, stores the pages, and records the document. */
    suspend fun createDocument(title: String, drafts: List<DraftPage>): ScannedDocument

    /**
     * Changes the title and renames the PDF file to match, so shared copies carry the new name.
     * @throws IllegalArgumentException if [title] is blank
     */
    suspend fun renameDocument(id: Long, title: String)

    suspend fun deleteDocument(id: Long)
}
