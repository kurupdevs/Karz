package com.kurupdevs.karz.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.kurupdevs.karz.data.model.DocumentCategory
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.data.model.LoanDocument
import com.kurupdevs.karz.data.model.LoanStatus
import com.kurupdevs.karz.data.model.LoanType
import com.kurupdevs.karz.data.model.Payment
import com.kurupdevs.karz.data.model.PaymentStatus
import com.kurupdevs.karz.data.model.PaymentType
import com.kurupdevs.karz.data.model.RateType
import com.kurupdevs.karz.math.Installment
import com.kurupdevs.karz.ui.screens.data.LoanDraft
import com.kurupdevs.karz.ui.screens.data.ScheduleRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Firestore document shapes, in one place. Money is stored as Long minor
 * units (full precision, no narrowing). Dates are ISO-8601 strings for
 * schedule rows and epoch millis for instants. serverTimestamp() is used for
 * createdAt/updatedAt; updatedAt doubles as the optimistic-concurrency token
 * for loan edits.
 */

private fun DocumentSnapshot.reqString(field: String): String =
    getString(field) ?: throw IllegalStateException("missing $field")

private fun DocumentSnapshot.reqLong(field: String): Long =
    getLong(field) ?: throw IllegalStateException("missing $field")

private fun DocumentSnapshot.reqDouble(field: String): Double =
    getDouble(field) ?: throw IllegalStateException("missing $field")

fun DocumentSnapshot.toLoan(): Loan {
    return Loan(
        id = id,
        lenderName = reqString("lenderName"),
        loanType = LoanType.valueOf(reqString("loanType")),
        accountRefMasked = getString("accountRefMasked")?.ifEmpty { null },
        principalOriginalMinor = reqLong("principalOriginalMinor"),
        currentBalanceMinor = reqLong("currentBalanceMinor"),
        annualRatePct = reqDouble("annualRatePct"),
        rateType = RateType.valueOf(reqString("rateType")),
        tenureMonthsTotal = reqLong("tenureMonthsTotal").toInt(),
        tenureMonthsElapsed = (getLong("tenureMonthsElapsed") ?: 0L).toInt(),
        emiDayOfMonth = reqLong("emiDayOfMonth").toInt(),
        emiAmountMinor = reqLong("emiAmountMinor"),
        startDate = LocalDate.parse(reqString("startDate")),
        nextDueDate = Instant.ofEpochMilli(reqLong("nextDueDate")),
        homeValueMinor = getLong("homeValueMinor"),
        currency = getString("currency") ?: "INR",
        status = LoanStatus.valueOf(reqString("status")),
        updatedAtMillis = this.updatedAtMillis() ?: 0L
    )
}

/** updatedAt millis for the optimistic-concurrency check. Null until the server stamps it. */
fun DocumentSnapshot.updatedAtMillis(): Long? =
    getTimestamp("updatedAt")?.toDate()?.time

fun loanMap(draft: LoanDraft, emiMinor: Long, firstDueDate: LocalDate): Map<String, Any?> {
    val startDate = Instant.ofEpochMilli(draft.startDateMillis)
        .atZone(ZoneOffset.UTC).toLocalDate()
    return mapOf(
        "lenderName" to draft.lenderName.trim(),
        "loanType" to draft.loanType.name,
        "accountRefMasked" to draft.accountRefMasked.trim(),
        "principalOriginalMinor" to draft.principalOriginalMinor,
        "currentBalanceMinor" to draft.currentBalanceMinor,
        "annualRatePct" to draft.annualRatePct,
        "rateType" to draft.rateType.name,
        "tenureMonthsTotal" to draft.tenureMonthsTotal.toLong(),
        "tenureMonthsElapsed" to 0L,
        "emiDayOfMonth" to draft.emiDayOfMonth.toLong(),
        "emiAmountMinor" to emiMinor,
        "startDate" to startDate.toString(),
        "nextDueDate" to firstDueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        "homeValueMinor" to draft.homeValueMinor,
        "currency" to draft.currency,
        "status" to LoanStatus.ACTIVE.name,
        "schemaVersion" to 1L,
        "createdAt" to Timestamp.now(),
        "updatedAt" to Timestamp.now()
    )
}

/** Field mask for loan edits. Only these keys are ever written by updateLoan. */
fun loanEditMask(draft: LoanDraft): Map<String, Any?> = mapOf(
    "lenderName" to draft.lenderName.trim(),
    "loanType" to draft.loanType.name,
    "accountRefMasked" to draft.accountRefMasked.trim(),
    "principalOriginalMinor" to draft.principalOriginalMinor,
    "currentBalanceMinor" to draft.currentBalanceMinor,
    "annualRatePct" to draft.annualRatePct,
    "rateType" to draft.rateType.name,
    "tenureMonthsTotal" to draft.tenureMonthsTotal.toLong(),
    "emiDayOfMonth" to draft.emiDayOfMonth.toLong(),
    "emiAmountMinor" to draft.emiAmountMinor,
    "homeValueMinor" to draft.homeValueMinor,
    "currency" to draft.currency
)

fun scheduleMap(inst: Installment): Map<String, Any?> = mapOf(
    "n" to inst.n.toLong(),
    "dueDate" to inst.dueDate.toString(),
    "emiMinor" to inst.emiMinor,
    "principalMinor" to inst.principalMinor,
    "interestMinor" to inst.interestMinor,
    "balanceAfterMinor" to inst.balanceAfterMinor,
    "status" to PaymentStatus.SCHEDULED.name
)

fun DocumentSnapshot.toScheduleRow(): ScheduleRow = ScheduleRow(
    n = reqLong("n").toInt(),
    dueDateMillis = LocalDate.parse(reqString("dueDate"))
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    emiMinor = reqLong("emiMinor"),
    principalMinor = reqLong("principalMinor"),
    interestMinor = reqLong("interestMinor"),
    balanceAfterMinor = reqLong("balanceAfterMinor"),
    status = PaymentStatus.valueOf(getString("status") ?: PaymentStatus.SCHEDULED.name)
)

fun paymentMap(
    amountMinor: Long,
    dueDateMillis: Long,
    paidAtMillis: Long?,
    type: PaymentType,
    status: PaymentStatus,
    principalMinor: Long,
    interestMinor: Long,
    balanceAfterMinor: Long,
    note: String
): Map<String, Any?> = mapOf(
    "amountMinor" to amountMinor,
    "dueDate" to dueDateMillis,
    "paidAt" to paidAtMillis,
    "type" to type.name,
    "status" to status.name,
    "principalMinor" to principalMinor,
    "interestMinor" to interestMinor,
    "balanceAfterMinor" to balanceAfterMinor,
    "note" to note,
    "createdAt" to Timestamp.now()
)

fun DocumentSnapshot.toPayment(loanId: String): Payment = Payment(
    id = id,
    loanId = loanId,
    amountMinor = reqLong("amountMinor"),
    paidAtMillis = getLong("paidAt"),
    type = PaymentType.valueOf(reqString("type")),
    status = PaymentStatus.valueOf(reqString("status")),
    principalMinor = getLong("principalMinor"),
    interestMinor = getLong("interestMinor"),
    balanceAfterMinor = getLong("balanceAfterMinor"),
    note = getString("note").orEmpty()
)

fun documentMap(
    fileName: String,
    category: DocumentCategory,
    mimeType: String,
    sizeBytes: Long,
    storagePath: String,
    loanId: String?,
    tags: List<String>
): Map<String, Any?> = mapOf(
    "fileName" to fileName,
    "category" to category.name,
    "mimeType" to mimeType,
    "sizeBytes" to sizeBytes,
    "storagePath" to storagePath,
    "loanId" to loanId,
    "tags" to tags,
    "uploadedAt" to Timestamp.now()
)

fun DocumentSnapshot.toLoanDocument(pendingUpload: Boolean = false): LoanDocument = LoanDocument(
    id = id,
    fileName = reqString("fileName"),
    category = DocumentCategory.valueOf(reqString("category")),
    mimeType = reqString("mimeType"),
    sizeBytes = reqLong("sizeBytes"),
    storagePath = getString("storagePath").orEmpty(),
    thumbnailPath = getString("thumbnailPath"),
    loanId = getString("loanId"),
    pendingUpload = pendingUpload
)

fun auditMap(action: String, entityType: String, entityId: String, metadata: Map<String, Any?>): Map<String, Any?> =
    mapOf(
        "action" to action,
        "entityType" to entityType,
        "entityId" to entityId,
        "metadata" to metadata,
        "createdAt" to Timestamp.now()
    )
