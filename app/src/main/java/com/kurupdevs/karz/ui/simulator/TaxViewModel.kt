package com.kurupdevs.karz.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TaxUiState(
    val splits: List<FySplit> = emptyList(),
    val selectedFyYear: Int = 0,
    val regime: TaxRegime = TaxRegime.OLD,
    val slabPct: Double = 30.0,
    val sharePct: Double = 100.0,
    val incomeText: String = "",
    val estimate: TaxEstimate? = null,
    val regimeComparison: RegimeComparison? = null,
)

/** Plain StateFlow ViewModel for the 80C/24(b) module. No Android deps beyond ViewModel. */
class TaxViewModel : ViewModel() {

    private var loan: SimulatorLoan? = null
    private val _state = MutableStateFlow(TaxUiState())
    val state: StateFlow<TaxUiState> = _state.asStateFlow()

    fun setLoan(loan: SimulatorLoan) {
        this.loan = loan
        val splits = splitByFinancialYear(loan.schedule)
        _state.update {
            it.copy(
                splits = splits,
                selectedFyYear = splits.firstOrNull()?.fyStartYear ?: 0,
            )
        }
        recompute()
    }

    fun selectFy(year: Int) {
        _state.update { it.copy(selectedFyYear = year) }
        recompute()
    }

    fun setRegime(regime: TaxRegime) {
        _state.update { it.copy(regime = regime) }
        recompute()
    }

    fun setSlabPct(slab: Double) {
        _state.update { it.copy(slabPct = slab) }
        recompute()
    }

    fun setSharePct(share: Double) {
        _state.update { it.copy(sharePct = share.coerceIn(1.0, 100.0)) }
        recompute()
    }

    fun setIncomeText(text: String) {
        val digits = text.filter { it.isDigit() }.take(10)
        _state.update { it.copy(incomeText = digits) }
        recompute()
    }

    private fun recompute() {
        val s = _state.value
        val split = s.splits.firstOrNull { it.fyStartYear == s.selectedFyYear }
        val estimate = split?.let { estimateTaxSaved(it, s.slabPct, s.regime, s.sharePct) }
        val incomeMinor = s.incomeText.toLongOrNull()?.times(100)
        val comparison = if (incomeMinor != null && incomeMinor > 0 && split != null) {
            compareRegimes(incomeMinor, split.principalMinor, split.interestMinor, s.sharePct)
        } else null
        _state.update { it.copy(estimate = estimate, regimeComparison = comparison) }
    }

    companion object {
        fun factory(loan: SimulatorLoan) = viewModelFactory {
            initializer { TaxViewModel().apply { setLoan(loan) } }
        }
    }
}
