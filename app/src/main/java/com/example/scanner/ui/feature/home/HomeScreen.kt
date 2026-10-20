package com.example.scanner.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.scanner.domain.model.ScannedDocument
import com.example.scanner.ui.preview.SampleData
import com.example.scanner.ui.theme.ScannerTheme
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    HomeContent(
        documents = documents,
        onScan = onScan,
        onOpen = onOpen,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

/** Stateless home screen. [documents] is null while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    documents: List<ScannedDocument>?,
    onScan: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (ScannedDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ScannedDocument?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Documents") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        val list = documents
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("No scans yet. Tap Scan to start.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                // Extra bottom space so the last item can scroll above the floating button.
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { doc ->
                    DocumentRow(doc, onClick = { onOpen(doc.id) }, onDelete = { pendingDelete = doc })
                }
            }
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete document?") },
            text = { Text("\"${doc.title}\" and its PDF will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(doc)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DocumentRow(document: ScannedDocument, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = remember(document.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.createdAt))
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = document.thumbnailPath?.let(::File),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 80.dp).clip(RoundedCornerShape(6.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val pages = if (document.pageCount == 1) "1 page" else "${document.pageCount} pages"
                Text("$pages · $date", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${document.title}")
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeWithDocumentsPreview() {
    ScannerTheme {
        HomeContent(documents = SampleData.documents(), onScan = {}, onOpen = {}, onDelete = {})
    }
}

@PreviewLightDark
@Composable
private fun HomeEmptyPreview() {
    ScannerTheme {
        HomeContent(documents = emptyList(), onScan = {}, onOpen = {}, onDelete = {})
    }
}

@PreviewLightDark
@Composable
private fun HomeLoadingPreview() {
    ScannerTheme {
        HomeContent(documents = null, onScan = {}, onOpen = {}, onDelete = {})
    }
}
