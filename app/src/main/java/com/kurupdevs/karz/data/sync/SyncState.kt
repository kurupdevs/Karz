package com.kurupdevs.karz.data.sync

import com.google.firebase.firestore.SnapshotMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Where the data on screen came from. Firestore snapshot listeners report
 * [SnapshotMetadata]; the UI turns this into a small "syncing" indicator.
 */
data class SyncState(
    val isFromCache: Boolean = true,
    val hasPendingWrites: Boolean = false
) {
    val isSyncing: Boolean get() = isFromCache || hasPendingWrites

    companion object {
        /** Live Firestore data, fully caught up. */
        val SYNCED = SyncState(isFromCache = false, hasPendingWrites = false)

        /** Local in-memory store (Firebase not linked). Nothing to sync. */
        val LOCAL = SyncState(isFromCache = true, hasPendingWrites = false)
    }
}

fun SnapshotMetadata.toSyncState(): SyncState =
    SyncState(isFromCache = isFromCache, hasPendingWrites = hasPendingWrites)

/** Convenience for repositories that have no listener to report. */
fun staticSyncState(): Flow<SyncState> = flowOf(SyncState.LOCAL)
