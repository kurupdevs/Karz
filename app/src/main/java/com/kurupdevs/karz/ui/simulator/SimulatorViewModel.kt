package com.kurupdevs.karz.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurupdevs.karz.math.OverpaymentComparison
import com.kurupdevs.karz.math.OverpaymentSimulation
import com.kurupdevs.karz.math.PrepayStrategy
import com.kurupdevs.karz.math.compareOverpaymentStrategies
import com.kurupdevs.karz.math.formatDuration
import com.kurupdevs.karz.math.formatMoney
import com.kurupdevs.karz.math.formatMoneyCompact
import com.kurupdevs.karz.math.formatRatePct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

data class SimulatorUiState(
    val loan: SimulatorLoan? = null,
    /** True when the loan has a remaining schedule to simulate against. */
    val loanValid: Boolean = false,
    val extraPerMonthMinor: Long = 0L,
    val lumpMinor: Long = 0L,
    val selectedStrategy: PrepayStrategy = PrepayStrategy.REDUCE_TENURE,
    val comparison: OverpaymentComparison? = null,
    /** The result for the selected strategy; null until there is something to simulate. */
    val selectedResult: OverpaymentSimulation? = null,
    val hasInputs: Boolean = false,
    val savedScenarios: List<SavedScenario> = emptyList(),
    val applying: Boolean = false,
    val message: String? = null,
    val canApply: Boolean = false,
)

/**
 * Plain StateFlow ViewModel for the prepayment simulator. All math is
 * synchronous and pure (microseconds for a 360-row schedule), so inputs
 * recompute immediately on every change. No Android dependencies beyond
 * ViewModel itself.
 */
class SimulatorViewModel(
    private val scheduleApplier: ScheduleApplier? = null,
    private val scenarioStore: ScenarioStore = InMemoryScenarioStore(),
) : ViewModel() {

    private val _state = MutableStateFlow(SimulatorUiState(canApply = scheduleApplier != null))
    val state: StateFlow<SimulatorUiState> = _state.asStateFlow()

    fun setLoan(loan: SimulatorLoan) {
        _state.update { it.copy(loan = loan, message = null) }
        refreshScenarios(loan.loanId)
        recompute()
    }

    fun setExtraPerMonth(minor: Long) {
        _state.update { it.copy(extraPerMonthMinor = minor.coerceAtLeast(0L), message = null) }
        recompute()
    }

    fun setLump(minor: Long) {
        val loan = _state.value.loan
        val cap = loan?.outstandingMinor ?: Long.MAX_VALUE
        _state.update { it.copy(lumpMinor = minor.coerceIn(0L, cap), message = null) }
        recompute()
    }

    fun selectStrategy(strategy: PrepayStrategy) {
        _state.update { it.copy(selectedStrategy = strategy) }
        recompute()
    }

    fun loadScenario(s: SavedScenario) {
        _state.update {
            it.copy(
                extraPerMonthMinor = s.extraPerMonthMinor,
                lumpMinor = s.lumpMinor,
                selectedStrategy = s.strategy,
                message = null,
            )
        }
        recompute()
    }

    fun deleteScenario(id: String) {
        val loanId = _state.value.loan?.loanId ?: return
        viewModelScope.launch {
            scenarioStore.delete(loanId, id)
            refreshScenarios(loanId)
        }
    }

    fun saveScenario() {
        val s = _state.value
        val loan = s.loan ?: return
        val result = s.selectedResult ?: return
        viewModelScope.launch {
            scenarioStore.save(
                savedScenarioFrom(
                    loanId = loan.loanId,
                    lumpMinor = s.lumpMinor,
                    extraPerMonthMinor = s.extraPerMonthMinor,
                    result = result,
                    currencyCode = loan.currencyCode,
                ),
            )
            refreshScenarios(loan.loanId)
            _state.update { it.copy(message = "Scenario saved.") }
        }
    }

    /** Persists the simulated schedule through the data layer. the data layer implements the write. */
    fun applyScenario() {
        val applier = scheduleApplier ?: return
        val s = _state.value
        val loan = s.loan ?: return
        val result = s.selectedResult ?: return
        if (s.applying) return
        viewModelScope.launch {
            _state.update { it.copy(applying = true, message = null) }
            try {
                applier.apply(
                    ApplyScenarioInput(
                        loanId = loan.loanId,
                        strategy = result.strategy,
                        lumpMinor = s.lumpMinor,
                        extraPerMonthMinor = s.extraPerMonthMinor,
                        newEmiMinor = result.newEmiMinor,
                        newSchedule = result.newSchedule,
                    ),
                )
                _state.update { it.copy(applying = false, message = "Applied. Your schedule is updated.") }
            } catch (t: Throwable) {
                _state.update { it.copy(applying = false, message = "Could not apply: ${t.message}") }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun refreshScenarios(loanId: String) {
        viewModelScope.launch {
            _state.update { it.copy(savedScenarios = scenarioStore.list(loanId)) }
        }
    }

    private fun recompute() {
        val s = _state.value
        val loan = s.loan
        if (loan == null || loan.remainingMonths <= 0 || loan.schedule.isEmpty()) {
            _state.update { it.copy(loanValid = false, comparison = null, selectedResult = null, hasInputs = false) }
            return
        }
        val comparison = compareOverpaymentStrategies(
            schedule = loan.schedule,
            annualRatePct = loan.annualRatePct,
            lumpMinor = s.lumpMinor,
            extraPerMonthMinor = s.extraPerMonthMinor,
            paidInstallments = loan.paidInstallments,
        )
        val selected = if (s.selectedStrategy == PrepayStrategy.REDUCE_TENURE) {
            comparison.reduceTenure
        } else {
            comparison.reduceEmi
        }
        _state.update {
            it.copy(
                loanValid = true,
                comparison = comparison,
                selectedResult = selected,
                hasInputs = s.lumpMinor > 0 || s.extraPerMonthMinor > 0,
            )
        }
    }

    /** Plain-text summary for the Sharesheet / copy actions. */
    fun shareText(): String {
        val s = _state.value
        val loan = s.loan ?: return ""
        val r = s.selectedResult ?: return ""
        val dateFmt = DateTimeFormatter.ofPattern("MMM yyyy")
        val strat = if (r.strategy == PrepayStrategy.REDUCE_TENURE) "tenure cut" else "EMI cut"
        val lines = mutableListOf(
            "My prepayment plan for ${loan.lenderName} ($strat)",
            "Extra: ${formatMoneyCompact(s.extraPerMonthMinor, loan.currencyCode)}/month" +
                if (s.lumpMinor > 0) " + ${formatMoneyCompact(s.lumpMinor, loan.currencyCode)} one-time" else "",
            "Finish ${formatDuration(r.monthsEarly)} early (${r.newPayoffDate.format(dateFmt)})",
            "Interest saved: ${formatMoney(r.interestSavedMinor, loan.currencyCode)}",
        )
        if (r.strategy == PrepayStrategy.REDUCE_EMI) {
            lines.add("New EMI: ${formatMoney(r.newEmiMinor, loan.currencyCode)} (was ${formatMoney(r.baseEmiMinor, loan.currencyCode)})")
        }
        lines.add("Rate ${formatRatePct(loan.annualRatePct)} on ${formatMoney(loan.outstandingMinor, loan.currencyCode)} outstanding.")
        lines.add("Numbers are estimates from my own entries. Confirm with your lender before acting.")
        return lines.joinToString("\n")
    }

    companion object {
        fun factory(
            scheduleApplier: ScheduleApplier? = null,
            scenarioStore: ScenarioStore = InMemoryScenarioStore(),
        ) = viewModelFactory {
            initializer { SimulatorViewModel(scheduleApplier, scenarioStore) }
        }
    }
}
