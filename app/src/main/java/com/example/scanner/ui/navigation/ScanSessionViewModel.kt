package com.example.scanner.ui.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner.ScannerApp
import com.example.scanner.domain.model.DraftPage
import com.example.scanner.ui.feature.camera.CaptureResult
import com.example.scanner.util.defaultDocumentTitle
import com.example.scanner.util.moved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the scan in progress, shared by the camera, edit and review screens: the photo just
 * taken and the pages already added. Held in memory only; leftover files are cleared at the
 * next app start.
 */
class ScanSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val container = getApplication<ScannerApp>().container
    private val fileStore = container.fileStore
    private val repository = container.repository

    /** The photo waiting to be edited. Read once when the edit screen opens. */
    var pendingCapture: CaptureResult? = null
        private set

    private val _drafts = MutableStateFlow<List<DraftPage>>(emptyList())
    val drafts: StateFlow<List<DraftPage>> = _drafts.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onCaptured(result: CaptureResult) {
        pendingCapture = result
    }

    fun addDraft(page: DraftPage) {
        _drafts.value = _drafts.value + page
    }

    /** Moves the page at [index] by [delta] positions (-1 = earlier, +1 = later). Out of range is ignored. */
    fun moveDraft(index: Int, delta: Int) {
        _drafts.value = _drafts.value.moved(index, delta)
    }

    fun removeDraft(index: Int) {
        val pages = _drafts.value.toMutableList()
        if (index !in pages.indices) return
        val removed = pages.removeAt(index)
        _drafts.value = pages
        viewModelScope.launch(Dispatchers.IO) { fileStore.deleteFiles(listOf(removed.imagePath)) }
    }

    /** Saves all pages, in their current order, as one document. [onSaved] gets the new document's id. */
    fun saveDrafts(onSaved: (Long) -> Unit) {
        val pages = _drafts.value
        if (pages.isEmpty() || _isSaving.value) return
        _isSaving.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val document = repository.createDocument(defaultDocumentTitle(), pages)
                finish()
                onSaved(document.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not save the document"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    /** Called after the pages were saved into a document (their files are already cleaned up). */
    fun finish() {
        _drafts.value = emptyList()
        pendingCapture = null
    }

    /** Abandons the scan and deletes its draft pages. */
    fun discard() {
        fileStore.deleteFiles(_drafts.value.map { it.imagePath })
        finish()
    }
}
