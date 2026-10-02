package com.kurupdevs.karz.ui.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurupdevs.karz.math.emiFor
import com.kurupdevs.karz.math.formatMoney
import com.kurupdevs.karz.math.formatMoneyCompact
import com.kurupdevs.karz.math.formatRatePct
import com.kurupdevs.karz.math.generateSchedule
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.ceil

// ---------------------------------------------------------------------------
// Pure analyzer. Zero Android dependencies. All money in minor units.
// ---------------------------------------------------------------------------

data class BtInput(
    val outstandingMinor: Long,
    val remainingMonths: Int,
    val currentRatePct: BigDecimal,
    val offeredRatePct: BigDecimal,
    val processingFeeMinor: Long,
    val currencyCode: String = "INR",
)

enum class BtVerdict { WORTH_EXPLORING, MARGINAL, PROBABLY_NOT_WORTH_IT }

data class BtAnalysis(
    val currentEmiMinor: Long,
    val newEmiMinor: Long,
    val monthlySavingMinor: Long,
    val currentRemainingInterestMinor: Long,
    val newTotalInterestMinor: Long,
    /** Interest saved minus the processing fee. The number that actually matters. */
    val netSavingMinor: Long,
    /** Months until the monthly savings have paid back the fee. Null if never. */
    val breakevenMonths: Int?,
    val rateDiffBps: Int,
    val ruleThresholdBps: Int,
    val ruleNote: String,
    val verdict: BtVerdict,
    val verdictNote: String,
    val trapResetTenureMonths: Int,
    val trapTotalInterestMinor: Long,
    /** How much MORE interest the tenure reset costs vs staying put. */
    val trapExtraCostMinor: Long,
)

/**
 * True-cost balance transfer analysis.
 *
 * Compares staying vs moving at the SAME remaining tenure, then separately
 * prices the tenure-reset trap: new lenders love resetting you to the max
 * tenure so the EMI looks smaller while total interest quietly rises.
 */
fun analyzeBalanceTransfer(input: BtInput, trapResetTenureMonths: Int = 240): BtAnalysis {
    require(input.outstandingMinor > 0) { "outstanding must be positive" }
    require(input.remainingMonths >= 6) { "remaining tenure must be at least 6 months" }
    require(input.processingFeeMinor >= 0) { "fee cannot be negative" }

    val today = LocalDate.now()
    val currentEmi = emiFor(input.outstandingMinor, input.currentRatePct, input.remainingMonths)
    val newEmi = emiFor(input.outstandingMinor, input.offeredRatePct, input.remainingMonths)
    val currentInterest = generateSchedule(input.outstandingMinor, input.currentRatePct, input.remainingMonths, today)
        .sumOf { it.interestMinor }
    val newInterest = generateSchedule(input.outstandingMinor, input.offeredRatePct, input.remainingMonths, today)
        .sumOf { it.interestMinor }
    val netSaving = currentInterest - newInterest - input.processingFeeMinor
    val monthlySaving = currentEmi - newEmi
    val breakeven = if (monthlySaving > 0) {
        ceil(input.processingFeeMinor.toDouble() / monthlySaving).toInt()
    } else null

    // Basis-point rules of thumb, scaled by time left.
    val yearsLeft = input.remainingMonths / 12.0
    val thresholdBps = when {
        yearsLeft < 5 -> 100
        yearsLeft < 10 -> 75
        yearsLeft < 15 -> 50
        else -> 25
    }
    val diffBps = input.currentRatePct.subtract(input.offeredRatePct)
        .multiply(BigDecimal(100))
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()

    val (verdict, verdictNote) = when {
        diffBps <= 0 -> BtVerdict.PROBABLY_NOT_WORTH_IT to
            "The offered rate is not lower than your current rate. There is nothing to gain here."
        netSaving <= 0 -> BtVerdict.PROBABLY_NOT_WORTH_IT to
            "After the processing fee, you would pay more in total, not less. The lower EMI is an illusion."
        diffBps >= thresholdBps -> BtVerdict.WORTH_EXPLORING to
            "The rate gap clears the rule of thumb and the fee pays for itself. Worth negotiating."
        diffBps * 2 >= thresholdBps -> BtVerdict.MARGINAL to
            "The gap is below the rule of thumb but not hopeless. Only worth it if the fee is small or negotiable."
        else -> BtVerdict.PROBABLY_NOT_WORTH_IT to
            "The rate gap is too small for the time you have left. The fee eats the saving."
    }
    val yearsText = if (yearsLeft == yearsLeft.toInt().toDouble()) {
        "${yearsLeft.toInt()}"
    } else {
        "%.1f".format(yearsLeft)
    }
    val ruleNote = "Rule of thumb: with $yearsText years left, look for at least $thresholdBps bps " +
        "(${thresholdBps / 100.0}%) lower. This offer gives you $diffBps bps."

    // The tenure-reset trap, priced explicitly.
    val trapTenure = trapResetTenureMonths.coerceAtLeast(input.remainingMonths)
    val trapInterest = generateSchedule(input.outstandingMinor, input.offeredRatePct, trapTenure, today)
        .sumOf { it.interestMinor }
    val trapExtra = trapInterest - currentInterest

    return BtAnalysis(
        currentEmiMinor = currentEmi,
        newEmiMinor = newEmi,
        monthlySavingMinor = monthlySaving,
        currentRemainingInterestMinor = currentInterest,
        newTotalInterestMinor = newInterest,
        netSavingMinor = netSaving,
        breakevenMonths = breakeven,
        rateDiffBps = diffBps,
        ruleThresholdBps = thresholdBps,
        ruleNote = ruleNote,
        verdict = verdict,
        verdictNote = verdictNote,
        trapResetTenureMonths = trapTenure,
        trapTotalInterestMinor = trapInterest,
        trapExtraCostMinor = trapExtra,
    )
}

/** Ready-made email asking the current lender for a rate reset instead of a transfer. */
fun rateResetEmailDraft(
    lenderName: String,
    accountMasked: String,
    currentRatePct: BigDecimal,
): Pair<String, String> {
    val subject = "Request for home loan rate reset"
    val body = buildString {
        appendLine("Dear Sir/Madam,")
        appendLine()
        appendLine("I hold a home loan with $lenderName (account $accountMasked) at ${formatRatePct(currentRatePct)}.")
        appendLine()
        appendLine("I see lower rates being offered now, and I would like to request a rate reset")
        appendLine("(conversion / repricing) on my existing loan instead of doing a balance transfer.")
        appendLine()
        appendLine("I understand a one-time conversion fee usually applies, typically Rs 1,500 to Rs 3,000,")
        appendLine("and I am fine paying it.")
        appendLine()
        appendLine("Please confirm in writing before processing:")
        appendLine("1. The revised rate I will get")
        appendLine("2. Whether my EMI or my tenure changes, and by how much")
        appendLine("3. The exact one-time fee")
        appendLine()
        appendLine("Regards,")
        appendLine("[Your name]")
        appendLine("[Phone number]")
    }.trimEnd()
    return subject to body
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

/**
 * Balance transfer / remortgage true-cost analyzer. Works standalone or
 * prefilled from a [SimulatorLoan].
 */
@Composable
fun BalanceTransferSection(loan: SimulatorLoan? = null) {
    val vm: BalanceTransferViewModel = viewModel(factory = BalanceTransferViewModel.factory(loan))
    LaunchedEffect(loan?.loanId) { vm.setLoan(loan) }
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var emailCopied by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "Balance transfer checker",
                subtitle = "True cost, not just the shiny lower EMI. Includes the tenure-reset trap lenders hope you miss.",
            )

            BtNumberField("Outstanding", state.outstandingText, vm::setOutstandingText, "e.g. 4500000")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BtNumberField(
                    "Months left", state.remainingText, vm::setRemainingText, "e.g. 180",
                    modifier = Modifier.weight(1f),
                )
                BtNumberField(
                    "Processing fee (Rs)", state.feeText, vm::setFeeText, "e.g. 10000",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BtDecimalField(
                    "Current rate %", state.currentRateText, vm::setCurrentRateText,
                    modifier = Modifier.weight(1f),
                )
                BtDecimalField(
                    "Offered rate %", state.offeredRateText, vm::setOfferedRateText,
                    modifier = Modifier.weight(1f),
                )
            }
            BtNumberField(
                "If they reset tenure to (months)",
                state.trapTenureText, vm::setTrapTenureText, "e.g. 240",
            )

            val analysis = state.analysis
            val currency = state.currencyCode
            if (analysis == null) {
                Text(
                    state.error ?: "Fill the fields above to see the true cost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SimColors.Secondary,
                )
                return@Column
            }

            // Verdict banner.
            val (bannerBg, bannerFg) = when (analysis.verdict) {
                BtVerdict.WORTH_EXPLORING -> SimColors.GreenTile to SimColors.Good
                BtVerdict.MARGINAL -> SimColors.LavenderTile to SimColors.Warn
                BtVerdict.PROBABLY_NOT_WORTH_IT -> SimColors.PinkTile to SimColors.Bad
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bannerBg)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when (analysis.verdict) {
                            BtVerdict.WORTH_EXPLORING -> "Worth exploring"
                            BtVerdict.MARGINAL -> "Marginal"
                            BtVerdict.PROBABLY_NOT_WORTH_IT -> "Probably not worth it"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = bannerFg,
                    )
                    Text(analysis.verdictNote, style = MaterialTheme.typography.bodySmall, color = SimColors.Headline)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Net saving after fee: ${formatMoney(analysis.netSavingMinor, currency)}",
                        style = MaterialTheme.typography.titleLarge.merge(TnumStyle)
                            .copy(fontWeight = FontWeight.ExtraBold),
                        color = SimColors.Headline,
                    )
                    Text(
                        buildString {
                            append("EMI ${formatMoneyCompact(analysis.currentEmiMinor, currency)} -> ")
                            append(formatMoneyCompact(analysis.newEmiMinor, currency))
                            append(" (saves ${formatMoneyCompact(analysis.monthlySavingMinor, currency)}/mo)")
                            val be = analysis.breakevenMonths
                            if (be != null) append(". Fee breaks even in $be months.")
                        },
                        style = MaterialTheme.typography.bodySmall.merge(TnumStyle),
                        color = SimColors.Secondary,
                    )
                }
            }

            Text(
                analysis.ruleNote,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = SimColors.Headline,
            )

            // Tenure-reset trap warning.
            if (analysis.trapExtraCostMinor > 0) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SimColors.PinkTile),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Tenure-reset trap",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = SimColors.Bad,
                        )
                        Text(
                            "If the new lender resets your tenure to ${analysis.trapResetTenureMonths} months, " +
                                "the EMI falls but you pay ${formatMoney(analysis.trapExtraCostMinor, currency)} MORE " +
                                "in total interest than staying where you are. " +
                                "Always compare at the SAME tenure first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SimColors.Headline,
                        )
                    }
                }
            }

            // Rate-reset path.
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SimColors.BlueTile)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Try this first: ask your own bank",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = SimColors.Headline,
                    )
                    Text(
                        "Most borrowers do not know this exists: your current lender can reset your rate " +
                            "for a one-time conversion fee of about Rs 1,500 to Rs 3,000. " +
                            "No paperwork marathon, no new lender. Send this email first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SimColors.Headline,
                    )
                    val (subject, body) = rateResetEmailDraft(
                        lenderName = loan?.lenderName ?: "[Your bank]",
                        accountMasked = "•••• 4821",
                        currentRatePct = state.currentRateDecimal,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString("Subject: $subject\n\n$body"))
                                emailCopied = true
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (emailCopied) "Copied" else "Copy email") }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, body)
                                }
                                context.startActivity(Intent.createChooser(intent, "Send rate reset request"))
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Share") }
                    }
                }
            }

            WorkingExpandable(
                rows = listOf(
                    "Outstanding compared" to formatMoney(state.outstandingMinor, currency),
                    "Current remaining interest" to formatMoney(analysis.currentRemainingInterestMinor, currency),
                    "New total interest (same tenure)" to formatMoney(analysis.newTotalInterestMinor, currency),
                    "Processing fee" to formatMoney(state.feeMinor, currency),
                    "Rate gap" to "${analysis.rateDiffBps} bps (rule needs ${analysis.ruleThresholdBps})",
                    "Tenure-reset total interest" to formatMoney(analysis.trapTotalInterestMinor, currency),
                ),
                formula = "net saving = old remaining interest − new remaining interest − fee. " +
                    "bps = (old rate − new rate) × 100. Breakeven = fee / monthly EMI saving.",
            )
        }
    }
}

@Composable
private fun BtNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = SimColors.Headline)
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(10)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text(placeholder, color = SimColors.Secondary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BtDecimalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = SimColors.Headline)
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                val cleaned = raw.filter { c -> c.isDigit() || c == '.' }
                if (cleaned.count { it == '.' } <= 1) onChange(cleaned.take(6))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = { Text("e.g. 8.5", color = SimColors.Secondary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
