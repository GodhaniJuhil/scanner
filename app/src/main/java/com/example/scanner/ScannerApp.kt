package com.example.scanner

import android.app.Application
import com.example.scanner.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScannerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(Dispatchers.IO).launch { container.fileStore.clearTemporaryFiles() }
        // Finish recognizing text for documents saved before the app was last closed.
        container.backgroundOcr.resumePending()
    }
}
