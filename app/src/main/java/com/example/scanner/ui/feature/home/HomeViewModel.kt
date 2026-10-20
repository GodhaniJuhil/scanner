package com.example.scanner.ui.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner.ScannerApp
import com.example.scanner.domain.model.ScannedDocument
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<ScannerApp>().container.repository

    /** Null while the first load is in flight, so the screen can tell "loading" from "empty". */
    val documents: StateFlow<List<ScannedDocument>?> = repository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(document: ScannedDocument) {
        viewModelScope.launch { repository.deleteDocument(document.id) }
    }
}
