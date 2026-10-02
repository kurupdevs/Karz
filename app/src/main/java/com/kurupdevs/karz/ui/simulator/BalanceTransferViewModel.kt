package com.kurupdevs.karz.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

data class BtUiState(
    val outstandingText: String = "",
    val remainingText: String = "",
    val currentRateText: String = "",
    val offeredRateText: String = "",
    val feeText: String = "",
    val trapTenureText: String = "240",
    val currencyCode: String = "INR",
    // Parsed values for the working panel.
    val outstandingMinor: Long = 0L,
    val feeMinor: Long = 0L,
    val currentRateDecimal: BigDecimal = BigDecimal.ZERO,
    val analysis: BtAnalysis? = null,
    val error: String? = null,
)

/** Plain StateFlow ViewModel for the balance transfer analyzer. No Android deps beyond ViewModel. */
class BalanceTransferViewModel : ViewModel() {

    private var loan: SimulatorLoan? = null
    private val _state = MutableStateFlow(BtUiState())
    val state: StateFlow<BtUiState> = _state.asStateFlow()

    fun setLoan(loan: SimulatorLoan?) {
        this.loan = loan
        if (loan == null) return
        _state.update {
            it.copy(
                outstandingText = (loan.outstandingMinor / 100).toString(),
                remainingText = loan.remainingMonths.toString(),
                currentRateText = loan.annualRatePct.stripTrailingZeros().toPlainString(),
                currencyCode = loan.currencyCode,
            )
        }
        recompute()
    }

    fun setOutstandingText(v: String) { _state.update { it.copy(outstandingText = v) }; recompute() }
    fun setRemainingText(v: String) { _state.update { it.copy(remainingText = v) }; recompute() }
    fun setCurrentRateText(v: String) { _state.update { it.copy(currentRateText = v) }; recompute() }
    fun setOfferedRateText(v: String) { _state.update { it.copy(offeredRateText = v) }; recompute() }
    fun setFeeText(v: String) { _state.update { it.copy(feeText = v) }; recompute() }
    fun setTrapTenureText(v: String) { _state.update { it.copy(trapTenureText = v) }; recompute() }

    private fun recompute() {
        val s = _state.value
        val outstandingMinor = s.outstandingText.toLongOrNull()?.times(100) ?: 0L
        val remaining = s.remainingText.toIntOrNull() ?: 0
        val currentRate = s.currentRateText.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val offeredRate = s.offeredRateText.toBigDecimalOrNull()
        val feeMinor = s.feeText.toLongOrNull()?.times(100) ?: 0L
        val trapTenure = s.trapTenureText.toIntOrNull() ?: 240

        if (offeredRate == null) {
            _state.update {
                it.copy(
                    outstandingMinor = outstandingMinor,
                    feeMinor = feeMinor,
                    currentRateDecimal = currentRate,
                    analysis = null,
                    error = "Enter the rate you are being offered to see the verdict.",
                )
            }
            return
        }
        try {
            val analysis = analyzeBalanceTransfer(
                BtInput(
                    outstandingMinor = outstandingMinor,
                    remainingMonths = remaining,
                    currentRatePct = currentRate,
                    offeredRatePct = offeredRate,
                    processingFeeMinor = feeMinor,
                    currencyCode = s.currencyCode,
                ),
                trapResetTenureMonths = trapTenure,
            )
            _state.update {
                it.copy(
                    outstandingMinor = outstandingMinor,
                    feeMinor = feeMinor,
                    currentRateDecimal = currentRate,
                    analysis = analysis,
                    error = null,
                )
            }
        } catch (t: IllegalArgumentException) {
            _state.update {
                it.copy(
                    outstandingMinor = outstandingMinor,
                    feeMinor = feeMinor,
                    currentRateDecimal = currentRate,
                    analysis = null,
                    error = t.message,
                )
            }
        }
    }

    companion object {
        fun factory(loan: SimulatorLoan?) = viewModelFactory {
            initializer { BalanceTransferViewModel().apply { setLoan(loan) } }
        }
    }
}
