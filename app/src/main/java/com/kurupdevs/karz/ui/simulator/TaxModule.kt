package com.kurupdevs.karz.ui.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurupdevs.karz.math.Installment
import com.kurupdevs.karz.math.MATH_CONTEXT
import com.kurupdevs.karz.math.formatMoney
import com.kurupdevs.karz.math.formatMoneyCompact
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Pure tax math. Zero Android dependencies. All money in minor units.
// ---------------------------------------------------------------------------

/**
 * Tax constants for FY 2026-27, individuals below 60, self-occupied home.
 * RE-VERIFY EVERY BUDGET. Kept as plain constants (not Remote Config) so the
 * numbers on screen always match the code that computed them.
 */
private const val DEDUCTION_80C_CAP_MINOR = 15_000_000L // Rs 1,50,000 principal
private const val DEDUCTION_24B_CAP_MINOR = 20_000_000L // Rs 2,00,000 interest
private const val OLD_STD_DEDUCTION_MINOR = 5_000_000L // Rs 50,000
private const val NEW_STD_DEDUCTION_MINOR = 7_500_000L // Rs 75,000
private const val CESS_MULTIPLIER = "1.04" // 4% health and education cess

private data class Slab(val upToMinor: Long, val ratePct: Double)

private val OLD_SLABS = listOf(
    Slab(25_000_000L, 0.0), // 0 - 2.5L
    Slab(50_000_000L, 5.0), // 2.5L - 5L
    Slab(100_000_000L, 20.0), // 5L - 10L
    Slab(Long.MAX_VALUE, 30.0), // above 10L
)

private val NEW_SLABS = listOf(
    Slab(40_000_000L, 0.0), // 0 - 4L
    Slab(80_000_000L, 5.0), // 4L - 8L
    Slab(120_000_000L, 10.0), // 8L - 12L
    Slab(160_000_000L, 15.0), // 12L - 16L
    Slab(200_000_000L, 20.0), // 16L - 20L
    Slab(240_000_000L, 25.0), // 20L - 24L
    Slab(Long.MAX_VALUE, 30.0), // above 24L
)

enum class TaxRegime { OLD, NEW }

/** Indian financial year starts in April. Returns the starting calendar year. */
fun financialYearStart(date: LocalDate): Int =
    if (date.monthValue >= 4) date.year else date.year - 1

fun fyLabel(startYear: Int): String =
    "FY $startYear-${"%02d".format((startYear + 1) % 100)}"

data class FySplit(
    val fyStartYear: Int,
    val principalMinor: Long,
    val interestMinor: Long,
) {
    val label: String get() = fyLabel(fyStartYear)
}

/** Groups a schedule into April-March financial years with principal/interest totals. */
fun splitByFinancialYear(schedule: List<Installment>): List<FySplit> =
    schedule.groupBy { financialYearStart(it.dueDate) }
        .toSortedMap()
        .map { (year, insts) ->
            FySplit(year, insts.sumOf { it.principalMinor }, insts.sumOf { it.interestMinor })
        }

private fun marginalTax(taxableMinor: Long, slabs: List<Slab>): Long {
    var tax = BigDecimal.ZERO
    var prev = 0L
    var remaining = taxableMinor
    for (slab in slabs) {
        if (remaining <= 0) break
        val inSlab = minOf(remaining, slab.upToMinor - prev)
        tax = tax.add(
            BigDecimal(inSlab).multiply(BigDecimal(slab.ratePct.toString()), MATH_CONTEXT)
                .divide(BigDecimal(100), MATH_CONTEXT),
        )
        remaining -= inSlab
        prev = slab.upToMinor
    }
    // 4% cess, rounded to the nearest rupee like the income tax portal does.
    return tax.multiply(BigDecimal(CESS_MULTIPLIER), MATH_CONTEXT)
        .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .longValueExact()
}

/** Total income tax (with cess) on a taxable income under the old regime. */
fun incomeTaxOld(taxableMinor: Long): Long = marginalTax(taxableMinor.coerceAtLeast(0), OLD_SLABS)

/** Total income tax (with cess) on a taxable income under the new regime. */
fun incomeTaxNew(taxableMinor: Long): Long = marginalTax(taxableMinor.coerceAtLeast(0), NEW_SLABS)

data class TaxEstimate(
    val fyLabel: String,
    val regime: TaxRegime,
    val sharePct: Double,
    val principalMinor: Long,
    val interestMinor: Long,
    val deduction80CMinor: Long,
    val deduction24bMinor: Long,
    val slabPct: Double,
    val taxSavedMinor: Long,
    val note: String,
)

/**
 * Estimated tax saved from this loan in one FY at your marginal slab.
 * Caps: 80C principal at Rs 1.5L, 24(b) interest at Rs 2L (self-occupied),
 * applied AFTER your joint-borrower share split, since each co-borrower gets
 * their own caps.
 */
fun estimateTaxSaved(
    split: FySplit,
    slabPct: Double,
    regime: TaxRegime,
    sharePct: Double = 100.0,
): TaxEstimate {
    val share = (sharePct / 100.0).coerceIn(0.0, 1.0)
    val yourPrincipal = (split.principalMinor * share).toLong()
    val yourInterest = (split.interestMinor * share).toLong()
    if (regime == TaxRegime.NEW) {
        return TaxEstimate(
            fyLabel = split.label,
            regime = regime,
            sharePct = sharePct,
            principalMinor = yourPrincipal,
            interestMinor = yourInterest,
            deduction80CMinor = 0L,
            deduction24bMinor = 0L,
            slabPct = slabPct,
            taxSavedMinor = 0L,
            note = "The new regime does not allow 80C or 24(b) on a self-occupied home. " +
                "Use the comparator below: the old regime still wins for many borrowers because of this loan.",
        )
    }
    val d80c = minOf(yourPrincipal, DEDUCTION_80C_CAP_MINOR)
    val d24b = minOf(yourInterest, DEDUCTION_24B_CAP_MINOR)
    val saved = ((d80c + d24b) * slabPct / 100.0).toLong()
    return TaxEstimate(
        fyLabel = split.label,
        regime = regime,
        sharePct = sharePct,
        principalMinor = yourPrincipal,
        interestMinor = yourInterest,
        deduction80CMinor = d80c,
        deduction24bMinor = d24b,
        slabPct = slabPct,
        taxSavedMinor = saved,
        note = "Principal repaid counts under 80C up to Rs 1.5L a year. " +
            "Interest counts under 24(b) up to Rs 2L a year for a self-occupied home.",
    )
}

data class RegimeComparison(
    val oldTotalTaxMinor: Long,
    val newTotalTaxMinor: Long,
    val better: TaxRegime,
    val savingVsOtherMinor: Long,
)

/**
 * Old vs new regime on your annual income, with this FY's home-loan
 * deductions applied under old (and none under new). Excludes every other
 * deduction, so treat it as directional, not filing advice.
 */
fun compareRegimes(
    annualIncomeMinor: Long,
    fyPrincipalMinor: Long,
    fyInterestMinor: Long,
    sharePct: Double = 100.0,
): RegimeComparison {
    val share = (sharePct / 100.0).coerceIn(0.0, 1.0)
    val d80c = minOf((fyPrincipalMinor * share).toLong(), DEDUCTION_80C_CAP_MINOR)
    val d24b = minOf((fyInterestMinor * share).toLong(), DEDUCTION_24B_CAP_MINOR)
    val oldTaxable = (annualIncomeMinor - d80c - d24b - OLD_STD_DEDUCTION_MINOR).coerceAtLeast(0)
    val newTaxable = (annualIncomeMinor - NEW_STD_DEDUCTION_MINOR).coerceAtLeast(0)
    val oldTax = incomeTaxOld(oldTaxable)
    val newTax = incomeTaxNew(newTaxable)
    val better = if (oldTax <= newTax) TaxRegime.OLD else TaxRegime.NEW
    return RegimeComparison(
        oldTotalTaxMinor = oldTax,
        newTotalTaxMinor = newTax,
        better = better,
        savingVsOtherMinor = kotlin.math.abs(oldTax - newTax),
    )
}

/** Plain-text summary the user can paste to their CA or keep for ITR filing. */
fun taxSummaryForCa(
    estimate: TaxEstimate,
    lenderName: String,
): String = buildString {
    appendLine("Home loan tax summary (${estimate.fyLabel})")
    appendLine("Lender: $lenderName")
    appendLine("Regime: ${if (estimate.regime == TaxRegime.OLD) "Old" else "New"}")
    appendLine("Borrower share: ${estimate.sharePct.toInt()}%")
    appendLine("Principal repaid in FY: ${formatMoney(estimate.principalMinor)}")
    appendLine("Interest paid in FY: ${formatMoney(estimate.interestMinor)}")
    appendLine("80C deduction claimed: ${formatMoney(estimate.deduction80CMinor)} (cap Rs 1,50,000)")
    appendLine("24(b) deduction claimed: ${formatMoney(estimate.deduction24bMinor)} (cap Rs 2,00,000, self-occupied)")
    appendLine("Marginal slab: ${estimate.slabPct.toInt()}%")
    appendLine("Estimated tax saved: ${formatMoney(estimate.taxSavedMinor)}")
    appendLine()
    appendLine("Estimate only, from my own loan entries. Caps and slabs per FY 2026-27; confirm with your CA before filing.")
}.trimEnd()

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

/**
 * India tax module. Rendered only in INR mode; GBP mode hides it entirely
 * (the caller checks [SimulatorLoan.isGbp]).
 */
@Composable
fun TaxModuleSection(loan: SimulatorLoan) {
    if (loan.isGbp) return
    val vm: TaxViewModel = viewModel(factory = TaxViewModel.factory(loan))
    LaunchedEffect(loan.loanId) { vm.setLoan(loan) }
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "Tax: 80C + 24(b)",
                subtitle = "Your FY principal/interest split, and what it actually saves you at your slab.",
            )

            if (state.splits.isEmpty()) {
                Text("No schedule to split yet.", color = SimColors.Secondary)
                return@Column
            }

            // FY picker.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.splits) { split ->
                    val selected = split.fyStartYear == state.selectedFyYear
                    Text(
                        text = split.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) SimColors.CardWhite else SimColors.Headline,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) SimColors.PurpleSolid else SimColors.TrackBg)
                            .clickable { vm.selectFy(split.fyStartYear) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            val estimate = state.estimate
            if (estimate != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TaxSplitTile(
                        title = "Principal paid",
                        value = formatMoneyCompact(estimate.principalMinor),
                        modifier = Modifier.weight(1f),
                    )
                    TaxSplitTile(
                        title = "Interest paid",
                        value = formatMoneyCompact(estimate.interestMinor),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Joint borrower share.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Your share",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = SimColors.Headline,
                    )
                    Text(
                        "${estimate.sharePct.toInt()}%",
                        style = MaterialTheme.typography.titleMedium.merge(TnumStyle)
                            .copy(fontWeight = FontWeight.Bold),
                        color = SimColors.PurpleSolid,
                    )
                }
                Slider(
                    value = state.sharePct.toFloat(),
                    onValueChange = { vm.setSharePct(it.toDouble()) },
                    valueRange = 1f..100f,
                    steps = 98,
                    colors = SliderDefaults.colors(
                        thumbColor = SimColors.PurpleSolid,
                        activeTrackColor = SimColors.PurpleSolid,
                        inactiveTrackColor = SimColors.TrackBg,
                    ),
                )
                Text(
                    "Split this with a co-borrower. Each borrower gets their own Rs 1.5L / Rs 2L caps.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SimColors.Secondary,
                )

                // Regime toggle.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SimColors.TrackBg)
                        .padding(4.dp),
                ) {
                    RegimeOption("Old regime", state.regime == TaxRegime.OLD, { vm.setRegime(TaxRegime.OLD) }, Modifier.weight(1f))
                    RegimeOption("New regime", state.regime == TaxRegime.NEW, { vm.setRegime(TaxRegime.NEW) }, Modifier.weight(1f))
                }

                if (state.regime == TaxRegime.OLD) {
                    Text(
                        "Your marginal slab",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = SimColors.Headline,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(5.0, 10.0, 15.0, 20.0, 25.0, 30.0)) { slab ->
                            val selected = slab == state.slabPct
                            Text(
                                text = "${slab.toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) SimColors.CardWhite else SimColors.Headline,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) SimColors.PurpleSolid else SimColors.TrackBg)
                                    .clickable { vm.setSlabPct(slab) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // Result.
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SimColors.Navy),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "ESTIMATED TAX SAVED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = TextUnit.Unspecified,
                            ),
                            color = SimColors.CardWhite.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatMoney(estimate.taxSavedMinor),
                            style = MaterialTheme.typography.headlineMedium.merge(TnumStyle)
                                .copy(fontWeight = FontWeight.ExtraBold),
                            color = SimColors.CardWhite,
                        )
                        Text(
                            "80C: ${formatMoney(estimate.deduction80CMinor)}  ·  24(b): ${formatMoney(estimate.deduction24bMinor)}",
                            style = MaterialTheme.typography.labelSmall.merge(TnumStyle),
                            color = SimColors.CardWhite.copy(alpha = 0.75f),
                        )
                        Text(
                            estimate.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = SimColors.CardWhite.copy(alpha = 0.6f),
                        )
                    }
                }

                // Regime comparator.
                SectionHeader(
                    title = "Old vs new regime",
                    subtitle = "With this loan's deductions counted under old. Directional, not filing advice.",
                )
                OutlinedTextField(
                    value = state.incomeText,
                    onValueChange = vm::setIncomeText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Text("₹", color = SimColors.Secondary) },
                    placeholder = { Text("Annual income, e.g. 1800000", color = SimColors.Secondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                val cmp = state.regimeComparison
                if (cmp != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RegimeCard(
                            title = "Old regime",
                            tax = formatMoney(cmp.oldTotalTaxMinor),
                            isBetter = cmp.better == TaxRegime.OLD,
                            modifier = Modifier.weight(1f),
                        )
                        RegimeCard(
                            title = "New regime",
                            tax = formatMoney(cmp.newTotalTaxMinor),
                            isBetter = cmp.better == TaxRegime.NEW,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        if (cmp.better == TaxRegime.OLD) {
                            "Old regime wins by ${formatMoney(cmp.savingVsOtherMinor)}. This loan is a big reason why."
                        } else {
                            "New regime wins by ${formatMoney(cmp.savingVsOtherMinor)} even without the home-loan deductions."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = SimColors.Headline,
                    )
                }

                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(taxSummaryForCa(estimate, loan.lenderName)))
                        copied = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SimColors.PurpleSolid),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (copied) "Copied" else "Copy summary for CA") }

                WorkingExpandable(
                    rows = listOf(
                        "80C cap (principal)" to "Rs 1,50,000 / year",
                        "24(b) cap (interest)" to "Rs 2,00,000 / year, self-occupied",
                        "Slabs used" to "FY 2026-27, individuals below 60",
                        "Cess" to "4% on tax, rounded to the nearest rupee",
                        "Excluded" to "Every other deduction; this is directional",
                    ),
                    formula = "tax saved = (80C deduction + 24(b) deduction) × your marginal slab. " +
                        "Caps apply after splitting your joint-borrower share.",
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun TaxSplitTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.BlueTile),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = SimColors.Secondary)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.merge(TnumStyle)
                    .copy(fontWeight = FontWeight.Bold),
                color = SimColors.Headline,
            )
        }
    }
}

@Composable
private fun RegimeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        ),
        color = if (selected) SimColors.Headline else SimColors.Secondary,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SimColors.CardWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RegimeCard(title: String, tax: String, isBetter: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBetter) SimColors.GreenTile else SimColors.TrackBg,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = SimColors.Secondary)
            Text(
                tax,
                style = MaterialTheme.typography.titleMedium.merge(TnumStyle)
                    .copy(fontWeight = FontWeight.Bold),
                color = SimColors.Headline,
            )
            if (isBetter) {
                Text(
                    "Better for you",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SimColors.Good,
                )
            }
        }
    }
}
