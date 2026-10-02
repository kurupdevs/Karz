package com.kurupdevs.karz.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Single choke point for Firebase access.
 *
 * The app is fully usable without Firebase linked (no google-services.json,
 * or the placeholder still in place). [isReady] is computed lazily on first
 * use: it is true only when a FirebaseApp exists AND the config values are
 * real (not the REPLACE_ME placeholder). Every repository checks this and
 * falls back to its local in-memory store when Firebase is unavailable, so
 * nothing ever crashes from a missing backend.
 */
object FirebaseBackend {

    private val checked = AtomicBoolean(false)

    @Volatile
    var isReady: Boolean = false
        private set

    /** Idempotent. Safe to call from anywhere; runs the check once. */
    fun ensureChecked(context: Context): Boolean {
        if (checked.compareAndSet(false, true)) {
            isReady = runCatching { computeReady(context) }.getOrDefault(false)
            if (isReady) {
                runCatching { applyPersistenceSettings() }
            }
        }
        return isReady
    }

    private fun computeReady(context: Context): Boolean {
        val res = context.resources
        val pkg = context.packageName
        fun string(name: String): String? {
            val id = res.getIdentifier(name, "string", pkg)
            return if (id != 0) res.getString(id) else null
        }
        val appId = string("google_app_id")
        val apiKey = string("google_api_key")
        val projectId = string("project_id")
        if (appId.isNullOrBlank() || apiKey.isNullOrBlank() || projectId.isNullOrBlank()) {
            return false
        }
        if (listOf(appId, apiKey, projectId).any { it.contains("REPLACE") }) {
            return false
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context) ?: return false
        }
        return FirebaseApp.getApps(context).isNotEmpty()
    }

    /**
     * Explicit offline persistence. Must run before the first Firestore
     * instance is used; every repository reaches Firestore through [firestore],
     * so this ordering is guaranteed.
     */
    private fun applyPersistenceSettings() {
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
    }

    fun firestore(context: Context): FirebaseFirestore? {
        return if (ensureChecked(context)) FirebaseFirestore.getInstance() else null
    }

    fun auth(context: Context): FirebaseAuth? {
        return if (ensureChecked(context)) FirebaseAuth.getInstance() else null
    }

    fun storage(context: Context): FirebaseStorage? {
        return if (ensureChecked(context)) FirebaseStorage.getInstance() else null
    }
}

/** Thrown when a backend call needs Firebase but it is not linked yet. */
class BackendNotLinkedException :
    IllegalStateException("Firebase is not linked yet. The app is running in offline-first local mode.")

/** Thrown when a write raced with another device. */
class StaleDataException(message: String) : IllegalStateException(message)

/** Thrown when the same loan already exists (lender + masked ref + type). */
class DuplicateLoanException(message: String) : IllegalStateException(message)

/** Bridge for Google Play services Tasks without adding another dependency. */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}
