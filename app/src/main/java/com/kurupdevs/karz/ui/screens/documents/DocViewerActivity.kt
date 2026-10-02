package com.kurupdevs.karz.ui.screens.documents

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.NavyNearBlack
import com.kurupdevs.karz.ui.theme.OnDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app document viewer. FLAG_SECURE is set on THIS activity only
 * (blocks screenshots/screen recording while IDs and sanction letters
 * are on screen); the rest of the app stays shareable.
 *
 * PDFs render via [PdfRenderer]; images via Coil.
 * Declare in the manifest with android:exported="false".
 */
class DocViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        val mime = intent.getStringExtra(EXTRA_MIME) ?: ""
        setContent {
            ViewerChrome(
                title = intent.getStringExtra(EXTRA_NAME) ?: "Document",
                onBack = { finish() }
            ) {
                if (mime == "application/pdf") {
                    PdfPages(uriString = uri)
                } else {
                    AsyncImage(
                        model = uri.toUri(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_URI = "doc_uri"
        private const val EXTRA_MIME = "doc_mime"
        private const val EXTRA_NAME = "doc_name"

        fun open(context: Context, uriString: String, mimeType: String, name: String = "Document") {
            val intent = Intent(context, DocViewerActivity::class.java).apply {
                putExtra(EXTRA_URI, uriString)
                putExtra(EXTRA_MIME, mimeType)
                putExtra(EXTRA_NAME, name)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun ViewerChrome(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(NavyNearBlack)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = OnDark)
            }
            Text(
                title, style = MortgageTypography.titleMedium.copy(color = OnDark),
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Filled.Lock, contentDescription = "Private", tint = OnDark.copy(alpha = 0.5f))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Renders PDF pages to bitmaps (cap 50 pages to bound memory). */
@Composable
private fun PdfPages(uriString: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(uriString) {
        var renderer: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null
        val job = scope.launch(Dispatchers.IO) {
            try {
                pfd = context.contentResolver.openFileDescriptor(uriString.toUri(), "r")
                renderer = pfd?.let { PdfRenderer(it) }
                val r = renderer ?: throw IllegalStateException("no renderer")
                val density = context.resources.displayMetrics.density
                val pages = (0 until minOf(r.pageCount, 50)).map { i ->
                    val page = r.openPage(i)
                    try {
                        val w = (page.width * density).toInt().coerceAtLeast(1)
                        val h = (page.height * density).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally {
                        page.close()
                    }
                }
                withContext(Dispatchers.Main) { bitmaps = pages }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { failed = true }
            }
        }
        onDispose {
            job.cancel()
            renderer?.close()
            try { pfd?.close() } catch (_: Exception) { }
        }
    }

    if (failed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not open this PDF.", style = MortgageTypography.bodyLarge.copy(color = OnDark))
                Spacer(Modifier.height(6.dp))
                Text(
                    "The file may be corrupted or still uploading.",
                    style = MortgageTypography.bodyMedium.copy(color = OnDark.copy(alpha = 0.6f))
                )
            }
        }
        return
    }
    if (bitmaps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", style = MortgageTypography.bodyMedium.copy(color = OnDark.copy(alpha = 0.7f)))
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        itemsIndexed(bitmaps, key = { index, _ -> index }) { _, bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentScale = ContentScale.FillWidth
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
