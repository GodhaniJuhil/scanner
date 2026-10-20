package com.example.scanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scanner.data.local.ScannerDatabase
import com.example.scanner.data.local.dao.DocumentDao
import com.example.scanner.data.local.entity.DocumentEntity
import com.example.scanner.data.local.entity.PageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentDaoTest {

    private lateinit var db: ScannerDatabase
    private lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScannerDatabase::class.java).build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertDocument(title: String, pageTexts: List<String?>): Long {
        val id = dao.insertDocument(
            DocumentEntity(
                title = title,
                createdAt = 1L,
                pageCount = pageTexts.size,
                pdfPath = "/old/$title.pdf",
                thumbnailPath = null,
            ),
        )
        dao.insertPages(
            pageTexts.mapIndexed { index, text ->
                PageEntity(documentId = id, pageIndex = index, imagePath = "/p$index.jpg", ocrText = text)
            },
        )
        return id
    }

    @Test
    fun updateTitleAndPdfPathChangesOnlyThoseFields() = runBlocking {
        val id = insertDocument("Old", listOf("text"))

        dao.updateTitleAndPdfPath(id, "New", "/new/New.pdf")

        val saved = dao.getById(id)!!
        assertEquals("New", saved.title)
        assertEquals("/new/New.pdf", saved.pdfPath)
        assertEquals(1, saved.pageCount)
        assertEquals(1L, saved.createdAt)
    }

    @Test
    fun deletingDocumentAlsoDeletesItsPages() = runBlocking {
        val id = insertDocument("Doc", listOf("a", "b"))
        assertEquals(2, dao.getPages(id).size)

        dao.deleteById(id)

        assertNull(dao.getById(id))
        assertTrue(dao.getPages(id).isEmpty())
    }

    @Test
    fun pendingOcrListsOnlyDocumentsWithUnprocessedPages() = runBlocking {
        val done = insertDocument("Done", listOf("text", ""))      // "" means processed, no text found
        val pending = insertDocument("Pending", listOf("text", null))

        assertEquals(listOf(pending), dao.documentIdsWithPendingOcr())

        val unprocessedPage = dao.getPages(pending).single { it.ocrText == null }
        dao.updateOcrText(unprocessedPage.id, "now read")

        assertTrue(dao.documentIdsWithPendingOcr().isEmpty())
        assertEquals("now read", dao.getPages(pending).last().ocrText)
        assertEquals("", dao.getPages(done).last().ocrText)
    }

    @Test
    fun observedDocumentReturnsPagesInOrder() = runBlocking {
        val id = insertDocument("Doc", listOf("first", "second", "third"))

        val withPages = dao.observeWithPages(id).first()!!

        assertEquals(listOf("first", "second", "third"), withPages.pages.sortedBy { it.pageIndex }.map { it.ocrText })
    }
}
