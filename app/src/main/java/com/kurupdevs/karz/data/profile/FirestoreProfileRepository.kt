package com.kurupdevs.karz.data.profile

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.kurupdevs.karz.data.FirebaseBackend
import com.kurupdevs.karz.data.auth.FirebaseAuthService
import com.kurupdevs.karz.data.awaitTask
import com.kurupdevs.karz.ui.screens.data.ProfileRepository
import com.kurupdevs.karz.ui.screens.data.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.TimeZone

/**
 * Implements the screens contract [ProfileRepository].
 *
 * Preferences live in the Firestore users/{uid} doc when signed in, and in
 * local SharedPreferences otherwise (fully usable before Firebase is linked
 * or before onboarding completes). Firestore is the source of truth once a
 * user exists; the local copy is just a pre-login stand-in, not a sync target.
 */
class FirestoreProfileRepository(
    private val auth: FirebaseAuthService,
    private val appContext: Context
) : ProfileRepository {

    private val prefs =
        appContext.getSharedPreferences("karz_profile", Context.MODE_PRIVATE)

    private val localProfile = MutableStateFlow(loadLocalProfile())

    override fun getProfile(): Flow<UserProfile> =
        auth.currentUser.flatMapLatest { user ->
            val db = FirebaseBackend.firestore(appContext)
            if (user == null || db == null) {
                localProfile
            } else {
                callbackFlow {
                    val ref = db.collection("users").document(user.uid)
                    val reg = ref.addSnapshotListener { snap, e ->
                        if (e != null) {
                            close(e)
                            return@addSnapshotListener
                        }
                        if (snap != null && snap.exists()) {
                            trySend(
                                UserProfile(
                                    uid = user.uid,
                                    displayName = snap.getString("displayName").orEmpty(),
                                    phoneNumber = snap.getString("phoneNumber")
                                        ?: user.phoneNumber.orEmpty(),
                                    homeCurrency = snap.getString("homeCurrency") ?: "INR",
                                    timezone = snap.getString("timezone") ?: "Asia/Kolkata",
                                    notificationsEnabled = snap.getBoolean("notificationsEnabled")
                                        ?: true
                                )
                            )
                        } else {
                            trySend(
                                UserProfile(
                                    uid = user.uid,
                                    displayName = "",
                                    phoneNumber = user.phoneNumber.orEmpty()
                                )
                            )
                        }
                    }
                    awaitClose { reg.remove() }
                }
            }
        }

    override suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        require(name.trim().length in 1..60) { "Display name cannot be empty." }
        writeField("displayName", name.trim())
        saveLocal { copy(displayName = name.trim()) }
    }

    override suspend fun updateCurrency(currencyCode: String): Result<Unit> = runCatching {
        val code = currencyCode.trim().uppercase()
        require(code.matches(Regex("^[A-Z]{3}$"))) { "Currency must be a 3-letter code." }
        writeField("homeCurrency", code)
        saveLocal { copy(homeCurrency = code) }
    }

    override suspend fun updateTimezone(timezoneId: String): Result<Unit> = runCatching {
        require(timezoneId in TimeZone.getAvailableIDs()) { "Unknown timezone: $timezoneId" }
        writeField("timezone", timezoneId)
        saveLocal { copy(timezone = timezoneId) }
    }

    override suspend fun updateNotificationsEnabled(enabled: Boolean): Result<Unit> = runCatching {
        writeField("notificationsEnabled", enabled)
        saveLocal { copy(notificationsEnabled = enabled) }
    }

    override suspend fun signOut(): Result<Unit> = auth.signOut()

    /**
     * Called once after OTP verification to create the user doc. Merge-set so
     * re-running onboarding never wipes existing fields.
     */
    suspend fun completeOnboarding(
        displayName: String,
        homeCurrency: String,
        timezone: String
    ): Result<Unit> = runCatching {
        val user = auth.currentUserNow() ?: return@runCatching
        val db = FirebaseBackend.firestore(appContext) ?: return@runCatching
        db.collection("users").document(user.uid).set(
            mapOf(
                "phoneNumber" to user.phoneNumber,
                "displayName" to displayName.trim(),
                "homeCurrency" to homeCurrency.uppercase(),
                "timezone" to timezone,
                "onboardingComplete" to true,
                "notificationsEnabled" to true,
                "biometricLockEnabled" to false,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).awaitTask()
        saveLocal {
            copy(
                displayName = displayName.trim(),
                homeCurrency = homeCurrency.uppercase(),
                timezone = timezone
            )
        }
    }

    /** Registers an FCM token for reminder delivery. Called by the messaging service. */
    suspend fun saveFcmToken(token: String): Result<Unit> = runCatching {
        val user = auth.currentUserNow() ?: return@runCatching
        val db = FirebaseBackend.firestore(appContext) ?: return@runCatching
        val key = token.replace(Regex("[^A-Za-z0-9_-]"), "_")
        db.collection("users").document(user.uid).update(
            mapOf(
                "fcmTokens.$key" to mapOf(
                    "token" to token,
                    "addedAt" to FieldValue.serverTimestamp()
                ),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).awaitTask()
    }

    private suspend fun writeField(field: String, value: Any) {
        val user = auth.currentUserNow()
        val db = FirebaseBackend.firestore(appContext)
        if (user != null && db != null) {
            db.collection("users").document(user.uid).update(
                mapOf(field to value, "updatedAt" to FieldValue.serverTimestamp())
            ).awaitTask()
        }
    }

    private fun loadLocalProfile(): UserProfile = UserProfile(
        uid = "",
        displayName = prefs.getString("displayName", "").orEmpty(),
        phoneNumber = prefs.getString("phoneNumber", "").orEmpty(),
        homeCurrency = prefs.getString("homeCurrency", "INR") ?: "INR",
        timezone = prefs.getString("timezone", TimeZone.getDefault().id) ?: "Asia/Kolkata",
        notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
    )

    private fun saveLocal(transform: UserProfile.() -> UserProfile) {
        val updated = localProfile.value.transform()
        prefs.edit()
            .putString("displayName", updated.displayName)
            .putString("homeCurrency", updated.homeCurrency)
            .putString("timezone", updated.timezone)
            .putBoolean("notificationsEnabled", updated.notificationsEnabled)
            .apply()
        localProfile.value = updated
    }
}
