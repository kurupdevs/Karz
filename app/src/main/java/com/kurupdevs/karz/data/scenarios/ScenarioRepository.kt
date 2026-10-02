package com.kurupdevs.karz.data.scenarios

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.kurupdevs.karz.data.FirebaseBackend
import com.kurupdevs.karz.data.auth.FirebaseAuthService
import com.kurupdevs.karz.data.awaitTask
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Saved prepayment simulator scenarios. The simulator screen
 * serializes its inputs and results to flat string maps; this repository
 * just persists them under users/{uid}/scenarios.
 *
 * Not part of the screens-owned contracts; this interface is owned by the
 * data layer and follows the same offline-first pattern as the rest.
 */
data class Scenario(
    val id: String,
    val loanId: String,
    /** "reduce_emi" or "reduce_tenure". */
    val kind: String,
    val inputs: Map<String, String>,
    val result: Map<String, String>,
    val createdAtMillis: Long
)

interface ScenarioRepository {
    fun getScenarios(loanId: String): Flow<List<Scenario>>
    suspend fun saveScenario(
        loanId: String,
        kind: String,
        inputs: Map<String, String>,
        result: Map<String, String>
    ): Result<String>
}

class FirestoreScenarioRepository(
    private val auth: FirebaseAuthService,
    private val appContext: Context
) : ScenarioRepository {

    private val memory = MutableStateFlow<List<Scenario>>(emptyList())

    override fun getScenarios(loanId: String): Flow<List<Scenario>> =
        auth.currentUser.flatMapLatest { user ->
            val db = if (user != null) FirebaseBackend.firestore(appContext) else null
            if (db == null) {
                memory.map { list ->
                    list.filter { it.loanId == loanId }.sortedByDescending { it.createdAtMillis }
                }
            } else {
                callbackFlow {
                    val reg = db.collection("users").document(user.uid)
                        .collection("scenarios")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(200)
                        .addSnapshotListener { snap, e ->
                            if (e != null) {
                                close(e)
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                trySend(
                                    snap.documents
                                        .mapNotNull { it.toScenario() }
                                        .filter { it.loanId == loanId }
                                )
                            }
                        }
                    awaitClose { reg.remove() }
                }
            }
        }

    override suspend fun saveScenario(
        loanId: String,
        kind: String,
        inputs: Map<String, String>,
        result: Map<String, String>
    ): Result<String> = runCatching {
        require(kind in setOf("reduce_emi", "reduce_tenure")) {
            "Unknown scenario kind: $kind"
        }
        val user = auth.currentUserNow()
        val db = if (user != null) FirebaseBackend.firestore(appContext) else null
        if (db == null) {
            val scenario = Scenario(
                id = UUID.randomUUID().toString(),
                loanId = loanId,
                kind = kind,
                inputs = inputs,
                result = result,
                createdAtMillis = System.currentTimeMillis()
            )
            memory.update { it + scenario }
            return@runCatching scenario.id
        }
        val ref = db.collection("users").document(user.uid)
            .collection("scenarios").document()
        ref.set(
            mapOf(
                "loanId" to loanId,
                "kind" to kind,
                "inputs" to inputs,
                "result" to result,
                "createdAt" to Timestamp.now()
            )
        ).awaitTask()
        ref.id
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toScenario(): Scenario? {
        return try {
            Scenario(
                id = id,
                loanId = getString("loanId") ?: return null,
                kind = getString("kind") ?: return null,
                inputs = (get("inputs") as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }
                    ?.mapValues { it.value.toString() } ?: emptyMap(),
                result = (get("result") as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }
                    ?.mapValues { it.value.toString() } ?: emptyMap(),
                createdAtMillis = getTimestamp("createdAt")?.toDate()?.time
                    ?: System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }
}
