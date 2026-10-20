package com.example.scanner.ui.feature.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.scanner.domain.model.ScanPage
import com.example.scanner.domain.model.ScannedDocument
import com.example.scanner.ui.preview.SampleData
import com.example.scanner.ui.theme.ScannerTheme
import com.example.scanner.util.ShareUtils
import java.io.File

/** Shows the pages of a saved document, with options to rename it and to open or share its PDF. */
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviewViewModel = viewModel(),
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    PreviewContent(
        document = document,
        onBack = onBack,
        onRename = viewModel::rename,
        onOpenPdf = {
            document?.let { doc ->
                if (!ShareUtils.openPdf(context, File(doc.pdfPath))) {
                    Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSharePdf = { document?.let { ShareUtils.sharePdf(context, File(it.pdfPath)) } },
        modifier = modifier,
    )
}

/** Stateless document screen. [document] is null while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewContent(
    document: ScannedDocument?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doc = document
    var showRename by remember { mutableStateOf(false) }
    var textPage by remember { mutableStateOf<ScanPage?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(doc?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (doc != null) {
                        IconButton(onClick = { showRename = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = onOpenPdf) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Open PDF")
                        }
                        IconButton(onClick = onSharePdf) {
                            Icon(Icons.Default.Share, contentDescription = "Share PDF")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (doc == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(doc.pages, key = { _, page -> page.index }) { position, page ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            AsyncImage(
                                model = File(page.imagePath),
                                contentDescription = "Page ${position + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Page ${position + 1} of ${doc.pages.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                // ocrText stays null until background recognition reaches this page.
                                when {
                                    page.ocrText == null -> Text(
                                        "Reading text…",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    page.ocrText.isBlank() -> Text(
                                        "No text found",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    else -> TextButton(onClick = { textPage = page }) { Text("View text") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRename && doc != null) {
        RenameDialog(
            currentTitle = doc.title,
            onConfirm = {
                onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }

    textPage?.let { page ->
        TextDialog(
            text = page.ocrText.orEmpty(),
            onDismiss = { textPage = null },
        )
    }
}

@Composable
private fun RenameDialog(currentTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename document") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(PreviewViewModel.MAX_TITLE_LENGTH) },
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recognized text") },
        text = {
            SelectionContainer {
                Text(text, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Scanned text", text))
                // Android 13+ shows its own "copied" confirmation.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Copy") }
        },
    )
}

/** Three pages showing each text state: text found, no text found, still being read. */
@PreviewLightDark
@Composable
private fun PreviewWithPagesPreview() {
    ScannerTheme {
        PreviewContent(
            document = SampleData.document(),
            onBack = {},
            onRename = {},
            onOpenPdf = {},
            onSharePdf = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewLongTitlePreview() {
    ScannerTheme {
        PreviewContent(
            document = SampleData.document(
                title = "Electricity bill September with a very long title that should be cut off",
                pages = SampleData.pages().take(1),
            ),
            onBack = {},
            onRename = {},
            onOpenPdf = {},
            onSharePdf = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewLoadingPreview() {
    ScannerTheme {
        PreviewContent(document = null, onBack = {}, onRename = {}, onOpenPdf = {}, onSharePdf = {})
    }
}

@PreviewLightDark
@Composable
private fun RenameDialogPreview() {
    ScannerTheme {
        RenameDialog(currentTitle = "Scan 2026-10-02 10-30", onConfirm = {}, onDismiss = {})
    }
}

@PreviewLightDark
@Composable
private fun TextDialogPreview() {
    ScannerTheme {
        TextDialog(text = SampleData.pages().first().ocrText.orEmpty(), onDismiss = {})
    }
}
