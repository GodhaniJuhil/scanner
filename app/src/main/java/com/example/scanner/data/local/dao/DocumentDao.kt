package com.example.scanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.scanner.data.local.entity.DocumentEntity
import com.example.scanner.data.local.entity.DocumentWithPages
import com.example.scanner.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeWithPages(id: Long): Flow<DocumentWithPages?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex")
    suspend fun getPages(documentId: Long): List<PageEntity>

    /** Documents that still have at least one page whose text has not been recognized yet. */
    @Query("SELECT DISTINCT documentId FROM pages WHERE ocrText IS NULL")
    suspend fun documentIdsWithPendingOcr(): List<Long>

    @Insert
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET title = :title, pdfPath = :pdfPath WHERE id = :id")
    suspend fun updateTitleAndPdfPath(id: Long, title: String, pdfPath: String)

    @Query("UPDATE pages SET ocrText = :text WHERE id = :pageId")
    suspend fun updateOcrText(pageId: Long, text: String)

    /** Pages are removed by the foreign key's ON DELETE CASCADE. */
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}
