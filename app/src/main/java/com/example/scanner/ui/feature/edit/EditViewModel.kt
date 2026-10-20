package com.example.scanner.ui.feature.edit

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner.ScannerApp
import com.example.scanner.domain.model.DraftPage
import com.example.scanner.domain.model.Quad
import com.example.scanner.domain.model.ScanFilter
import com.example.scanner.processing.edge.DocumentEdgeDetector
import com.example.scanner.processing.edge.PerspectiveCropper
import com.example.scanner.processing.filter.ImageFilters
import com.example.scanner.processing.ocr.TextRecognizer
import com.example.scanner.util.defaultDocumentTitle
import com.example.scanner.util.loadUprightBitmap
import com.example.scanner.util.rotated
import com.example.scanner.util.scaledToMaxSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditUiState(
    val isLoading: Boolean = true,
    /** Downscaled, filtered, rotated image for on-screen display. */
    val preview: Bitmap? = null,
    val filter: ScanFilter = ScanFilter.MAGIC_COLOR,
    val rotationDegrees: Int = 0,
    val isOcrRunning: Boolean = false,
    val ocrText: String? = null,
    /** True while the page is being rendered at full resolution and stored. */
    val isSaving: Boolean = false,
    val error: String? = null,
    /** True while the user is adjusting the crop outline instead of viewing the result. */
    val isCropping: Boolean = false,
    /** The whole (uncropped) photo, downscaled, shown behind the outline while cropping. */
    val cropImage: Bitmap? = null,
    /** The outline being edited; only applied when the user confirms. Normalized to [cropImage]. */
    val cropQuad: Quad = Quad.FULL,
)

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val container = getApplication<ScannerApp>().container
    private val fileStore = container.fileStore
    private val repository = container.repository

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val recognizer = TextRecognizer()

    private var loadedPath: String? = null
    private var source: Bitmap? = null         // the whole photo, upright; kept so the crop can be redone
    private var autoQuad: Quad = Quad.FULL     // outline found automatically (or the live one)
    private var appliedQuad: Quad = Quad.FULL  // outline `cropped` was made from
    private var cropped: Bitmap? = null        // full-resolution, perspective-corrected page
    private var previewBase: Bitmap? = null    // downscaled copy of `cropped` for fast filtering
    private var extractedText: String? = null  // OCR result for the current rotation
    private var renderJob: Job? = null

    /** Loads [path], crops it to [quad] (detecting the page again if null) and shows a preview. */
    fun load(path: String, quad: Quad?) {
        if (loadedPath == path) return
        loadedPath = path
        extractedText = null
        _uiState.value = EditUiState()

        viewModelScope.launch {
            try {
                val sourcePreview = withContext(Dispatchers.Default) {
                    val photo = loadUprightBitmap(path, MAX_SIDE)
                    val outline = quad ?: DocumentEdgeDetector().detect(photo) ?: Quad.FULL
                    source = photo
                    autoQuad = outline
                    appliedQuad = outline
                    cropPage(photo, outline)
                    photo.scaledToMaxSide(PREVIEW_SIDE)
                }
                _uiState.update { it.copy(cropImage = sourcePreview) }
                render()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load image") }
            }
        }
    }

    /** Opens the crop editor, starting from the outline currently in use. */
    fun startCrop() {
        if (source == null) return
        _uiState.update { it.copy(isCropping = true, cropQuad = appliedQuad) }
    }

    fun onCropQuadChange(quad: Quad) = _uiState.update { it.copy(cropQuad = quad) }

    /** Puts the outline back to what automatic detection found. */
    fun resetCropToAuto() = _uiState.update { it.copy(cropQuad = autoQuad) }

    fun useWholePhoto() = _uiState.update { it.copy(cropQuad = Quad.FULL) }

    fun cancelCrop() = _uiState.update { it.copy(isCropping = false) }

    /** Re-crops the original photo to the edited outline; rotation and filter are kept. */
    fun applyCrop() {
        val photo = source ?: return
        val outline = _uiState.value.cropQuad
        _uiState.update { it.copy(isCropping = false, isLoading = true) }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { cropPage(photo, outline) }
                appliedQuad = outline
                extractedText = null // the old text came from a different crop
                render()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not crop the image") }
            }
        }
    }

    /** Crops [photo] to [outline] and refreshes the full-size page and its preview copy. */
    private fun cropPage(photo: Bitmap, outline: Quad) {
        val page = PerspectiveCropper.crop(photo, outline)
        cropped = page
        previewBase = page.scaledToMaxSide(PREVIEW_SIDE)
    }

    fun onFilterSelected(filter: ScanFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        viewModelScope.launch { render() }
    }

    fun rotateClockwise() {
        extractedText = null // the old text was read in a different orientation
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
        viewModelScope.launch { render() }
    }

    fun extractText() {
        val page = cropped ?: return
        if (_uiState.value.isOcrRunning) return
        _uiState.update { it.copy(isOcrRunning = true) }

        viewModelScope.launch {
            try {
                // OCR reads the unfiltered page: thresholding can erase faint text.
                val rotated = withContext(Dispatchers.Default) { page.rotated(_uiState.value.rotationDegrees) }
                val text = recognizer.recognize(rotated)
                extractedText = text
                _uiState.update { it.copy(isOcrRunning = false, ocrText = text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isOcrRunning = false, error = e.message ?: "Text recognition failed") }
            }
        }
    }

    /** Stores this page as a draft so the user can scan another one. */
    fun addPage(onAdded: (DraftPage) -> Unit) = runSaving { onAdded(renderDraft()) }

    /** Stores this page, then builds a PDF from [previous] + this page and saves the document. */
    fun saveDocument(previous: List<DraftPage>, onSaved: (Long) -> Unit) = runSaving {
        val document = repository.createDocument(defaultDocumentTitle(),previous + renderDraft())
        onSaved(document.id)
    }

    fun dismissOcr() = _uiState.update { it.copy(ocrText = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    /** Renders the page at full resolution with the chosen rotation and filter, and writes it to disk. */
    private suspend fun renderDraft(): DraftPage {
        val page = checkNotNull(cropped) { "Page is not loaded yet" }
        val state = _uiState.value
        return withContext(Dispatchers.Default) {
            val image = ImageFilters.apply(page.rotated(state.rotationDegrees), state.filter)
            val file = fileStore.newDraftFile()
            fileStore.saveJpeg(image, file)
            DraftPage(file.absolutePath, extractedText)
        }
    }

    private fun runSaving(block: suspend () -> Unit) {
        if (cropped == null || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Could not save the page") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /** Re-renders the preview; a newer request cancels an older one still in flight. */
    private suspend fun render() {
        renderJob?.cancel()
        val base = previewBase ?: return
        val state = _uiState.value
        val job = viewModelScope.launch {
            val image = withContext(Dispatchers.Default) {
                ImageFilters.apply(base.rotated(state.rotationDegrees), state.filter)
            }
            _uiState.update { it.copy(isLoading = false, preview = image) }
        }
        renderJob = job
        job.join()
    }

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_SIDE = 3200
        const val PREVIEW_SIDE = 1280
    }
}
