package com.example.scanner.ui.feature.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scanner.domain.model.DraftPage
import com.example.scanner.domain.model.Quad
import com.example.scanner.domain.model.ScanFilter
import com.example.scanner.ui.components.CropEditor
import com.example.scanner.ui.preview.SampleData
import com.example.scanner.ui.theme.ScannerTheme

/** Everything the edit screen can ask for. The no-op [None] is used by previews. */
private data class EditActions(
    val onFilterSelected: (ScanFilter) -> Unit,
    val onRotate: () -> Unit,
    val onRetake: () -> Unit,
    val onExtractText: () -> Unit,
    val onAddPage: () -> Unit,
    val onSave: () -> Unit,
    val onDismissOcr: () -> Unit,
    val onStartCrop: () -> Unit,
    val onCropQuadChange: (Quad) -> Unit,
    val onCropAuto: () -> Unit,
    val onCropWholePhoto: () -> Unit,
    val onCropCancel: () -> Unit,
    val onCropApply: () -> Unit,
) {
    companion object {
        val None = EditActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Shows the cropped page with crop, filter, rotate and OCR controls.
 *
 * @param previousPages pages already added to the current scan; this page is added after them
 * @param onPageAdded the page was stored; the caller keeps it and returns to the camera
 * @param onSaved the document (previous pages plus this one) was saved with this id
 */
@Composable
fun EditScreen(
    imagePath: String,
    quad: Quad?,
    previousPages: List<DraftPage>,
    onRetake: () -> Unit,
    onPageAdded: (DraftPage) -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(imagePath) { viewModel.load(imagePath, quad) }

    // Back closes the crop editor first instead of leaving the screen.
    BackHandler(enabled = state.isCropping, onBack = viewModel::cancelCrop)

    val actions = EditActions(
        onFilterSelected = viewModel::onFilterSelected,
        onRotate = viewModel::rotateClockwise,
        onRetake = onRetake,
        onExtractText = viewModel::extractText,
        onAddPage = { viewModel.addPage(onPageAdded) },
        onSave = { viewModel.saveDocument(previousPages, onSaved) },
        onDismissOcr = viewModel::dismissOcr,
        onStartCrop = viewModel::startCrop,
        onCropQuadChange = viewModel::onCropQuadChange,
        onCropAuto = viewModel::resetCropToAuto,
        onCropWholePhoto = viewModel::useWholePhoto,
        onCropCancel = viewModel::cancelCrop,
        onCropApply = viewModel::applyCrop,
    )
    EditContent(state = state, pageNumber = previousPages.size + 1, actions = actions, modifier = modifier)
}

/** Stateless edit screen: crop editor or normal editing, plus the OCR result dialog. */
@Composable
private fun EditContent(
    state: EditUiState,
    pageNumber: Int,
    actions: EditActions,
    modifier: Modifier = Modifier,
) {
    if (state.isCropping && state.cropImage != null) {
        CropMode(state = state, actions = actions, modifier = modifier)
    } else {
        EditMode(state = state, pageNumber = pageNumber, actions = actions, modifier = modifier)
    }

    state.ocrText?.let { text ->
        AlertDialog(
            onDismissRequest = actions.onDismissOcr,
            confirmButton = { TextButton(onClick = actions.onDismissOcr) { Text("Close") } },
            title = { Text("Extracted text") },
            text = {
                SelectionContainer {
                    Text(
                        text = text.ifBlank { "No text found." },
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            },
        )
    }
}

@Composable
private fun CropMode(state: EditUiState, actions: EditActions, modifier: Modifier) {
    val image = checkNotNull(state.cropImage)
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Adjust crop", style = MaterialTheme.typography.titleMedium)
        Text("Drag the corners to fit the page.", style = MaterialTheme.typography.bodySmall)

        CropEditor(
            image = image,
            quad = state.cropQuad,
            onQuadChange = actions.onCropQuadChange,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = actions.onCropAuto) { Text("Auto") }
            TextButton(onClick = actions.onCropWholePhoto) { Text("Whole photo") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onCropCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = actions.onCropApply, modifier = Modifier.weight(1f)) { Text("Apply") }
        }
    }
}

@Composable
private fun EditMode(
    state: EditUiState,
    pageNumber: Int,
    actions: EditActions,
    modifier: Modifier,
) {
    val idle = !state.isLoading && !state.isSaving
    val canSave = state.preview != null && idle

    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Page $pageNumber", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = actions.onStartCrop, enabled = state.cropImage != null && idle) {
                Icon(Icons.Default.Crop, contentDescription = "Adjust crop")
            }
            IconButton(onClick = actions.onRotate, enabled = idle) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate")
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val preview = state.preview
            if (preview != null) {
                val image = remember(preview) { preview.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Scanned page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.isLoading || state.isOcrRunning || state.isSaving) CircularProgressIndicator()
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { actions.onFilterSelected(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = actions.onRetake, enabled = !state.isSaving) { Text("Retake") }
            OutlinedButton(
                onClick = actions.onExtractText,
                enabled = canSave && !state.isOcrRunning,
            ) { Text("Extract text") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = actions.onAddPage,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            ) { Text("Add page") }
            Button(
                onClick = actions.onSave,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            ) { Text("Save PDF") }
        }
    }
}

// ---- Previews ----------------------------------------------------------------------------

@Composable
private fun EditPreviewHost(pageNumber: Int = 1, state: EditUiState) {
    ScannerTheme {
        EditContent(state = state, pageNumber = pageNumber, actions = EditActions.None)
    }
}

private fun sampleState(
    filter: ScanFilter = ScanFilter.MAGIC_COLOR,
    isLoading: Boolean = false,
    isSaving: Boolean = false,
    isCropping: Boolean = false,
    withPreview: Boolean = true,
    error: String? = null,
    ocrText: String? = null,
) = EditUiState(
    isLoading = isLoading,
    preview = if (withPreview) SampleData.pageBitmap() else null,
    filter = filter,
    isSaving = isSaving,
    isCropping = isCropping,
    cropImage = SampleData.photoBitmap(),
    cropQuad = SampleData.quad,
    error = error,
    ocrText = ocrText,
)

@PreviewLightDark
@Composable
private fun EditNormalPreview() = EditPreviewHost(state = sampleState())

@PreviewLightDark
@Composable
private fun EditSecondPageBlackWhitePreview() =
    EditPreviewHost(pageNumber = 2, state = sampleState(filter = ScanFilter.BLACK_WHITE))

@PreviewLightDark
@Composable
private fun EditLoadingPreview() =
    EditPreviewHost(state = sampleState(isLoading = true, withPreview = false))

@PreviewLightDark
@Composable
private fun EditSavingPreview() = EditPreviewHost(state = sampleState(isSaving = true))

@PreviewLightDark
@Composable
private fun EditErrorPreview() =
    EditPreviewHost(state = sampleState(error = "Could not save the page"))

@PreviewLightDark
@Composable
private fun EditOcrResultPreview() = EditPreviewHost(
    state = sampleState(ocrText = "Invoice #2026-0142\nTotal due ........ 1,348.50"),
)

/** The crop editor. Use Interactive mode to drag corners and see the loupe. */
@PreviewLightDark
@Composable
private fun EditCropModePreview() {
    var state by remember { mutableStateOf(sampleState(isCropping = true)) }
    ScannerTheme {
        EditContent(
            state = state,
            pageNumber = 1,
            actions = EditActions.None.copy(onCropQuadChange = { state = state.copy(cropQuad = it) }),
        )
    }
}
