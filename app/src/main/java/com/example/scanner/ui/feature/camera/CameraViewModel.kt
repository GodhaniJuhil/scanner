package com.example.scanner.ui.feature.camera

import android.app.Application
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import com.example.scanner.domain.model.Quad
import com.example.scanner.processing.edge.DocumentEdgeDetector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.awaitCancellation
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val detector = DocumentEdgeDetector()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var camera: androidx.camera.core.Camera? = null
    private var missedFrames = 0

    // All use cases share a 4:3 aspect ratio so the preview and the analysis frame cover the same
    // field of view; this lets the overlay map normalized quad coordinates straight onto the preview.
    private val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

    private val preview = Preview.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
        .build()
        .apply { setSurfaceProvider { request -> _surfaceRequest.value = request } }

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
        .build()

    private val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspect)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        .build()
        .apply { setAnalyzer(analysisExecutor) { proxy -> proxy.use(::analyze) } }

    /** Binds the camera to [lifecycleOwner] and suspends until cancelled, then unbinds. */
    suspend fun bindToCamera(lifecycleOwner: LifecycleOwner): Nothing {
        val provider = ProcessCameraProvider.awaitInstance(getApplication())
        val boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
            imageCapture,
        )
        camera = boundCamera
        _uiState.update {
            it.copy(hasFlash = boundCamera.cameraInfo.hasFlashUnit(), torchOn = false)
        }
        try {
            awaitCancellation()
        } finally {
            camera = null
            provider.unbindAll()
            _uiState.update { it.copy(quad = null, torchOn = false) }
        }
    }

    fun toggleTorch() {
        val cam = camera ?: return
        if (!_uiState.value.hasFlash) return
        val enable = !_uiState.value.torchOn
        cam.cameraControl.enableTorch(enable)
        _uiState.update { it.copy(torchOn = enable) }
    }

    fun capture() {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true) }

        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val quadAtShutter = _uiState.value.quad

        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(app),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    _uiState.update { it.copy(isCapturing = false) }
                    _events.trySend(CameraEvent.Captured(CaptureResult(file.absolutePath, quadAtShutter)))
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.update { it.copy(isCapturing = false) }
                    _events.trySend(CameraEvent.Error(exception.message ?: "Capture failed"))
                }
            },
        )
    }

    /** Runs on [analysisExecutor]. */
    private fun analyze(image: ImageProxy) {
        val detected = try {
            detector.detect(image)
        } catch (_: Exception) {
            null
        }
        publishQuad(detected)
    }

    /**
     * Smooths the detection so the overlay doesn't jitter, and keeps the last outline for a few
     * frames when detection momentarily fails so it doesn't flicker.
     */
    private fun publishQuad(detected: Quad?) {
        if (detected == null) {
            if (++missedFrames > MAX_MISSED_FRAMES) _uiState.update { it.copy(quad = null) }
            return
        }
        missedFrames = 0
        _uiState.update { state ->
            val previous = state.quad
            val next = when {
                previous == null -> detected
                // A big jump means a different object: snap instead of dragging across the screen.
                previous.maxDelta(detected) > SNAP_THRESHOLD -> detected
                else -> previous.lerp(detected, SMOOTHING)
            }
            state.copy(quad = next)
        }
    }

    override fun onCleared() {
        imageAnalysis.clearAnalyzer()
        analysisExecutor.shutdown()
        super.onCleared()
    }

    private companion object {
        const val MAX_MISSED_FRAMES = 6
        const val SMOOTHING = 0.4f
        const val SNAP_THRESHOLD = 0.2f
    }
}
