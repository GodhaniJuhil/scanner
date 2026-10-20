package com.example.scanner.ui.feature.preview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.scanner.ScannerApp
import com.example.scanner.domain.model.ScannedDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The document id arrives through [SavedStateHandle] from the navigation route argument. */
class PreviewViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = getApplication<ScannerApp>().container.repository
    private val documentId: Long = checkNotNull(savedStateHandle[ARG_ID]) { "Missing document id" }

    val document: StateFlow<ScannedDocument?> = repository
        .observeDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    /** A one-off failure message; call [dismissError] once it has been shown. */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun rename(title: String) {
        viewModelScope.launch {
            try {
                repository.renameDocument(documentId, title)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not rename the document"
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        const val ARG_ID = "id"
        const val MAX_TITLE_LENGTH = 100
    }
}
