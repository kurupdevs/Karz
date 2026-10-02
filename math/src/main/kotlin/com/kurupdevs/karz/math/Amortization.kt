package com.kurupdevs.karz.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.ln

/**
 * Pure loan math for Karz. Zero Android dependencies, plain JVM only.
 *
 * Conventions, read before touching:
 * - Every money value is in MINOR units (paise for INR, pence for GBP) as Long.
 * - All intermediate math is BigDecimal with [MATH_CONTEXT] (precision 32, HALF_UP).
 *   Rounding to minor units happens ONLY at boundaries: the EMI quote and the
 *   per-installment interest. Nothing in between is ever rounded.
 * - Monthly rate r = annualRatePct / 1200.
 */
val MATH_CONTEXT: MathContext = MathContext(32, RoundingMode.HALF_UP)

private val ONE = BigDecimal.ONE
private val TWELVE_HUNDRED = BigDecimal("1200")

/** Monthly rate as a decimal fraction. 8.5% annual -> 0.0070833... */
fun monthlyRate(annualRatePct: BigDecimal): BigDecimal {
    require(annualRatePct >= BigDecimal.ZERO) { "annual rate cannot be negative" }
    return annualRatePct.divide(TWELVE_HUNDRED, MATH_CONTEXT)
}

/**
 * EMI in minor units.
 * EMI = P * r * (1+r)^n / ((1+r)^n - 1). When r == 0 the loan is a straight
 * split: P / n. The exact value is rounded to minor units once, HALF_UP.
 */
fun emiFor(principalMinor: Long, annualRatePct: BigDecimal, tenureMonths: Int): Long {
    require(principalMinor > 0) { "principal must be positive" }
    require(tenureMonths >= 1) { "tenure must be at least 1 month" }
    val r = monthlyRate(annualRatePct)
    val exact = if (r.compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal(principalMinor).divide(BigDecimal(tenureMonths), MATH_CONTEXT)
    } else {
        val onePlusR = ONE.add(r)
        val pow = onePlusR.pow(tenureMonths, MATH_CONTEXT)
        BigDecimal(principalMinor)
            .multiply(r, MATH_CONTEXT)
            .multiply(pow, MATH_CONTEXT)
            .divide(pow.subtract(ONE), MATH_CONTEXT)
    }
    return exact.setScale(0, RoundingMode.HALF_UP).longValueExact()
}

/** One row of an amortization schedule. All money in minor units. */
data class Installment(
    val n: Int,
    val dueDate: LocalDate,
    val emiMinor: Long,
    val principalMinor: Long,
    val interestMinor: Long,
    val balanceAfterMinor: Long,
)

private fun roundedInterest(balanceMinor: Long, r: BigDecimal): Long =
    if (r.compareTo(BigDecimal.ZERO) == 0) 0L
    else BigDecimal(balanceMinor).multiply(r, MATH_CONTEXT)
        .setScale(0, RoundingMode.HALF_UP).longValueExact()

/**
 * Full amortization schedule.
 *
 * Per installment: interest = round(balance * r) to minor units,
 * principal = EMI - interest. The LAST installment absorbs any rounding
 * residue (principal = whatever is left), so the balance ends at exactly 0
 * and the last EMI can differ from the quoted EMI by a few paise. That is
 * standard lender behavior, not a bug.
 */
fun generateSchedule(
    principalMinor: Long,
    annualRatePct: BigDecimal,
    tenureMonths: Int,
    firstDueDate: LocalDate,
): List<Installment> {
    val emi = emiFor(principalMinor, annualRatePct, tenureMonths)
    val r = monthlyRate(annualRatePct)
    val out = ArrayList<Installment>(tenureMonths)
    var balance = principalMinor
    var date = firstDueDate
    for (i in 1..tenureMonths) {
        val interest = roundedInterest(balance, r)
        val isLast = i == tenureMonths
        val principal = if (isLast) balance else emi - interest
        require(principal >= 0) { "EMI does not cover interest in month $i" }
        val actualEmi = if (isLast) principal + interest else emi
        balance -= principal
        out.add(Installment(i, date, actualEmi, principal, interest, balance))
        date = date.plusMonths(1)
    }
    check(out.last().balanceAfterMinor == 0L) { "schedule did not terminate at zero" }
    return out
}

/**
 * Tenure in months for a fixed EMI: n = ceil(-ln(1 - r*P/EMI) / ln(1+r)).
 *
 * The closed form is computed in doubles, then corrected by exact simulation
 * so the answer is the TRUE minimum months for the given rounded EMI. (A
 * paise-rounded EMI is a hair below the exact EMI, so the naive formula can
 * be off by one; the correction loop fixes it.)
 *
 * Throws if the EMI does not cover the first month's interest, the loan would
 * never amortize in that case.
 */
fun tenureFromEmi(principalMinor: Long, annualRatePct: BigDecimal, emiMinor: Long): Int {
    require(principalMinor > 0) { "principal must be positive" }
    require(emiMinor > 0) { "EMI must be positive" }
    val r = monthlyRate(annualRatePct)
    if (r.compareTo(BigDecimal.ZERO) == 0) {
        return ceil(principalMinor.toDouble() / emiMinor).toInt()
    }
    val firstInterest = roundedInterest(principalMinor, r)
    require(emiMinor > firstInterest) {
        "EMI ${formatMoney(emiMinor)} does not cover one month's interest " +
            "${formatMoney(firstInterest)}; the loan would never pay off"
    }
    val rD = r.toDouble()
    var n = ceil(-ln(1 - rD * principalMinor / emiMinor) / ln(1 + rD)).toInt().coerceAtLeast(1)
    // Correct to the true minimum: simulate the payoff at this exact EMI.
    while (n > 1 && monthsToPayoff(principalMinor, r, emiMinor) < n) n--
    while (monthsToPayoff(principalMinor, r, emiMinor) > n) n++
    return n
}

/** Exact months for a balance to hit zero paying a fixed EMI every month. */
private fun monthsToPayoff(principalMinor: Long, r: BigDecimal, emiMinor: Long, cap: Int = 1200): Int {
    var balance = principalMinor
    var months = 0
    while (balance > 0 && months < cap) {
        val interest = roundedInterest(balance, r)
        balance -= (emiMinor - interest)
        months++
    }
    return months
}

// ---------------------------------------------------------------------------
// LTV bands
// ---------------------------------------------------------------------------

/** LTV band label plus the explainer copy shown under the LTV ring. */
data class LtvBand(val key: String, val label: String, val copy: String)

/**
 * LTV band per spec: <=60 strong equity, 60-75 healthy (70% is the highest ROI
 * zone for prepayments), 75-90 high leverage, >90 thin equity.
 */
fun ltvBand(ltvPct: Double): LtvBand = when {
    ltvPct <= 60.0 -> LtvBand(
        key = "strong_equity",
        label = "Strong equity",
        copy = "Solid ground. You own most of your home, so lenders see you as low risk and better rates start opening up.",
    )
    ltvPct <= 75.0 -> LtvBand(
        key = "healthy",
        label = "Healthy",
        copy = "Extra payments here cut interest fast. The 70% band is the highest ROI zone for prepayments.",
    )
    ltvPct <= 90.0 -> LtvBand(
        key = "high_leverage",
        label = "High leverage",
        copy = "Most of the home is still the bank's. Small rate moves hurt more at this level, so keep an eye on your rate.",
    )
    else -> LtvBand(
        key = "thin_equity",
        label = "Thin equity",
        copy = "Almost no cushion yet. Building equity is priority one before anything fancy.",
    )
}

// ---------------------------------------------------------------------------
// Prepayment scenarios (one-time lump, SPEC section 7 semantics)
// ---------------------------------------------------------------------------

enum class PrepayStrategy { REDUCE_EMI, REDUCE_TENURE }

sealed interface PrepayResult {
    val interestSavedMinor: Long
    val newSchedule: List<Installment>
}

data class ReduceEmiResult(
    val newEmiMinor: Long,
    override val interestSavedMinor: Long,
    /** True when the payoff date is unchanged (pure EMI-cut). */
    val payoffSame: Boolean,
    override val newSchedule: List<Installment>,
) : PrepayResult

data class ReduceTenureResult(
    val newTenureMonths: Int,
    val newPayoffDate: LocalDate,
    override val interestSavedMinor: Long,
    override val newSchedule: List<Installment>,
) : PrepayResult

data class PrepayComparison(
    val reduceEmi: ReduceEmiResult,
    val reduceTenure: ReduceTenureResult,
    val recommended: PrepayStrategy,
    val recommendationReason: String,
)

/**
 * One-time prepayment of [prepayMinor] applied after [paidInstallments]
 * installments (0 = before the first EMI, i.e. on the full principal).
 *
 * - REDUCE_EMI: balance drops to P' = balance - prepay, EMI is recomputed on
 *   P' over the SAME remaining tenure. Payoff date does not move.
 * - REDUCE_TENURE: EMI stays the same, tenure shrinks to the true minimum
 *   for P' at that EMI. Payoff date moves earlier.
 *
 * interestSaved compares the remaining interest of the old schedule against
 * the new one. Throws if the prepay is not positive or covers the whole
 * outstanding (that is a loan closure, a different flow).
 */
fun prepayScenario(
    schedule: List<Installment>,
    annualRatePct: BigDecimal,
    prepayMinor: Long,
    strategy: PrepayStrategy,
    paidInstallments: Int = 0,
): PrepayResult {
    require(schedule.isNotEmpty()) { "schedule is empty" }
    require(prepayMinor > 0) { "prepay amount must be positive" }
    require(paidInstallments in 0..schedule.size) { "paidInstallments out of range" }

    val principal = schedule[0].principalMinor + schedule[0].balanceAfterMinor
    val balance = if (paidInstallments == 0) principal else schedule[paidInstallments - 1].balanceAfterMinor
    require(balance > 0) { "loan is already paid off" }
    require(prepayMinor < balance) {
        "prepay of ${formatMoney(prepayMinor)} covers the full outstanding " +
            "${formatMoney(balance)}; close the loan instead"
    }

    val remaining = schedule.drop(paidInstallments)
    val remainingCount = remaining.size
    val oldRemainingInterest = remaining.sumOf { it.interestMinor }
    val currentEmi = remaining.first().emiMinor
    val firstDueDate = remaining.first().dueDate
    val newBalance = balance - prepayMinor

    return when (strategy) {
        PrepayStrategy.REDUCE_EMI -> {
            val newEmi = emiFor(newBalance, annualRatePct, remainingCount)
            val newSchedule = generateSchedule(newBalance, annualRatePct, remainingCount, firstDueDate)
            val newInterest = newSchedule.sumOf { it.interestMinor }
            ReduceEmiResult(
                newEmiMinor = newEmi,
                interestSavedMinor = oldRemainingInterest - newInterest,
                payoffSame = true,
                newSchedule = newSchedule,
            )
        }
        PrepayStrategy.REDUCE_TENURE -> {
            val newN = tenureFromEmi(newBalance, annualRatePct, currentEmi)
            val newSchedule = generateSchedule(newBalance, annualRatePct, newN, firstDueDate)
            val newInterest = newSchedule.sumOf { it.interestMinor }
            ReduceTenureResult(
                newTenureMonths = newN,
                newPayoffDate = newSchedule.last().dueDate,
                interestSavedMinor = oldRemainingInterest - newInterest,
                newSchedule = newSchedule,
            )
        }
    }
}

/**
 * Both strategies side by side with a recommendation. Banks silently default
 * prepayments to EMI reduction, which is usually the worse option, so the
 * recommendation calls out whichever saves more interest and says by how much.
 */
fun comparePrepayStrategies(
    schedule: List<Installment>,
    annualRatePct: BigDecimal,
    prepayMinor: Long,
    paidInstallments: Int = 0,
): PrepayComparison {
    val emi = prepayScenario(schedule, annualRatePct, prepayMinor, PrepayStrategy.REDUCE_EMI, paidInstallments) as ReduceEmiResult
    val tenure = prepayScenario(schedule, annualRatePct, prepayMinor, PrepayStrategy.REDUCE_TENURE, paidInstallments) as ReduceTenureResult
    val recommended = if (tenure.interestSavedMinor >= emi.interestSavedMinor) {
        PrepayStrategy.REDUCE_TENURE
    } else {
        PrepayStrategy.REDUCE_EMI
    }
    val monthsDiff = remainingMonths(schedule, paidInstallments) - tenure.newTenureMonths
    val sooner = if (monthsDiff > 0) {
        " And you are debt free ${formatDuration(monthsDiff)} sooner."
    } else {
        ""
    }
    val reason = if (recommended == PrepayStrategy.REDUCE_TENURE) {
        val extra = tenure.interestSavedMinor - emi.interestSavedMinor
        "Cutting tenure saves ${formatMoneyCompact(extra)} more interest than cutting EMI.$sooner"
    } else {
        "Cutting EMI saves ${formatMoneyCompact(emi.interestSavedMinor - tenure.interestSavedMinor)} more interest here, " +
            "so keeping the tenure and lowering the monthly burden wins."
    }
    return PrepayComparison(emi, tenure, recommended, reason)
}

private fun remainingMonths(schedule: List<Installment>, paidInstallments: Int): Int =
    schedule.size - paidInstallments

// ---------------------------------------------------------------------------
// Overpayment simulation: recurring extra per month + one-time lump.
// This powers the simulator's viral slider.
// ---------------------------------------------------------------------------

/** Full result of simulating extra payments from the current position. */
data class OverpaymentSimulation(
    val strategy: PrepayStrategy,
    val baseEmiMinor: Long,
    /** Contractual EMI after the prepay (recomputed for REDUCE_EMI, unchanged for REDUCE_TENURE). */
    val newEmiMinor: Long,
    val basePayoffDate: LocalDate,
    val newPayoffDate: LocalDate,
    val monthsEarly: Int,
    val baseTotalInterestMinor: Long,
    val newTotalInterestMinor: Long,
    val interestSavedMinor: Long,
    val lumpMinor: Long,
    val extraPerMonthMinor: Long,
    val newSchedule: List<Installment>,
)

/**
 * Simulates, from [paidInstallments] onward: a one-time [lumpMinor] prepay
 * right now, plus [extraPerMonthMinor] added to every EMI after that.
 *
 * REDUCE_EMI: the contractual EMI is recomputed on the reduced balance over
 * the same remaining tenure, then the monthly extra is paid on top.
 * REDUCE_TENURE: the contractual EMI is untouched; the extra (and the lump)
 * go straight at the principal, so the loan ends earlier.
 *
 * Interest saved is old remaining interest minus new remaining interest.
 * A zero lump and zero extra is a valid no-op: everything comes back zero.
 */
fun simulateOverpayment(
    schedule: List<Installment>,
    annualRatePct: BigDecimal,
    lumpMinor: Long,
    extraPerMonthMinor: Long,
    strategy: PrepayStrategy,
    paidInstallments: Int = 0,
): OverpaymentSimulation {
    require(schedule.isNotEmpty()) { "schedule is empty" }
    require(lumpMinor >= 0) { "lump cannot be negative" }
    require(extraPerMonthMinor >= 0) { "extra per month cannot be negative" }
    require(paidInstallments in 0..schedule.size) { "paidInstallments out of range" }

    val principal = schedule[0].principalMinor + schedule[0].balanceAfterMinor
    val balance = if (paidInstallments == 0) principal else schedule[paidInstallments - 1].balanceAfterMinor
    require(balance > 0) { "loan is already paid off" }

    val remaining = schedule.drop(paidInstallments)
    val remainingCount = remaining.size
    val baseInterest = remaining.sumOf { it.interestMinor }
    val baseEmi = remaining.first().emiMinor
    val basePayoff = schedule.last().dueDate
    val firstDueDate = remaining.first().dueDate
    val r = monthlyRate(annualRatePct)

    val balanceAfterLump = balance - lumpMinor
    if (balanceAfterLump <= 0) {
        // The lump alone closes the loan right now.
        return OverpaymentSimulation(
            strategy = strategy,
            baseEmiMinor = baseEmi,
            newEmiMinor = 0L,
            basePayoffDate = basePayoff,
            newPayoffDate = firstDueDate,
            monthsEarly = remainingCount,
            baseTotalInterestMinor = baseInterest,
            newTotalInterestMinor = 0L,
            interestSavedMinor = baseInterest,
            lumpMinor = lumpMinor,
            extraPerMonthMinor = extraPerMonthMinor,
            newSchedule = emptyList(),
        )
    }

    val contractualEmi = when (strategy) {
        PrepayStrategy.REDUCE_EMI -> emiFor(balanceAfterLump, annualRatePct, remainingCount)
        PrepayStrategy.REDUCE_TENURE -> baseEmi
    }
    val monthlyPayment = contractualEmi + extraPerMonthMinor
    val firstInterest = roundedInterest(balanceAfterLump, r)
    require(monthlyPayment > firstInterest) {
        "monthly payment does not cover one month's interest; the loan would never pay off"
    }

    val newSchedule = ArrayList<Installment>()
    var bal = balanceAfterLump
    var date = firstDueDate
    var n = 0
    val cap = remainingCount + 1200
    while (bal > 0 && n < cap) {
        n++
        val interest = roundedInterest(bal, r)
        if (monthlyPayment >= bal + interest) {
            newSchedule.add(Installment(n, date, bal + interest, bal, interest, 0L))
            bal = 0L
        } else {
            val princ = monthlyPayment - interest
            bal -= princ
            newSchedule.add(Installment(n, date, monthlyPayment, princ, interest, bal))
            date = date.plusMonths(1)
        }
    }
    check(bal == 0L) { "simulation did not converge" }

    val newInterest = newSchedule.sumOf { it.interestMinor }
    val saved = baseInterest - newInterest
    return OverpaymentSimulation(
        strategy = strategy,
        baseEmiMinor = baseEmi,
        newEmiMinor = contractualEmi,
        basePayoffDate = basePayoff,
        newPayoffDate = newSchedule.last().dueDate,
        monthsEarly = (remainingCount - newSchedule.size).coerceAtLeast(0),
        baseTotalInterestMinor = baseInterest,
        newTotalInterestMinor = newInterest,
        interestSavedMinor = saved.coerceAtLeast(0),
        lumpMinor = lumpMinor,
        extraPerMonthMinor = extraPerMonthMinor,
        newSchedule = newSchedule,
    )
}

/** Both strategies for the same overpayment inputs, with a recommendation. */
data class OverpaymentComparison(
    val reduceTenure: OverpaymentSimulation,
    val reduceEmi: OverpaymentSimulation,
    val recommended: PrepayStrategy,
    val recommendationReason: String,
)

fun compareOverpaymentStrategies(
    schedule: List<Installment>,
    annualRatePct: BigDecimal,
    lumpMinor: Long,
    extraPerMonthMinor: Long,
    paidInstallments: Int = 0,
): OverpaymentComparison {
    val tenure = simulateOverpayment(schedule, annualRatePct, lumpMinor, extraPerMonthMinor, PrepayStrategy.REDUCE_TENURE, paidInstallments)
    val emi = simulateOverpayment(schedule, annualRatePct, lumpMinor, extraPerMonthMinor, PrepayStrategy.REDUCE_EMI, paidInstallments)
    val recommended = if (tenure.interestSavedMinor >= emi.interestSavedMinor) PrepayStrategy.REDUCE_TENURE else PrepayStrategy.REDUCE_EMI
    val monthsDiff = tenure.monthsEarly - emi.monthsEarly
    val sooner = if (monthsDiff > 0) " and finishes ${formatDuration(monthsDiff)} earlier" else ""
    val reason = if (recommended == PrepayStrategy.REDUCE_TENURE) {
        val extra = tenure.interestSavedMinor - emi.interestSavedMinor
        "Cutting tenure saves ${formatMoneyCompact(extra)} more interest than cutting EMI here$sooner. " +
            "This is also why banks defaulting you to EMI-cut costs you money."
    } else {
        "Here the EMI cut saves ${formatMoneyCompact(emi.interestSavedMinor - tenure.interestSavedMinor)} more interest, " +
            "so taking the lower monthly burden is the smarter move this time."
    }
    return OverpaymentComparison(tenure, emi, recommended, reason)
}

// ---------------------------------------------------------------------------
// Money formatting. INR default. Tabular numerals are a font concern;
// every formatted string here is digit-stable for tnum rendering.
// ---------------------------------------------------------------------------

private fun currencySymbol(currencyCode: String): String = when (currencyCode.uppercase()) {
    "INR" -> "\u20B9"
    "GBP" -> "\u00A3"
    "USD" -> "$"
    "EUR" -> "\u20AC"
    else -> "$currencyCode "
}

/** Full money string with minor units: 500000000 paise + INR -> Rs 50,00,000.00 style. */
fun formatMoney(minor: Long, currencyCode: String = "INR"): String {
    val locale = if (currencyCode.equals("INR", ignoreCase = true)) Locale("en", "IN") else Locale.UK
    val fmt = NumberFormat.getCurrencyInstance(locale)
    return try {
        fmt.currency = Currency.getInstance(currencyCode.uppercase())
        fmt.maximumFractionDigits = 2
        fmt.minimumFractionDigits = 2
        fmt.format(BigDecimal(minor).movePointLeft(2))
    } catch (_: IllegalArgumentException) {
        // Unknown currency code: fall back to code + grouped amount.
        val grouped = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
        "${currencyCode.uppercase()} ${grouped.format(BigDecimal(minor).movePointLeft(2))}"
    }
}

/** Compact, India-natural money: 50L, 1.25Cr, 5k. GBP: 1.2M, 45k. */
fun formatMoneyCompact(minor: Long, currencyCode: String = "INR"): String {
    val symbol = currencySymbol(currencyCode)
    val abs = kotlin.math.abs(minor)
    val sign = if (minor < 0) "-" else ""
    if (currencyCode.equals("INR", ignoreCase = true)) {
        val rupees = abs / 100.0
        return when {
            rupees >= 1_00_00_000 -> "$sign$symbol${trimZeros(rupees / 1_00_00_000)} Cr"
            rupees >= 1_00_000 -> "$sign$symbol${trimZeros(rupees / 1_00_000)} L"
            rupees >= 1_000 -> "$sign$symbol${trimZeros(rupees / 1_000)}k"
            else -> formatMoney(minor, currencyCode)
        }
    }
    val major = abs / 100.0
    return when {
        major >= 1_000_000 -> "$sign$symbol${trimZeros(major / 1_000_000)}M"
        major >= 1_000 -> "$sign$symbol${trimZeros(major / 1_000)}k"
        else -> formatMoney(minor, currencyCode)
    }
}

private fun trimZeros(v: Double): String {
    val s = "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    return s.ifEmpty { "0" }
}

/** 73 -> "6 years 1 month", 14 -> "1 year 2 months", 0 -> "0 months". */
fun formatDuration(totalMonths: Int): String {
    if (totalMonths <= 0) return "0 months"
    val y = totalMonths / 12
    val m = totalMonths % 12
    val yPart = if (y > 0) "$y year${if (y == 1) "" else "s"}" else null
    val mPart = if (m > 0) "$m month${if (m == 1) "" else "s"}" else null
    return listOfNotNull(yPart, mPart).joinToString(" ")
}

/** "8.5" -> "8.50%", BigDecimal-safe. */
fun formatRatePct(annualRatePct: BigDecimal): String =
    "${annualRatePct.setScale(2, RoundingMode.HALF_UP).toPlainString()}%"
