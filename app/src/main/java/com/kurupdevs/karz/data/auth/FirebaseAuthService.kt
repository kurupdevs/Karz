package com.kurupdevs.karz.data.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.kurupdevs.karz.data.BackendNotLinkedException
import com.kurupdevs.karz.data.FirebaseBackend
import com.kurupdevs.karz.data.awaitTask
import com.kurupdevs.karz.ui.screens.data.AuthGateway
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thrown from sendOtp when the 60s cooldown has not elapsed yet. */
class OtpThrottledException(val retryAfterSeconds: Long) :
    IllegalStateException("Please wait $retryAfterSeconds seconds before requesting a new OTP.")

/** Thrown from sendOtp after 5 resends to the same number in one day. */
class OtpQuotaExceededException :
    IllegalStateException("Too many OTP requests for this number today. Try again tomorrow.")

/**
 * Phone-OTP auth over Firebase Auth. Implements the screens contract
 * [AuthGateway]; the verification id lives inside this service between
 * [sendOtp] and [verifyOtp], which is why the contract needs no session param.
 *
 * Abuse guards (SPEC section 11): 60s cooldown between sends and max 5
 * resends per number per day, both persisted in SharedPreferences so they
 * survive process death. Sessions are persisted by Firebase Auth itself:
 * a signed-in user stays signed in across restarts with no extra work.
 *
 * One platform requirement: Firebase needs an Activity for the reCAPTCHA
 * fallback, so the onboarding host Activity must call [bindActivity] once
 * (e.g. in onCreate) before [sendOtp].
 */
class FirebaseAuthService(private val appContext: Context) : AuthGateway {

    data class AuthUser(val uid: String, val phoneNumber: String?)

    private val throttlePrefs =
        appContext.getSharedPreferences("karz_otp_throttle", Context.MODE_PRIVATE)

    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var pendingVerificationId: String? = null

    @Volatile
    private var pendingResendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun bindActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    val currentUser: Flow<AuthUser?> = callbackFlow {
        val auth = FirebaseBackend.auth(appContext)
        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        trySend(auth.currentUser?.let { AuthUser(it.uid, it.phoneNumber) })
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser?.let { AuthUser(it.uid, it.phoneNumber) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /** Synchronous snapshot for repository routing. Null when signed out or unlinked. */
    fun currentUserNow(): AuthUser? {
        val user = FirebaseBackend.auth(appContext)?.currentUser ?: return null
        return AuthUser(user.uid, user.phoneNumber)
    }

    override suspend fun sendOtp(phone: String): Result<Unit> = runCatching {
        val e164 = phone.trim()
        require(e164.matches(E164_REGEX)) {
            "Phone number must be in E.164 format, e.g. +919876543210"
        }
        val auth = FirebaseBackend.auth(appContext) ?: throw BackendNotLinkedException()
        checkThrottle(e164)
        val activity = activityRef?.get()
            ?: throw IllegalStateException("No Activity bound. Call FirebaseAuthService.bindActivity() first.")

        suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Instant verification or auto-retrieval: sign in quietly.
                    // The currentUser flow emits; the manual code path still works.
                    auth.signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    pendingVerificationId = verificationId
                    pendingResendToken = token
                    recordSent(e164)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            pendingResendToken?.let { builder.setForceResendingToken(it) }
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }
    }

    override suspend fun verifyOtp(code: String): Result<Unit> = runCatching {
        val auth = FirebaseBackend.auth(appContext) ?: throw BackendNotLinkedException()
        val verificationId = pendingVerificationId
            ?: throw IllegalStateException("No OTP request is pending. Request a code first.")
        require(code.trim().length >= 4) { "Enter the complete OTP." }
        val credential = PhoneAuthProvider.getCredential(verificationId, code.trim())
        auth.signInWithCredential(credential).awaitTask()
        pendingVerificationId = null
        pendingResendToken = null
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        FirebaseBackend.auth(appContext)?.signOut()
        pendingVerificationId = null
        pendingResendToken = null
    }

    // ------------------------------------------------------------------
    // Throttle: 60s cooldown, max 5 resends per number per UTC day.
    // ------------------------------------------------------------------

    private fun throttleKeys(e164: String): Triple<String, String, String> {
        val digits = e164.filter { it.isDigit() }
        return Triple("last_sent_$digits", "day_$digits", "count_$digits")
    }

    private fun checkThrottle(e164: String) {
        val (lastKey, dayKey, countKey) = throttleKeys(e164)
        val now = System.currentTimeMillis()
        val lastSent = throttlePrefs.getLong(lastKey, 0L)
        if (now - lastSent < OTP_COOLDOWN_MILLIS) {
            val waitSec = (OTP_COOLDOWN_MILLIS - (now - lastSent)) / 1000 + 1
            throw OtpThrottledException(waitSec)
        }
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        val sentToday = if (throttlePrefs.getString(dayKey, null) == today) {
            throttlePrefs.getInt(countKey, 0)
        } else {
            0
        }
        if (sentToday >= MAX_RESENDS_PER_DAY) {
            throw OtpQuotaExceededException()
        }
    }

    private fun recordSent(e164: String) {
        val (lastKey, dayKey, countKey) = throttleKeys(e164)
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        val count = if (throttlePrefs.getString(dayKey, null) == today) {
            throttlePrefs.getInt(countKey, 0) + 1
        } else {
            1
        }
        throttlePrefs.edit()
            .putLong(lastKey, System.currentTimeMillis())
            .putString(dayKey, today)
            .putInt(countKey, count)
            .apply()
    }

    companion object {
        private val E164_REGEX = Regex("^\\+[1-9]\\d{7,14}$")
        private const val OTP_COOLDOWN_MILLIS = 60_000L
        private const val MAX_RESENDS_PER_DAY = 5
    }
}
