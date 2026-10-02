package com.kurupdevs.karz.data.model

import java.time.Instant
import java.time.LocalDate

/**
 * Canonical domain models. Money is always minor units (paise / pence) as
 * Long, timestamps are UTC epoch millis or java.time types, rates are
 * Double percent. The screens program against the interfaces in
 * ui.screens.data; the Firebase layer maps these models to Firestore docs.
 */

enum class LoanType {
    HOME, PERSONAL, CAR, EDUCATION, OTHER
}

enum class RateType {
    FIXED, FLOATING
}

enum class LoanStatus {
    ACTIVE, CLOSED, ARCHIVED
}

enum class PaymentType {
    EMI, PART_PREPAYMENT, FEE, OTHER
}

enum class PaymentStatus {
    SCHEDULED, PAID, MISSED, PARTIAL
}

enum class DocumentCategory {
    IDENTITY, INCOME, PROPERTY, LOAN, SANCTION, OTHER
}

data class Loan(
    val id: String,
    val lenderName: String,
    val loanType: LoanType,
    val accountRefMasked: String? = null,
    val principalOriginalMinor: Long,
    val currentBalanceMinor: Long,
    val annualRatePct: Double,
    val rateType: RateType,
    val tenureMonthsTotal: Int,
    val tenureMonthsElapsed: Int = 0,
    val emiDayOfMonth: Int,
    val emiAmountMinor: Long,
    val startDate: LocalDate,
    val nextDueDate: Instant,
    val homeValueMinor: Long? = null,
    val currency: String = "INR",
    val status: LoanStatus = LoanStatus.ACTIVE,
    val updatedAtMillis: Long = 0L
)

data class Payment(
    val id: String,
    val loanId: String,
    val amountMinor: Long,
    /** Null for scheduled (not yet paid) rows. */
    val paidAtMillis: Long?,
    val type: PaymentType,
    val status: PaymentStatus = PaymentStatus.SCHEDULED,
    val principalMinor: Long? = null,
    val interestMinor: Long? = null,
    val balanceAfterMinor: Long? = null,
    val note: String = ""
)

data class LoanDocument(
    val id: String,
    val fileName: String,
    val category: DocumentCategory,
    val mimeType: String,
    val sizeBytes: Long,
    /** Storage path for synced docs; the source content URI for pending uploads. */
    val storagePath: String = "",
    val thumbnailPath: String? = null,
    val loanId: String? = null,
    val pendingUpload: Boolean = false
)

/** LTV ratio 0..1+, null when no home value is set. */
val Loan.ltv: Double?
    get() = homeValueMinor
        ?.takeIf { it > 0 }
        ?.let { currentBalanceMinor.toDouble() / it }
