package com.kurupdevs.karz.data.documents

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.Query
import com.google.firebase.storage.StorageMetadata
import com.kurupdevs.karz.data.BackendNotLinkedException
import com.kurupdevs.karz.data.FirebaseBackend
import com.kurupdevs.karz.data.auth.FirebaseAuthService
import com.kurupdevs.karz.data.awaitTask
import com.kurupdevs.karz.data.documentMap
import com.kurupdevs.karz.data.model.DocumentCategory
import com.kurupdevs.karz.data.model.LoanDocument
import com.kurupdevs.karz.data.toLoanDocument
import com.kurupdevs.karz.ui.screens.data.DocumentRepository
import com.kurupdevs.karz.ui.screens.data.PendingUpload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Implements the screens contract [DocumentRepository].
 *
 * Files live in Firebase Storage at users/{uid}/docs/{docId}/{fileName};
 * metadata lives in Firestore users/{uid}/documents. Offline uploads are
 * queued in SharedPreferences (persisted across restarts) and retried
 * automatically when connectivity returns, plus on demand via [retryQueued].
 *
 * Queued docs show up in [getDocuments] immediately with pendingUpload=true,
 * so the vault never looks empty while offline.
 *
 * Callers handing over a content:// URI should take a persistable URI
 * permission first, otherwise a queued retry after process death cannot
 * re-open the file.
 */
class FirestoreDocumentRepository(
    private val auth: FirebaseAuthService,
    private val appContext: Context
) : DocumentRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queuePrefs =
        appContext.getSharedPreferences("karz_doc_queue", Context.MODE_PRIVATE)
    private val queueFlow = MutableStateFlow(readQueue())

    /** Process-local docs for memory mode (Firebase not linked / signed out). */
    private val memoryDocs = MutableStateFlow<List<LoanDocument>>(emptyList())

    init {
        watchConnectivity()
    }

    // ------------------------------------------------------------------
    // Reads.
    // ------------------------------------------------------------------

    override fun getDocuments(loanId: String?): Flow<List<LoanDocument>> {
        val remote: Flow<List<LoanDocument>> = auth.currentUser.flatMapLatest { user ->
            val db = FirebaseBackend.firestore(appContext)
            if (user == null || db == null) {
                memoryDocs
            } else {
                callbackFlow {
                    val reg = db.collection("users").document(user.uid)
                        .collection("documents")
                        .orderBy("uploadedAt", Query.Direction.DESCENDING)
                        .limit(500)
                        .addSnapshotListener { snap, e ->
                            if (e != null) {
                                close(e)
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                trySend(snap.documents.map { it.toLoanDocument() })
                            }
                        }
                    awaitClose { reg.remove() }
                }
            }
        }
        return combine(remote, queueFlow) { docs, queued ->
            val pending = queued
                .filter { loanId == null || it.loanId == loanId }
                .sortedByDescending { it.enqueuedAtMillis }
                .map { it.toPendingDocument() }
            val filtered = if (loanId == null) docs else docs.filter { it.loanId == loanId }
            pending + filtered
        }
    }

    override fun getDocumentCount(): Flow<Int> =
        getDocuments(null).map { it.size }

    override fun getPendingUploads(): Flow<List<PendingUpload>> =
        queueFlow.map { queued ->
            queued.sortedByDescending { it.enqueuedAtMillis }.map { item ->
                PendingUpload(
                    id = item.docId,
                    fileName = item.fileName,
                    category = DocumentCategory.valueOf(item.category),
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    loanId = item.loanId
                )
            }
        }

    /** Live view of the offline retry queue (for a "pending uploads" row). */
    fun queuedUploads(): Flow<List<QueuedUpload>> = queueFlow

    // ------------------------------------------------------------------
    // Writes.
    // ------------------------------------------------------------------

    override suspend fun uploadDocument(
        fileName: String,
        category: DocumentCategory,
        mimeType: String,
        contentUri: Uri,
        sizeBytes: Long,
        loanId: String?
    ): Result<String> = runCatching {
        validateUpload(fileName, mimeType, sizeBytes)
        val docId = UUID.randomUUID().toString()
        val queued = QueuedUpload(
            docId = docId,
            fileName = fileName,
            category = category.name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            contentUri = contentUri.toString(),
            loanId = loanId
        )
        val user = auth.currentUserNow()
        val canUploadNow = user != null &&
            FirebaseBackend.ensureChecked(appContext) &&
            isOnline()
        if (!canUploadNow) {
            enqueue(queued)
            return@runCatching docId
        }
        val storagePath = storagePathFor(user!!.uid, docId, fileName)
        try {
            uploadToStorage(storagePath, contentUri, mimeType)
            writeMetadata(user.uid, docId, fileName, category, mimeType, sizeBytes, loanId, storagePath)
        } catch (e: Exception) {
            Log.w(TAG, "upload failed, queued for retry", e)
            enqueue(queued)
        }
        docId
    }

    override suspend fun cancelPendingUpload(pendingId: String): Result<Unit> = runCatching {
        dequeue(pendingId)
    }

    override suspend fun deleteDocument(docId: String): Result<Unit> = runCatching {
        // Pending queue entry: just drop it.
        val queued = queueFlow.value.find { it.docId == docId }
        if (queued != null) {
            dequeue(docId)
            return@runCatching
        }
        val user = auth.currentUserNow()
        val db = FirebaseBackend.firestore(appContext)
        if (user == null || db == null) {
            memoryDocs.update { list -> list.filterNot { it.id == docId } }
            return@runCatching
        }
        val metaRef = db.collection("users").document(user.uid)
            .collection("documents").document(docId)
        val snap = metaRef.get().awaitTask()
        val storagePath = snap.getString("storagePath")
        metaRef.delete().awaitTask()
        // Storage object removal is best-effort; metadata delete is the source of truth.
        if (storagePath != null) {
            runCatching {
                FirebaseBackend.storage(appContext)!!.reference.child(storagePath)
                    .delete().awaitTask()
            }.onFailure { Log.w(TAG, "best-effort storage delete failed", it) }
        }
    }

    override suspend fun resolveContentUri(doc: LoanDocument): Result<Uri> = runCatching {
        if (doc.pendingUpload) {
            // Not uploaded yet: the source content URI travels in storagePath.
            return@runCatching Uri.parse(doc.storagePath)
        }
        require(doc.storagePath.isNotBlank()) { "Document has no storage path." }
        val cached = File(appContext.cacheDir, "doc_cache/${doc.id}/${doc.fileName.ifBlank { doc.id }}")
        if (!cached.exists()) {
            val storage = FirebaseBackend.storage(appContext) ?: throw BackendNotLinkedException()
            cached.parentFile?.mkdirs()
            storage.reference.child(doc.storagePath).getFile(cached).awaitTask()
        }
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            cached
        )
    }

    /** Retries every queued upload. Called automatically on reconnect. */
    suspend fun retryQueued(): Result<Unit> = runCatching {
        val user = auth.currentUserNow() ?: return@runCatching
        if (!FirebaseBackend.ensureChecked(appContext) || !isOnline()) return@runCatching
        val pending = queueFlow.first()
        for (item in pending) {
            val storagePath = storagePathFor(user.uid, item.docId, item.fileName)
            try {
                uploadToStorage(storagePath, Uri.parse(item.contentUri), item.mimeType)
                writeMetadata(
                    user.uid, item.docId,
                    item.fileName,
                    DocumentCategory.valueOf(item.category),
                    item.mimeType,
                    item.sizeBytes,
                    item.loanId,
                    storagePath
                )
                dequeue(item.docId)
            } catch (e: Exception) {
                Log.w(TAG, "queued upload retry failed for ${item.docId}", e)
                bumpAttempts(item.docId)
                if (!isOnline()) break
            }
        }
    }

    // ------------------------------------------------------------------
    // Internals.
    // ------------------------------------------------------------------

    private fun validateUpload(fileName: String, mimeType: String, sizeBytes: Long) {
        require(fileName.isNotBlank()) { "File name is required." }
        require(mimeType in ALLOWED_MIME_TYPES) {
            "Only PDF, JPG, PNG and WebP files are supported."
        }
        require(sizeBytes in 1..MAX_BYTES) {
            "Files must be smaller than 15 MB."
        }
    }

    private fun storagePathFor(uid: String, docId: String, fileName: String): String {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "users/$uid/docs/$docId/$safeName"
    }

    private suspend fun uploadToStorage(storagePath: String, uri: Uri, mimeType: String) {
        val storage = FirebaseBackend.storage(appContext)
            ?: throw BackendNotLinkedException()
        val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
        storage.reference.child(storagePath).putFile(uri, metadata).awaitTask()
    }

    private suspend fun writeMetadata(
        uid: String,
        docId: String,
        fileName: String,
        category: DocumentCategory,
        mimeType: String,
        sizeBytes: Long,
        loanId: String?,
        storagePath: String
    ) {
        val db = FirebaseBackend.firestore(appContext)
            ?: throw BackendNotLinkedException()
        db.collection("users").document(uid).collection("documents").document(docId)
            .set(
                documentMap(
                    fileName = fileName,
                    category = category,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    storagePath = storagePath,
                    loanId = loanId,
                    tags = emptyList()
                )
            ).awaitTask()
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun watchConnectivity() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { retryQueued() }
            }
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    // Queue persistence (SharedPreferences + kotlinx.serialization).

    private fun readQueue(): List<QueuedUpload> {
        val raw = queuePrefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<QueuedUpload>>(raw) }.getOrDefault(emptyList())
    }

    private fun persistQueue(items: List<QueuedUpload>) {
        queuePrefs.edit().putString(KEY_QUEUE, Json.encodeToString(items)).apply()
        queueFlow.value = items
    }

    private fun enqueue(item: QueuedUpload) {
        persistQueue(queueFlow.value.filterNot { it.docId == item.docId } + item)
    }

    private fun dequeue(docId: String) {
        persistQueue(queueFlow.value.filterNot { it.docId == docId })
    }

    private fun bumpAttempts(docId: String) {
        persistQueue(queueFlow.value.map {
            if (it.docId == docId) it.copy(attempts = it.attempts + 1) else it
        })
    }

    companion object {
        private const val TAG = "DocumentRepository"
        private const val KEY_QUEUE = "queue_json"
        private const val MAX_BYTES = 15L * 1024 * 1024
        private val ALLOWED_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}

@Serializable
data class QueuedUpload(
    val docId: String,
    val fileName: String,
    val category: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentUri: String,
    val loanId: String? = null,
    val attempts: Int = 0,
    val enqueuedAtMillis: Long = System.currentTimeMillis()
) {
    fun toPendingDocument(): LoanDocument = LoanDocument(
        id = docId,
        fileName = fileName,
        category = DocumentCategory.valueOf(category),
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        storagePath = contentUri,
        thumbnailPath = null,
        loanId = loanId,
        pendingUpload = true
    )
}
