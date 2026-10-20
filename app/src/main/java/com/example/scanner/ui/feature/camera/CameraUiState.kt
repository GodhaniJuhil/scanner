package com.example.scanner.ui.feature.camera

import com.example.scanner.domain.model.Quad

data class CameraUiState(
    /** Smoothed document outline in normalized, upright preview coordinates; null if none found. */
    val quad: Quad? = null,
    val isCapturing: Boolean = false,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
)

/** A captured page: the saved JPEG plus the outline that was visible when the shutter fired. */
data class CaptureResult(
    val imagePath: String,
    val quad: Quad?,
)

sealed interface CameraEvent {
    data class Captured(val result: CaptureResult) : CameraEvent
    data class Error(val message: String) : CameraEvent
}
