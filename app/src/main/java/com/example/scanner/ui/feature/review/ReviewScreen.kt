package com.example.scanner.ui.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.scanner.domain.model.DraftPage
import com.example.scanner.ui.preview.SampleData
import com.example.scanner.ui.theme.ScannerTheme
import java.io.File

/**
 * Lists the pages of the scan in progress so they can be reordered, deleted and saved as a PDF.
 *
 * @param onAddPage go back to the camera for another page
 * @param onSave save the pages, in this order, as a document
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    pages: List<DraftPage>,
    isSaving: Boolean,
    error: String?,
    onMove: (index: Int, delta: Int) -> Unit,
    onDelete: (index: Int) -> Unit,
    onAddPage: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Review pages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddPage, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        Text("Add page")
                    }
                    Button(
                        onClick = onSave,
                        enabled = pages.isNotEmpty() && !isSaving,
                        modifier = Modifier.weight(1f),
                    ) { Text("Save PDF") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (pages.isEmpty()) {
                Text("No pages. Add a page to continue.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(pages, key = { _, page -> page.imagePath }) { index, page ->
                        PageRow(
                            page = page,
                            number = index + 1,
                            canMoveUp = index > 0,
                            canMoveDown = index < pages.lastIndex,
                            enabled = !isSaving,
                            onMoveUp = { onMove(index, -1) },
                            onMoveDown = { onMove(index, +1) },
                            onDelete = { onDelete(index) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            if (isSaving) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun PageRow(
    page: DraftPage,
    number: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = File(page.imagePath),
                contentDescription = "Page $number",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 72.dp, height = 96.dp).clip(RoundedCornerShape(6.dp)),
            )
            Text("Page $number", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Column {
                IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move page $number up")
                }
                IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move page $number down")
                }
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "Delete page $number")
            }
        }
    }
}

@Composable
private fun ReviewPreviewHost(pages: List<DraftPage>, isSaving: Boolean = false, error: String? = null) {
    ScannerTheme {
        ReviewScreen(
            pages = pages,
            isSaving = isSaving,
            error = error,
            onMove = { _, _ -> },
            onDelete = {},
            onAddPage = {},
            onSave = {},
            onBack = {},
        )
    }
}

/** First row has no "up" button and the last has no "down" button. */
@PreviewLightDark
@Composable
private fun ReviewThreePagesPreview() = ReviewPreviewHost(SampleData.draftPages(3))

@PreviewLightDark
@Composable
private fun ReviewSinglePagePreview() = ReviewPreviewHost(SampleData.draftPages(1))

@PreviewLightDark
@Composable
private fun ReviewEmptyPreview() = ReviewPreviewHost(emptyList())

@PreviewLightDark
@Composable
private fun ReviewSavingPreview() = ReviewPreviewHost(SampleData.draftPages(2), isSaving = true)

@PreviewLightDark
@Composable
private fun ReviewErrorPreview() =
    ReviewPreviewHost(SampleData.draftPages(2), error = "Could not save the document")
