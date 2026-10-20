package com.example.scanner.domain.model

/**
 * A saved scan. [pages] is only populated when a single document is loaded;
 * list queries leave it empty and use [pageCount] instead.
 */
data class ScannedDocument(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val pageCount: Int,
    val pdfPath: String,
    val thumbnailPath: String?,
    val pages: List<ScanPage> = emptyList(),
)

data class ScanPage(
    val index: Int,
    val imagePath: String,
    val ocrText: String?,
)

/** A processed page that has been written to disk but not yet saved into a document. */
data class DraftPage(
    val imagePath: String,
    val ocrText: String?,
)
