package com.kurupdevs.karz.ui.screens.documents

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kurupdevs.karz.data.model.DocumentCategory
import com.kurupdevs.karz.data.model.LoanDocument
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.screens.common.MortgageIcons
import com.kurupdevs.karz.ui.screens.data.DocumentRepository
import com.kurupdevs.karz.ui.screens.data.PendingUpload
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.TextHeadline
import kotlinx.coroutines.launch

private const val MAX_UPLOAD_BYTES = 15L * 1024 * 1024
private val ALLOWED_MIME = setOf(
    "application/pdf", "image/jpeg", "image/png", "image/webp"
)

/**
 * S6 document vault: category chips, grid, FAB upload (15MB cap,
 * pdf/jpg/png/webp allowlist), in-app viewer (FLAG_SECURE, see
 * [DocViewerActivity]), offline-pending badges. The live count feeds the
 * Manage screen tile via [DocumentRepository.getDocumentCount].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    documents: DocumentRepository,
    onBack: () -> Unit = {}
) {
    val docs by documents.getDocuments().collectAsStateWithLifecycle(initialValue = emptyList())
    val pending by documents.getPendingUploads().collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf<DocumentCategory?>(null) }
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var docToDelete by remember { mutableStateOf<LoanDocument?>(null) }
    var pendingToDelete by remember { mutableStateOf<PendingUpload?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: ""
        val size = querySize(cr, uri)
        val name = queryName(cr, uri) ?: "document"
        when {
            mime !in ALLOWED_MIME -> scope.launch {
                snackbar.showSnackbar("Only PDF, JPG, PNG and WebP files are supported.")
            }
            size != null && size > MAX_UPLOAD_BYTES -> scope.launch {
                snackbar.showSnackbar("Files must be under 15MB.")
            }
            else -> {
                pickedFile = PickedFile(name, mime, uri, size ?: 0L)
                showCategorySheet = true
            }
        }
    }

    val visible = if (filter == null) docs else docs.filter { it.category == filter }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launcher.launch("*/*") },
                containerColor = PurpleSolid,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Filled.Add, contentDescription = "Upload document") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextHeadline)
                }
                Text(
                    "Documents", style = MortgageTypography.headlineLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip("All", filter == null) { filter = null }
                DocumentCategory.entries.forEach { cat ->
                    CategoryChip(cat.label(), filter == cat) { filter = cat }
                }
            }
            // offline-pending strip
            if (pending.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pending.forEach { p ->
                        PendingRow(
                            pending = p,
                            onCancel = { pendingToDelete = p }
                        )
                    }
                }
            }
            if (visible.isEmpty() && pending.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No documents yet", style = MortgageTypography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Keep sanction letters, ID proofs and pay slips here. They stay private to your account.",
                            style = MortgageTypography.bodyMedium.copy(textAlign = TextAlign.Center)
                        )
                    }
                }
            } else if (visible.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }, contentType = { "doc" }) { doc ->
                        DocCard(
                            doc = doc,
                            documents = documents,
                            onOpen = { uriString, mime ->
                                DocViewerActivity.open(context, uriString, mime, doc.fileName)
                            },
                            onDelete = { docToDelete = doc }
                        )
                    }
                }
            }
        }
    }

    if (showCategorySheet && pickedFile != null) {
        CategorySheet(
            onDismiss = { showCategorySheet = false; pickedFile = null },
            onPick = { cat ->
                val file = pickedFile!!
                showCategorySheet = false
                pickedFile = null
                scope.launch {
                    documents.uploadDocument(
                        fileName = file.name,
                        category = cat,
                        mimeType = file.mime,
                        contentUri = file.uri,
                        sizeBytes = file.size
                    ).onSuccess { snackbar.showSnackbar("Document saved.") }
                        .onFailure { snackbar.showSnackbar("Upload failed. It will retry when you are back online.") }
                }
            }
        )
    }

    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Delete document?", style = MortgageTypography.titleLarge) },
            text = { Text(doc.fileName, style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        documents.deleteDocument(doc.id)
                        docToDelete = null
                    }
                }) { Text("Delete", color = Color(0xFFD33F3F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { docToDelete = null }) { Text("Keep") } }
        )
    }

    pendingToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingToDelete = null },
            title = { Text("Cancel upload?", style = MortgageTypography.titleLarge) },
            text = { Text(p.fileName, style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        documents.cancelPendingUpload(p.id)
                        pendingToDelete = null
                    }
                }) { Text("Cancel upload", color = Color(0xFFD33F3F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingToDelete = null }) { Text("Keep") } }
        )
    }
}

private data class PickedFile(val name: String, val mime: String, val uri: Uri, val size: Long)

private fun DocumentCategory.label(): String = when (this) {
    DocumentCategory.IDENTITY -> "Identity"
    DocumentCategory.INCOME -> "Income"
    DocumentCategory.PROPERTY -> "Property"
    DocumentCategory.LOAN -> "Loan"
    DocumentCategory.SANCTION -> "Sanction"
    DocumentCategory.OTHER -> "Other"
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .pressScale(0.94f)
            .clip(RoundedCornerShape(50))
            .background(if (selected) PurpleSolid else CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            style = MortgageTypography.labelLarge.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = if (selected) Color.White else TextHeadline
            )
        )
    }
}

@Composable
private fun PendingRow(pending: PendingUpload, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF6DC))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(pending.fileName, style = MortgageTypography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Waiting for connection", style = MortgageTypography.labelLarge.copy(color = Color(0xFF8A6D00)))
        }
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color(0xFFD33F3F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
private fun DocCard(
    doc: LoanDocument,
    documents: DocumentRepository,
    onOpen: (uriString: String, mime: String) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uriCache = remember { mutableStateMapOf<String, String>() }
    val isImage = doc.mimeType.startsWith("image/")
    val cached = uriCache[doc.id]

    // Resolve a readable URI lazily for image thumbnails (Coil).
    LaunchedEffect(doc.id) {
        if (isImage && !uriCache.containsKey(doc.id)) {
            documents.resolveContentUri(doc).onSuccess { uri ->
                uriCache[doc.id] = uri.toString()
            }
        }
    }

    Column(
        Modifier
            .pressScale(0.97f)
            .clip(MortgageRadii.InnerCardShape)
            .background(CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    documents.resolveContentUri(doc)
                        .onSuccess { onOpen(it.toString(), doc.mimeType) }
                }
            }
            .padding(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F0F7)),
            contentAlignment = Alignment.Center
        ) {
            if (isImage && cached != null) {
                AsyncImage(
                    model = cached,
                    contentDescription = doc.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    MortgageIcons.Doc,
                    contentDescription = null,
                    tint = if (isImage) Color(0xFF3E6FD8) else Color(0xFFD33F3F),
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(CardWhite.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = TextHeadline, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            doc.fileName, style = MortgageTypography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(doc.category.label(), style = MortgageTypography.labelLarge)
            if (doc.sizeBytes > 0) {
                Text(" · ${formatBytes(doc.sizeBytes)}", style = MortgageTypography.labelLarge)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(onDismiss: () -> Unit, onPick: (DocumentCategory) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = CardWhite) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("What kind of document is this?", style = MortgageTypography.titleLarge)
            Spacer(Modifier.height(14.dp))
            DocumentCategory.entries.forEach { cat ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPick(cat) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(PurpleSolid))
                    Spacer(Modifier.size(12.dp))
                    Text(cat.label(), style = MortgageTypography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun querySize(cr: ContentResolver, uri: Uri): Long? {
    var cursor: Cursor? = null
    return try {
        cursor = cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0) cursor.getLong(idx).takeIf { it >= 0 } else null
        } else null
    } catch (_: Exception) {
        null
    } finally {
        cursor?.close()
    }
}

private fun queryName(cr: ContentResolver, uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else null
    } catch (_: Exception) {
        null
    } finally {
        cursor?.close()
    }
}
