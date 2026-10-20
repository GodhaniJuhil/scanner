package com.example.scanner.ui.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import com.example.scanner.ui.components.QuadOverlay
import com.example.scanner.ui.preview.SampleData
import com.example.scanner.ui.theme.ScannerTheme

/**
 * Camera screen with live document-edge detection. Handles the camera permission itself.
 *
 * @param pageCount pages already added to the current scan, shown at the top
 * @param onClose the user left the camera (close button or back); the caller discards the scan
 * @param onReview open the list of pages scanned so far (shown once there is at least one)
 */
@Composable
fun CameraScreen(
    pageCount: Int,
    onCaptured: (CaptureResult) -> Unit,
    onClose: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraContent(pageCount, onCaptured, onClose, onReview, modifier, viewModel)
    } else {
        BackHandler(onBack = onClose)
        PermissionRequired(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = modifier,
        )
    }
}

@Composable
private fun CameraContent(
    pageCount: Int,
    onCaptured: (CaptureResult) -> Unit,
    onClose: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier,
    viewModel: CameraViewModel,
) {
    BackHandler(onBack = onClose)
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Re-binds whenever the lifecycle owner changes and unbinds when this leaves composition.
    LaunchedEffect(lifecycleOwner) { viewModel.bindToCamera(lifecycleOwner) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CameraEvent.Captured -> onCaptured(event.result)
                is CameraEvent.Error -> snackbarHost.showSnackbar(event.message)
            }
        }
    }

    CameraLayout(
        state = state,
        pageCount = pageCount,
        snackbarHost = snackbarHost,
        onClose = onClose,
        onReview = onReview,
        onToggleTorch = viewModel::toggleTorch,
        onCapture = viewModel::capture,
        modifier = modifier,
        viewfinder = {
            surfaceRequest?.let { request ->
                CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
            }
        },
    )
}

/** Stateless camera layout. [viewfinder] draws the live camera image; previews supply a stand-in. */
@Composable
private fun CameraLayout(
    state: CameraUiState,
    pageCount: Int,
    snackbarHost: SnackbarHostState,
    onClose: () -> Unit,
    onReview: () -> Unit,
    onToggleTorch: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
                }
                if (pageCount > 0) {
                    TextButton(onClick = onReview) {
                        Text(
                            text = if (pageCount == 1) "1 page · Review" else "$pageCount pages · Review",
                            color = Color.White,
                        )
                    }
                }
                // Keeps the page counter centred when the device has no flash button.
                if (!state.hasFlash) Spacer(Modifier.size(48.dp))
                if (state.hasFlash) {
                    IconButton(onClick = onToggleTorch) {
                        Icon(
                            imageVector = if (state.torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle flash",
                            tint = Color.White,
                        )
                    }
                }
            }

            // Preview + overlay share one 3:4 box, so normalized quad coordinates line up exactly.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    viewfinder()
                    QuadOverlay(quad = state.quad)
                }
                Text(
                    text = if (state.quad == null) "Point the camera at a document" else "Hold steady",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(Color(0x99000000), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // Shutter
            Box(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShutterButton(enabled = !state.isCapturing, onClick = onCapture)
                if (state.isCapturing) CircularProgressIndicator(color = Color.White)
            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp)
            .background(Color.Black, CircleShape)
            .padding(3.dp)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape),
    )
}

@Composable
private fun PermissionRequired(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera access is needed to scan documents.",
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant permission")
        }
    }
}

// ---- Previews ----------------------------------------------------------------------------
// The live camera image can't be shown in a preview, so a still photo stands in for it.

@Composable
private fun CameraPreviewHost(state: CameraUiState, pageCount: Int = 0) {
    val photo = remember { SampleData.photoBitmap().asImageBitmap() }
    ScannerTheme {
        CameraLayout(
            state = state,
            pageCount = pageCount,
            snackbarHost = remember { SnackbarHostState() },
            onClose = {},
            onReview = {},
            onToggleTorch = {},
            onCapture = {},
            viewfinder = {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

/** No document in view yet, no flash on this device. */
@Preview(name = "Searching", showBackground = true)
@Composable
private fun CameraSearchingPreview() = CameraPreviewHost(CameraUiState())

/** Outline found, two pages already scanned, flash available. */
@Preview(name = "Document detected", showBackground = true)
@Composable
private fun CameraDetectedPreview() = CameraPreviewHost(
    state = CameraUiState(quad = SampleData.quad, hasFlash = true),
    pageCount = 2,
)

@Preview(name = "Torch on, capturing", showBackground = true)
@Composable
private fun CameraCapturingPreview() = CameraPreviewHost(
    state = CameraUiState(quad = SampleData.quad, hasFlash = true, torchOn = true, isCapturing = true),
    pageCount = 1,
)

@Preview(name = "Permission needed", showBackground = true)
@Composable
private fun CameraPermissionPreview() {
    ScannerTheme { PermissionRequired(onRequest = {}) }
}
