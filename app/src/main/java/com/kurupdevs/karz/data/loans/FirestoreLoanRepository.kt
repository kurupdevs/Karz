package com.kurupdevs.karz.data.loans

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.kurupdevs.karz.data.DuplicateLoanException
import com.kurupdevs.karz.data.FirebaseBackend
import com.kurupdevs.karz.data.StaleDataException
import com.kurupdevs.karz.data.auditMap
import com.kurupdevs.karz.data.auth.FirebaseAuthService
import com.kurupdevs.karz.data.awaitTask
import com.kurupdevs.karz.data.loanEditMask
import com.kurupdevs.karz.data.loanMap
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.data.model.LoanStatus
import com.kurupdevs.karz.data.model.LoanType
import com.kurupdevs.karz.data.model.Payment
import com.kurupdevs.karz.data.model.PaymentStatus
import com.kurupdevs.karz.data.model.PaymentType
import com.kurupdevs.karz.data.paymentMap
import com.kurupdevs.karz.data.scheduleMap
import com.kurupdevs.karz.data.sync.SyncState
import com.kurupdevs.karz.data.sync.toSyncState
import com.kurupdevs.karz.data.toLoan
import com.kurupdevs.karz.data.toPayment
import com.kurupdevs.karz.data.toScheduleRow
import com.kurupdevs.karz.data.updatedAtMillis
import com.kurupdevs.karz.math.emiFor
import com.kurupdevs.karz.math.generateSchedule
import com.kurupdevs.karz.ui.screens.data.LoanDraft
import com.kurupdevs.karz.ui.screens.data.LoanRepository
import com.kurupdevs.karz.ui.screens.data.ScheduleRow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.min

/**
 * Implements the screens contract [LoanRepository].
 *
 * Two stores, one interface: Firestore when Firebase is linked and a user is
 * signed in, otherwise a process-local in-memory store. The app is fully
 * usable offline-first either way: Firestore brings its own disk persistence,
 * and every batched write is queued automatically when the network is down.
 *
 * addLoan is a single batched write: loans/{id}, then the client-generated
 * schedule docs (at most 360, via the shared :math module, the one and only
 * amortization engine), then the next 3 scheduled payments, then an auditLog
 * entry. 1 + 360 + 3 + 1 = 365 writes, inside the 500-write batch limit.
 *
 * Conflict policy: loan edits are field-mask update() calls stamped with
 * serverTimestamp. The repository remembers the updatedAt it last served for
 * each loan; if the server value moved since, the write is refused with
 * [StaleDataException] ("updated on another device") instead of silently
 * overwriting.
 */
class FirestoreLoanRepository(
    private val auth: FirebaseAuthService,
    private val appContext: Context
) : LoanRepository {

    private val firestoreStores = mutableMapOf<String, FirestoreLoanStore>()
    private val lock = Any()

    private fun storeFor(uid: String): FirestoreLoanStore = synchronized(lock) {
        firestoreStores.getOrPut(uid) { FirestoreLoanStore(uid, appContext) }
    }

    /** Sync status driving the UI "syncing" indicator. Always LOCAL in memory mode. */
    val syncState: Flow<SyncState> = auth.currentUser.flatMapLatest { user ->
        if (user != null && FirebaseBackend.ensureChecked(appContext)) {
            storeFor(user.uid).syncState
        } else {
            flowOf(SyncState.LOCAL)
        }
    }

    private fun activeStore(): Flow<LoanStore> = auth.currentUser.map { user ->
        if (user != null && FirebaseBackend.ensureChecked(appContext)) {
            storeFor(user.uid) as LoanStore
        } else {
            MemoryLoanStore as LoanStore
        }
    }

    private suspend fun writeStore(): LoanStore {
        val user = auth.currentUserNow()
        return if (user != null && FirebaseBackend.ensureChecked(appContext)) {
            storeFor(user.uid)
        } else {
            MemoryLoanStore
        }
    }

    override fun getLoans(): Flow<List<Loan>> = activeStore().flatMapLatest { it.getLoans() }

    override fun getLoan(loanId: String): Flow<Loan?> =
        activeStore().flatMapLatest { it.getLoan(loanId) }

    override suspend fun addLoan(draft: LoanDraft): Result<String> =
        runCatching { writeStore().addLoan(draft).getOrThrow() }

    override suspend fun updateLoan(loanId: String, draft: LoanDraft): Result<Unit> =
        runCatching { writeStore().updateLoan(loanId, draft).getOrThrow() }

    override suspend fun archiveLoan(loanId: String): Result<Unit> =
        runCatching { writeStore().archiveLoan(loanId).getOrThrow() }

    override suspend fun isDuplicate(
        lenderName: String,
        accountRefMasked: String,
        loanType: LoanType,
        excludeLoanId: String?
    ): Boolean = writeStore().isDuplicate(lenderName, accountRefMasked, loanType, excludeLoanId)

    override fun getPayments(loanId: String): Flow<List<Payment>> =
        activeStore().flatMapLatest { it.getPayments(loanId) }

    override suspend fun recordPayment(
        loanId: String,
        amountMinor: Long,
        paidAtMillis: Long,
        type: PaymentType,
        note: String
    ): Result<String> =
        runCatching { writeStore().recordPayment(loanId, amountMinor, paidAtMillis, type, note).getOrThrow() }

    override fun getSchedule(loanId: String): Flow<List<ScheduleRow>> =
        activeStore().flatMapLatest { it.getSchedule(loanId) }

    override suspend fun updateEmiDay(loanId: String, emiDay: Int): Result<Unit> =
        runCatching { writeStore().updateEmiDay(loanId, emiDay).getOrThrow() }
}

/** Internal store contract; both backends implement the same semantics. */
internal interface LoanStore {
    fun getLoans(): Flow<List<Loan>>
    fun getLoan(loanId: String): Flow<Loan?>
    suspend fun addLoan(draft: LoanDraft): Result<String>
    suspend fun updateLoan(loanId: String, draft: LoanDraft): Result<Unit>
    suspend fun archiveLoan(loanId: String): Result<Unit>
    suspend fun isDuplicate(
        lenderName: String,
        accountRefMasked: String,
        loanType: LoanType,
        excludeLoanId: String?
    ): Boolean

    fun getPayments(loanId: String): Flow<List<Payment>>
    suspend fun recordPayment(
        loanId: String,
        amountMinor: Long,
        paidAtMillis: Long,
        type: PaymentType,
        note: String
    ): Result<String>

    fun getSchedule(loanId: String): Flow<List<ScheduleRow>>
    suspend fun updateEmiDay(loanId: String, emiDay: Int): Result<Unit>
}

// ---------------------------------------------------------------------------
// Shared validation and date helpers.
// ---------------------------------------------------------------------------

internal fun validateDraft(draft: LoanDraft) {
    require(draft.lenderName.isNotBlank()) { "Lender name is required." }
    require(draft.principalOriginalMinor > 0) { "Original principal must be positive." }
    require(draft.currentBalanceMinor >= 0) { "Outstanding balance cannot be negative." }
    require(draft.annualRatePct in 0.0..50.0) { "Rate must be between 0 and 50 percent." }
    require(draft.tenureMonthsTotal in 6..360) { "Tenure must be between 6 and 360 months." }
    require(draft.emiDayOfMonth in 1..28) { "EMI day must be between 1 and 28." }
    require(draft.startDateMillis <= System.currentTimeMillis()) { "Start date cannot be in the future." }
    require(draft.emiAmountMinor >= 0) { "EMI amount cannot be negative." }
    draft.homeValueMinor?.let { require(it > 0) { "Home value must be positive." } }
}

internal fun draftStartDate(draft: LoanDraft): LocalDate =
    Instant.ofEpochMilli(draft.startDateMillis).atZone(ZoneOffset.UTC).toLocalDate()

/** First EMI date: the first occurrence of emiDay strictly after startDate. */
internal fun firstDueDate(startDate: LocalDate, emiDay: Int): LocalDate {
    val day = min(emiDay, startDate.lengthOfMonth())
    var candidate = startDate.withDayOfMonth(day)
    if (!candidate.isAfter(startDate)) {
        candidate = candidate.plusMonths(1)
    }
    return candidate
}

internal fun advanceOneMonth(due: Instant, emiDay: Int): Instant =
    due.atZone(ZoneOffset.UTC).plusMonths(1).withDayOfMonth(min(emiDay, 28)).toInstant()

/**
 * One month of interest on a balance, rounded to minor units. Mirrors the
 * private roundedInterest() inside the :math module exactly (same formula,
 * same rounding); kept local so the data layer does not reach into the math
 * module's internals. The full schedule engine itself always comes from :math.
 */
internal fun monthInterestMinor(balanceMinor: Long, annualRatePct: Double): Long {
    if (annualRatePct == 0.0) return 0L
    val r = BigDecimal.valueOf(annualRatePct)
        .divide(BigDecimal("1200"), MathContext(32, RoundingMode.HALF_UP))
    return BigDecimal.valueOf(balanceMinor).multiply(r).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

internal fun isDuplicateMatch(
    loans: List<Loan>,
    lenderName: String,
    accountRefMasked: String,
    loanType: LoanType,
    excludeLoanId: String?
): Boolean {
    val lender = lenderName.trim().lowercase()
    val ref = accountRefMasked.trim()
    return loans.any {
        it.id != excludeLoanId &&
            it.lenderName.trim().lowercase() == lender &&
            it.loanType == loanType &&
            it.accountRefMasked.orEmpty() == ref
    }
}

/** Splits a payment into principal/interest the same way for both stores. */
internal fun splitPayment(
    balanceMinor: Long,
    amountMinor: Long,
    annualRatePct: Double,
    type: PaymentType
): Pair<Long, Long> = when (type) {
    PaymentType.EMI -> {
        val interest = monthInterestMinor(balanceMinor, annualRatePct)
        (amountMinor - interest).coerceAtLeast(0L) to interest
    }
    PaymentType.PART_PREPAYMENT, PaymentType.OTHER -> amountMinor.coerceAtLeast(0L) to 0L
    PaymentType.FEE -> 0L to 0L
}

// ---------------------------------------------------------------------------
// Firestore store.
// ---------------------------------------------------------------------------

private class FirestoreLoanStore(
    private val uid: String,
    private val appContext: Context
) : LoanStore {

    private val db: FirebaseFirestore get() = FirebaseBackend.firestore(appContext)!!
    private val loansCol get() = db.collection("users").document(uid).collection("loans")
    private fun auditCol() = db.collection("users").document(uid).collection("auditLog")

    private val _syncState = MutableStateFlow(SyncState.LOCAL)
    val syncState: Flow<SyncState> = _syncState

    /** updatedAt last served per loan, for the optimistic-concurrency check. */
    private val lastSeenUpdatedAt = mutableMapOf<String, Long?>()

    private fun <T> listen(query: Query, map: (List<DocumentSnapshot>) -> T): Flow<T> =
        callbackFlow {
            val reg = query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    _syncState.value = snap.metadata.toSyncState()
                    trySend(map(snap.documents))
                }
            }
            awaitClose { reg.remove() }
        }

    override fun getLoans(): Flow<List<Loan>> =
        listen(loansCol.orderBy("updatedAt", Query.Direction.DESCENDING).limit(100)) { docs ->
            docs.filter { it.getString("status") != LoanStatus.ARCHIVED.name }
                .map { it.toLoan() }
        }

    override fun getLoan(loanId: String): Flow<Loan?> =
        callbackFlow {
            val reg = loansCol.document(loanId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                    if (e != null) {
                        close(e)
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        _syncState.value = snap.metadata.toSyncState()
                        if (snap.exists()) {
                            lastSeenUpdatedAt[loanId] = snap.updatedAtMillis()
                            trySend(snap.toLoan())
                        } else {
                            trySend(null)
                        }
                    }
                }
            awaitClose { reg.remove() }
        }

    override fun getPayments(loanId: String): Flow<List<Payment>> =
        listen(
            loansCol.document(loanId).collection("payments")
                .orderBy("dueDate", Query.Direction.DESCENDING).limit(500)
        ) { docs -> docs.map { it.toPayment(loanId) } }

    override fun getSchedule(loanId: String): Flow<List<ScheduleRow>> =
        listen(
            loansCol.document(loanId).collection("schedule")
                .orderBy("n", Query.Direction.ASCENDING).limit(360)
        ) { docs -> docs.map { it.toScheduleRow() } }

    override suspend fun isDuplicate(
        lenderName: String,
        accountRefMasked: String,
        loanType: LoanType,
        excludeLoanId: String?
    ): Boolean = isDuplicateMatch(
        getLoans().first(), lenderName, accountRefMasked, loanType, excludeLoanId
    )

    override suspend fun addLoan(draft: LoanDraft): Result<String> = runCatching {
        validateDraft(draft)
        if (isDuplicate(draft.lenderName, draft.accountRefMasked, draft.loanType, null)) {
            throw DuplicateLoanException(
                "This loan already exists. Check your loan list before adding it again."
            )
        }
        val principal = draft.principalOriginalMinor
        val rate = BigDecimal.valueOf(draft.annualRatePct)
        val emi = emiFor(principal, rate, draft.tenureMonthsTotal)
        val startDate = draftStartDate(draft)
        val firstDue = firstDueDate(startDate, draft.emiDayOfMonth)
        val schedule = generateSchedule(principal, rate, draft.tenureMonthsTotal, firstDue)

        val loanId = loansCol.document().id
        val loanRef = loansCol.document(loanId)
        val batch = db.batch()
        batch.set(loanRef, loanMap(draft, emi, firstDue))
        schedule.forEach { inst ->
            val rowRef = loanRef.collection("schedule").document("%03d".format(inst.n))
            batch.set(rowRef, scheduleMap(inst))
        }
        schedule.take(3).forEach { inst ->
            val payRef = loanRef.collection("payments").document()
            batch.set(
                payRef, paymentMap(
                    amountMinor = inst.emiMinor,
                    dueDateMillis = inst.dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    paidAtMillis = null,
                    type = PaymentType.EMI,
                    status = PaymentStatus.SCHEDULED,
                    principalMinor = inst.principalMinor,
                    interestMinor = inst.interestMinor,
                    balanceAfterMinor = inst.balanceAfterMinor,
                    note = ""
                )
            )
        }
        batch.set(
            auditCol().document(),
            auditMap(
                "loan_created", "loan", loanId,
                mapOf("lenderName" to draft.lenderName.trim(), "tenureMonths" to draft.tenureMonthsTotal)
            )
        )
        batch.commit().awaitTask()
        lastSeenUpdatedAt[loanId] = System.currentTimeMillis()
        loanId
    }

    private suspend fun freshLoan(loanId: String): Pair<Loan, Long?> {
        val snap = loansCol.document(loanId).get().awaitTask()
        if (!snap.exists()) throw IllegalStateException("Loan not found.")
        return snap.toLoan() to snap.updatedAtMillis()
    }

    private fun checkConflict(loanId: String, freshUpdatedAt: Long?) {
        val seen = lastSeenUpdatedAt[loanId]
        if (seen != null && freshUpdatedAt != null && freshUpdatedAt != seen) {
            throw StaleDataException(
                "This loan was updated on another device. Reload it before saving your changes."
            )
        }
    }

    override suspend fun updateLoan(loanId: String, draft: LoanDraft): Result<Unit> = runCatching {
        validateDraft(draft)
        val (_, freshUpdatedAt) = freshLoan(loanId)
        checkConflict(loanId, freshUpdatedAt)
        val updates = loanEditMask(draft).toMutableMap()
        updates["updatedAt"] = Timestamp.now()
        loansCol.document(loanId).update(updates).awaitTask()
        lastSeenUpdatedAt[loanId] = System.currentTimeMillis()
    }

    override suspend fun archiveLoan(loanId: String): Result<Unit> = runCatching {
        val (_, freshUpdatedAt) = freshLoan(loanId)
        checkConflict(loanId, freshUpdatedAt)
        loansCol.document(loanId).update(
            mapOf("status" to LoanStatus.ARCHIVED.name, "updatedAt" to Timestamp.now())
        ).awaitTask()
        lastSeenUpdatedAt.remove(loanId)
    }

    override suspend fun recordPayment(
        loanId: String,
        amountMinor: Long,
        paidAtMillis: Long,
        type: PaymentType,
        note: String
    ): Result<String> = runCatching {
        require(amountMinor > 0) { "Payment amount must be positive." }
        val loanRef = loansCol.document(loanId)
        val (loan, freshUpdatedAt) = freshLoan(loanId)
        checkConflict(loanId, freshUpdatedAt)

        val balance = loan.currentBalanceMinor
        val (principal, interest) = splitPayment(balance, amountMinor, loan.annualRatePct, type)
        val newBalance = (balance - principal).coerceAtLeast(0L)
        val dueDateMillis = if (type == PaymentType.EMI) {
            loan.nextDueDate.toEpochMilli()
        } else {
            paidAtMillis
        }
        val nextDue = if (type == PaymentType.EMI) {
            advanceOneMonth(loan.nextDueDate, loan.emiDayOfMonth)
        } else {
            loan.nextDueDate
        }
        val elapsed = if (type == PaymentType.EMI) loan.tenureMonthsElapsed + 1
        else loan.tenureMonthsElapsed

        val batch = db.batch()
        val payRef = loanRef.collection("payments").document()
        batch.set(
            payRef, paymentMap(
                amountMinor = amountMinor,
                dueDateMillis = dueDateMillis,
                paidAtMillis = paidAtMillis,
                type = type,
                status = PaymentStatus.PAID,
                principalMinor = principal,
                interestMinor = interest,
                balanceAfterMinor = newBalance,
                note = note
            )
        )
        batch.update(
            loanRef, mapOf(
                "currentBalanceMinor" to newBalance,
                "nextDueDate" to nextDue.toEpochMilli(),
                "tenureMonthsElapsed" to elapsed.toLong(),
                "updatedAt" to Timestamp.now()
            )
        )
        batch.set(
            auditCol().document(),
            auditMap(
                "payment_recorded", "payment", payRef.id,
                mapOf("loanId" to loanId, "amountMinor" to amountMinor, "type" to type.name)
            )
        )
        batch.commit().awaitTask()
        lastSeenUpdatedAt[loanId] = System.currentTimeMillis()
        payRef.id
    }

    override suspend fun updateEmiDay(loanId: String, emiDay: Int): Result<Unit> = runCatching {
        require(emiDay in 1..28) { "EMI day must be between 1 and 28." }
        val (loan, freshUpdatedAt) = freshLoan(loanId)
        checkConflict(loanId, freshUpdatedAt)
        val now = Instant.now()
        var next = loan.nextDueDate.atZone(ZoneOffset.UTC).withDayOfMonth(emiDay).toInstant()
        if (!next.isAfter(now)) {
            next = next.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()
        }
        loansCol.document(loanId).update(
            mapOf(
                "emiDayOfMonth" to emiDay.toLong(),
                "nextDueDate" to next.toEpochMilli(),
                "updatedAt" to Timestamp.now()
            )
        ).awaitTask()
        lastSeenUpdatedAt[loanId] = System.currentTimeMillis()
    }
}

// ---------------------------------------------------------------------------
// In-memory store: used when Firebase is not linked or nobody is signed in.
// Same semantics, local only. Nothing here ever touches the network.
// ---------------------------------------------------------------------------

private object MemoryLoanStore : LoanStore {

    private data class StoredLoan(
        val loan: Loan,
        val updatedAtMillis: Long,
        val schedule: List<ScheduleRow>,
        val payments: List<Payment>
    )

    private val loans = MutableStateFlow<Map<String, StoredLoan>>(emptyMap())
    private val lastSeenUpdatedAt = mutableMapOf<String, Long?>()

    private fun checkConflict(loanId: String, stored: StoredLoan) {
        val seen = lastSeenUpdatedAt[loanId]
        if (seen != null && stored.updatedAtMillis != seen) {
            throw StaleDataException(
                "This loan was updated on another device. Reload it before saving your changes."
            )
        }
    }

    override fun getLoans(): Flow<List<Loan>> =
        loans.map { map ->
            map.values.filter { it.loan.status != LoanStatus.ARCHIVED }
                .sortedByDescending { it.updatedAtMillis }
                .map { it.loan }
        }

    override fun getLoan(loanId: String): Flow<Loan?> =
        loans.map { it[loanId] }.onEach { stored ->
            lastSeenUpdatedAt[loanId] = stored?.updatedAtMillis
        }.map { it?.loan }

    override fun getPayments(loanId: String): Flow<List<Payment>> =
        loans.map { map ->
            map[loanId]?.payments?.sortedByDescending { it.paidAtMillis ?: 0L } ?: emptyList()
        }

    override fun getSchedule(loanId: String): Flow<List<ScheduleRow>> =
        loans.map { map -> map[loanId]?.schedule ?: emptyList() }

    override suspend fun isDuplicate(
        lenderName: String,
        accountRefMasked: String,
        loanType: LoanType,
        excludeLoanId: String?
    ): Boolean = isDuplicateMatch(
        getLoans().first(), lenderName, accountRefMasked, loanType, excludeLoanId
    )

    override suspend fun addLoan(draft: LoanDraft): Result<String> = runCatching {
        validateDraft(draft)
        if (isDuplicate(draft.lenderName, draft.accountRefMasked, draft.loanType, null)) {
            throw DuplicateLoanException(
                "This loan already exists. Check your loan list before adding it again."
            )
        }
        val principal = draft.principalOriginalMinor
        val rate = BigDecimal.valueOf(draft.annualRatePct)
        val emi = emiFor(principal, rate, draft.tenureMonthsTotal)
        val startDate = draftStartDate(draft)
        val firstDue = firstDueDate(startDate, draft.emiDayOfMonth)
        val schedule = generateSchedule(principal, rate, draft.tenureMonthsTotal, firstDue)
            .map {
                ScheduleRow(
                    n = it.n,
                    dueDateMillis = it.dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    emiMinor = it.emiMinor,
                    principalMinor = it.principalMinor,
                    interestMinor = it.interestMinor,
                    balanceAfterMinor = it.balanceAfterMinor,
                    status = PaymentStatus.SCHEDULED
                )
            }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val loan = Loan(
            id = id,
            lenderName = draft.lenderName.trim(),
            loanType = draft.loanType,
            accountRefMasked = draft.accountRefMasked.trim().ifEmpty { null },
            principalOriginalMinor = draft.principalOriginalMinor,
            currentBalanceMinor = draft.currentBalanceMinor,
            annualRatePct = draft.annualRatePct,
            rateType = draft.rateType,
            tenureMonthsTotal = draft.tenureMonthsTotal,
            emiDayOfMonth = draft.emiDayOfMonth,
            emiAmountMinor = emi,
            startDate = startDate,
            nextDueDate = firstDue.atStartOfDay(ZoneOffset.UTC).toInstant(),
            homeValueMinor = draft.homeValueMinor,
            currency = draft.currency,
            status = LoanStatus.ACTIVE,
            updatedAtMillis = now
        )
        val seedPayments = schedule.take(3).map { row ->
            Payment(
                id = UUID.randomUUID().toString(),
                loanId = id,
                amountMinor = row.emiMinor,
                paidAtMillis = null,
                type = PaymentType.EMI,
                status = PaymentStatus.SCHEDULED,
                principalMinor = row.principalMinor,
                interestMinor = row.interestMinor,
                balanceAfterMinor = row.balanceAfterMinor,
                note = ""
            )
        }
        loans.update { it + (id to StoredLoan(loan, now, schedule, seedPayments)) }
        lastSeenUpdatedAt[id] = now
        id
    }

    override suspend fun updateLoan(loanId: String, draft: LoanDraft): Result<Unit> = runCatching {
        validateDraft(draft)
        val stored = loans.value[loanId] ?: throw IllegalStateException("Loan not found.")
        checkConflict(loanId, stored)
        val now = System.currentTimeMillis()
        val updated = stored.loan.copy(
            lenderName = draft.lenderName.trim(),
            loanType = draft.loanType,
            accountRefMasked = draft.accountRefMasked.trim().ifEmpty { null },
            principalOriginalMinor = draft.principalOriginalMinor,
            currentBalanceMinor = draft.currentBalanceMinor,
            annualRatePct = draft.annualRatePct,
            rateType = draft.rateType,
            tenureMonthsTotal = draft.tenureMonthsTotal,
            emiDayOfMonth = draft.emiDayOfMonth,
            emiAmountMinor = draft.emiAmountMinor,
            homeValueMinor = draft.homeValueMinor,
            currency = draft.currency,
            updatedAtMillis = now
        )
        loans.update { it + (loanId to stored.copy(loan = updated, updatedAtMillis = now)) }
        lastSeenUpdatedAt[loanId] = now
    }

    override suspend fun archiveLoan(loanId: String): Result<Unit> = runCatching {
        val stored = loans.value[loanId] ?: throw IllegalStateException("Loan not found.")
        checkConflict(loanId, stored)
        val now = System.currentTimeMillis()
        loans.update {
            it + (loanId to stored.copy(
                loan = stored.loan.copy(status = LoanStatus.ARCHIVED, updatedAtMillis = now),
                updatedAtMillis = now
            ))
        }
        lastSeenUpdatedAt.remove(loanId)
    }

    override suspend fun recordPayment(
        loanId: String,
        amountMinor: Long,
        paidAtMillis: Long,
        type: PaymentType,
        note: String
    ): Result<String> = runCatching {
        require(amountMinor > 0) { "Payment amount must be positive." }
        val stored = loans.value[loanId] ?: throw IllegalStateException("Loan not found.")
        checkConflict(loanId, stored)
        val loan = stored.loan
        val balance = loan.currentBalanceMinor
        val (principal, interest) = splitPayment(balance, amountMinor, loan.annualRatePct, type)
        val newBalance = (balance - principal).coerceAtLeast(0L)
        val nextDue = if (type == PaymentType.EMI) {
            advanceOneMonth(loan.nextDueDate, loan.emiDayOfMonth)
        } else {
            loan.nextDueDate
        }
        val elapsed = if (type == PaymentType.EMI) loan.tenureMonthsElapsed + 1
        else loan.tenureMonthsElapsed
        val paymentId = UUID.randomUUID().toString()
        val payment = Payment(
            id = paymentId,
            loanId = loanId,
            amountMinor = amountMinor,
            paidAtMillis = paidAtMillis,
            type = type,
            status = PaymentStatus.PAID,
            principalMinor = principal,
            interestMinor = interest,
            balanceAfterMinor = newBalance,
            note = note
        )
        val now = System.currentTimeMillis()
        val updated = loan.copy(
            currentBalanceMinor = newBalance,
            nextDueDate = nextDue,
            tenureMonthsElapsed = elapsed,
            updatedAtMillis = now
        )
        loans.update {
            it + (loanId to stored.copy(
                loan = updated,
                updatedAtMillis = now,
                payments = stored.payments + payment
            ))
        }
        lastSeenUpdatedAt[loanId] = now
        paymentId
    }

    override suspend fun updateEmiDay(loanId: String, emiDay: Int): Result<Unit> = runCatching {
        require(emiDay in 1..28) { "EMI day must be between 1 and 28." }
        val stored = loans.value[loanId] ?: throw IllegalStateException("Loan not found.")
        checkConflict(loanId, stored)
        val nowInstant = Instant.now()
        var next = stored.loan.nextDueDate.atZone(ZoneOffset.UTC).withDayOfMonth(emiDay).toInstant()
        if (!next.isAfter(nowInstant)) {
            next = next.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()
        }
        val now = System.currentTimeMillis()
        loans.update {
            it + (loanId to stored.copy(
                loan = stored.loan.copy(emiDayOfMonth = emiDay, nextDueDate = next, updatedAtMillis = now),
                updatedAtMillis = now
            ))
        }
        lastSeenUpdatedAt[loanId] = now
    }
}
