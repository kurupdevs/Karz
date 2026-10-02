package com.kurupdevs.karz.ui.screens.data

import android.net.Uri
import com.kurupdevs.karz.data.model.DocumentCategory
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.data.model.LoanDocument
import com.kurupdevs.karz.data.model.LoanType
import com.kurupdevs.karz.data.model.Payment
import com.kurupdevs.karz.data.model.PaymentStatus
import com.kurupdevs.karz.data.model.PaymentType
import com.kurupdevs.karz.data.model.RateType
import kotlinx.coroutines.flow.Flow

/**
 * Data contracts owned by the screens layer.
 *
 * Domain models come from the canonical `data.model` package
 * (money = minor units as Long, timestamps = UTC epoch millis).
 * The data layer implements [AuthGateway], [LoanRepository], [DocumentRepository]
 * and [ProfileRepository] against Firebase (Auth phone-OTP, Firestore,
 * Storage). Screens only ever talk to these interfaces, never to Firebase.
 *
 * Money is always minor units (paise / pence) as Long. Rates are Double percent.
 */

/* ------------------------------ local models -------------------------------- */

/** Draft used for both create and edit; id/status/timestamps live on the server. */
data class LoanDraft(
    val lenderName: String,
    val loanType: LoanType,
    val accountRefMasked: String = "",
    val principalOriginalMinor: Long,
    val currentBalanceMinor: Long,
    val annualRatePct: Double,
    val rateType: RateType,
    val tenureMonthsTotal: Int,
    val emiDayOfMonth: Int,
    val emiAmountMinor: Long,
    val startDateMillis: Long,
    val homeValueMinor: Long? = null,
    val currency: String = "INR"
)

/** One row of the client-generated amortization schedule. */
data class ScheduleRow(
    val n: Int,
    val dueDateMillis: Long,
    val emiMinor: Long,
    val principalMinor: Long,
    val interestMinor: Long,
    val balanceAfterMinor: Long,
    val status: PaymentStatus = PaymentStatus.SCHEDULED
)

/** An upload queued while offline; the repository completes it on reconnect. */
data class PendingUpload(
    val id: String,
    val fileName: String,
    val category: DocumentCategory,
    val mimeType: String,
    val sizeBytes: Long,
    val loanId: String? = null
)

data class UserProfile(
    val uid: String,
    val displayName: String,
    val phoneNumber: String,
    val homeCurrency: String = "INR",
    val timezone: String = "Asia/Kolkata",
    val notificationsEnabled: Boolean = true
)

/* -------------------------------- interfaces -------------------------------- */

/**
 * Phone-OTP auth. Implemented by the data layer with Firebase Auth.
 * The app never gates any screen behind a phone number beyond this one flow.
 */
interface AuthGateway {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(code: String): Result<Unit>
}

/**
 * Loans, payment ledger and amortization schedule.
 * Implemented by the data layer with Firestore (offline persistence on).
 */
interface LoanRepository {
    /** All non-archived loans, newest first. */
    fun getLoans(): Flow<List<Loan>>

    fun getLoan(loanId: String): Flow<Loan?>

    /**
     * Creates the loan, client-generates the schedule, seeds the next 3
     * scheduled payments, writes the audit log. Returns the new id.
     */
    suspend fun addLoan(draft: LoanDraft): Result<String>

    /** Field-mask update; surfaces "updated on another device" conflicts. */
    suspend fun updateLoan(loanId: String, draft: LoanDraft): Result<Unit>

    /** Soft delete: status = ARCHIVED. */
    suspend fun archiveLoan(loanId: String): Result<Unit>

    /** Duplicate guard: same lender + masked ref + type already exists. */
    suspend fun isDuplicate(
        lenderName: String,
        accountRefMasked: String,
        loanType: LoanType,
        excludeLoanId: String? = null
    ): Boolean

    /** Append-only payment ledger, newest first. Paid rows are immutable. */
    fun getPayments(loanId: String): Flow<List<Payment>>

    /**
     * Appends an immutable ledger entry; updates balance, next due date
     * and elapsed tenure. paidAtMillis is UTC epoch millis.
     */
    suspend fun recordPayment(
        loanId: String,
        amountMinor: Long,
        paidAtMillis: Long,
        type: PaymentType,
        note: String = ""
    ): Result<String>

    /** Client-generated amortization schedule, installment order. */
    fun getSchedule(loanId: String): Flow<List<ScheduleRow>>

    suspend fun updateEmiDay(loanId: String, emiDay: Int): Result<Unit>
}

/**
 * Document vault. Implemented by the data layer with Firebase Storage + Firestore
 * metadata. Uploads are queued offline and completed on reconnect.
 */
interface DocumentRepository {
    /** All docs (optionally filtered to one loan), newest first. */
    fun getDocuments(loanId: String? = null): Flow<List<LoanDocument>>

    /** Live count feeding the Manage screen "Saved documents N" tile. */
    fun getDocumentCount(): Flow<Int>

    /** Uploads currently queued offline. */
    fun getPendingUploads(): Flow<List<PendingUpload>>

    /**
     * Uploads to Storage (15MB cap, pdf/jpg/png/webp enforced by caller too).
     * When offline, queues and returns the pending id.
     */
    suspend fun uploadDocument(
        fileName: String,
        category: DocumentCategory,
        mimeType: String,
        contentUri: Uri,
        sizeBytes: Long,
        loanId: String? = null
    ): Result<String>

    suspend fun cancelPendingUpload(pendingId: String): Result<Unit>

    /** Deletes metadata; Storage object removal is best-effort. */
    suspend fun deleteDocument(docId: String): Result<Unit>

    /**
     * Returns a content:// URI the viewer can open (downloads to cache and
     * serves via FileProvider when needed).
     */
    suspend fun resolveContentUri(doc: LoanDocument): Result<Uri>
}

/**
 * Profile + preferences. Implemented by the data layer (Firestore users/{uid} + Auth).
 */
interface ProfileRepository {
    fun getProfile(): Flow<UserProfile>
    suspend fun updateDisplayName(name: String): Result<Unit>
    suspend fun updateCurrency(currencyCode: String): Result<Unit>
    suspend fun updateTimezone(timezoneId: String): Result<Unit>
    suspend fun updateNotificationsEnabled(enabled: Boolean): Result<Unit>
    suspend fun signOut(): Result<Unit>
}
