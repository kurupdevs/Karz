package com.kurupdevs.karz.ui.simulator

import com.kurupdevs.karz.math.Installment
import com.kurupdevs.karz.math.OverpaymentSimulation
import com.kurupdevs.karz.math.PrepayStrategy
import java.math.BigDecimal
import java.time.Instant

/**
 * Everything the simulator needs about a loan, in one handoff object.
 * The data layer maps the Firestore loan document to this; the simulator never
 * touches Firestore types directly, which keeps the math honest and the UI
 * testable with zero fakes.
 *
 * @param schedule the FULL amortization schedule from disbursement.
 * @param paidInstallments how many EMIs are already paid; the simulator
 * works on the remaining tail only.
 */
data class SimulatorLoan(
    val loanId: String,
    val lenderName: String,
    val currencyCode: String = "INR",
    val annualRatePct: BigDecimal,
    val schedule: List<Installment>,
    val paidInstallments: Int = 0,
) {
    /** Outstanding right now, in minor units. */
    val outstandingMinor: Long
        get() = when {
            schedule.isEmpty() -> 0L
            paidInstallments <= 0 -> schedule.first().principalMinor + schedule.first().balanceAfterMinor
            else -> schedule[(paidInstallments - 1).coerceAtMost(schedule.size - 1)].balanceAfterMinor
        }

    val remainingMonths: Int get() = (schedule.size - paidInstallments).coerceAtLeast(0)
    val currentEmiMinor: Long
        get() = if (paidInstallments < schedule.size) schedule[paidInstallments].emiMinor else 0L
    val isGbp: Boolean get() = currencyCode.equals("GBP", ignoreCase = true)
}

/**
 * Callback into the data layer (the data layer wires the Firestore implementation).
 * Apply takes the simulator's freshly generated schedule and persists it:
 * batched schedule rewrite + loan field updates + audit log entry.
 */
interface ScheduleApplier {
    suspend fun apply(input: ApplyScenarioInput)
}

data class ApplyScenarioInput(
    val loanId: String,
    val strategy: PrepayStrategy,
    val lumpMinor: Long,
    val extraPerMonthMinor: Long,
    val newEmiMinor: Long,
    val newSchedule: List<Installment>,
)

/** A saved simulation, mirrored 1:1 to users/{uid}/scenarios/{id} by the data layer. */
data class SavedScenario(
    val id: String,
    val loanId: String,
    val name: String,
    val lumpMinor: Long,
    val extraPerMonthMinor: Long,
    val strategy: PrepayStrategy,
    val monthsEarly: Int,
    val interestSavedMinor: Long,
    val newEmiMinor: Long,
    val savedAt: Instant,
)

/**
 * Persistence contract for saved scenarios. The data layer provides the Firestore
 * implementation; until then [InMemoryScenarioStore] keeps the UI fully
 * working for the session with real user data (nothing faked).
 */
interface ScenarioStore {
    suspend fun save(scenario: SavedScenario): SavedScenario
    suspend fun list(loanId: String): List<SavedScenario>
    suspend fun delete(loanId: String, scenarioId: String)
}

class InMemoryScenarioStore : ScenarioStore {
    private val items = mutableListOf<SavedScenario>()
    private var counter = 0L

    override suspend fun save(scenario: SavedScenario): SavedScenario {
        val withId = if (scenario.id.isBlank()) scenario.copy(id = "local-${++counter}") else scenario
        items.removeAll { it.id == withId.id }
        items.add(0, withId)
        return withId
    }

    override suspend fun list(loanId: String): List<SavedScenario> =
        items.filter { it.loanId == loanId }

    override suspend fun delete(loanId: String, scenarioId: String) {
        items.removeAll { it.loanId == loanId && it.id == scenarioId }
    }
}

// Factory for a scenario being saved (kept beside the data class).
fun savedScenarioFrom(
    loanId: String,
    lumpMinor: Long,
    extraPerMonthMinor: Long,
    result: OverpaymentSimulation,
    currencyCode: String,
): SavedScenario {
    val parts = mutableListOf<String>()
    if (extraPerMonthMinor > 0) parts.add(
        "${com.kurupdevs.karz.math.formatMoneyCompact(extraPerMonthMinor, currencyCode)}/mo",
    )
    if (lumpMinor > 0) parts.add(
        "${com.kurupdevs.karz.math.formatMoneyCompact(lumpMinor, currencyCode)} lump",
    )
    val strategyName = if (result.strategy == PrepayStrategy.REDUCE_TENURE) "Tenure cut" else "EMI cut"
    return SavedScenario(
        id = "",
        loanId = loanId,
        name = (parts.joinToString(" + ").ifBlank { "No extra" }) + " · $strategyName",
        lumpMinor = lumpMinor,
        extraPerMonthMinor = extraPerMonthMinor,
        strategy = result.strategy,
        monthsEarly = result.monthsEarly,
        interestSavedMinor = result.interestSavedMinor,
        newEmiMinor = result.newEmiMinor,
        savedAt = Instant.now(),
    )
}
