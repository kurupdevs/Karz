package com.kurupdevs.karz.ui.simulator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurupdevs.karz.math.OverpaymentSimulation
import com.kurupdevs.karz.math.PrepayStrategy
import com.kurupdevs.karz.math.formatDuration
import com.kurupdevs.karz.math.formatMoney
import com.kurupdevs.karz.math.formatMoneyCompact
import com.kurupdevs.karz.math.formatRatePct
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The prepayment simulator: the app's viral screen.
 *
 * Extra-per-month slider + one-time lump input + tenure/EMI strategy toggle,
 * animated hero reveal of "Y early + ₹Z saved", both strategies shown side by
 * side with a recommendation, save/apply/share, and show-the-working on every
 * number. Every figure is computed live from the loan passed in. No fake data.
 */
@Composable
fun SimulatorScreen(
    loan: SimulatorLoan,
    scheduleApplier: ScheduleApplier? = null,
    scenarioStore: ScenarioStore = remember { InMemoryScenarioStore() },
    onBack: () -> Unit = {},
) {
    val vm: SimulatorViewModel = viewModel(factory = SimulatorViewModel.factory(scheduleApplier, scenarioStore))
    LaunchedEffect(loan.loanId) { vm.setLoan(loan) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showApplyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SimColors.AppBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top bar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ Back",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SimColors.PurpleSolid,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp),
            )
        }
        Text(
            text = "Prepayment simulator",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = SimColors.Headline,
        )
        Text(
            text = "See exactly what extra payments do to your loan. Every number shows its working.",
            style = MaterialTheme.typography.bodyMedium,
            color = SimColors.Secondary,
        )

        val currentLoan = state.loan
        if (currentLoan == null || !state.loanValid) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
            ) {
                Text(
                    text = "No active loan to simulate against yet. Add your loan first, then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SimColors.Secondary,
                    modifier = Modifier.padding(18.dp),
                )
            }
            return@Column
        }

        LoanSummaryCard(currentLoan)

        // Inputs.
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(
                    title = "Your extra payment",
                    subtitle = "Slide, type, and watch the numbers move. Nothing is charged or changed until you hit Apply.",
                )
                Spacer(Modifier.height(6.dp))
                ExtraSlider(
                    extraMinor = state.extraPerMonthMinor,
                    emiMinor = currentLoan.currentEmiMinor,
                    currencyCode = currentLoan.currencyCode,
                    onChange = vm::setExtraPerMonth,
                )
                Spacer(Modifier.height(10.dp))
                LumpInput(
                    lumpMinor = state.lumpMinor,
                    currencyCode = currentLoan.currencyCode,
                    onChange = vm::setLump,
                )
            }
        }

        StrategyToggle(
            selected = state.selectedStrategy,
            onSelect = vm::selectStrategy,
        )

        val comparison = state.comparison
        val selected = state.selectedResult
        if (state.hasInputs && comparison != null && selected != null) {
            HeroResultCard(
                loan = currentLoan,
                result = selected,
                isRecommended = state.selectedStrategy == comparison.recommended,
                extraMinor = state.extraPerMonthMinor,
                lumpMinor = state.lumpMinor,
            )

            // Both strategies side by side, per research rule 4.
            SectionHeader(title = "Both options, side by side")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StrategyMiniCard(
                    result = comparison.reduceTenure,
                    loan = currentLoan,
                    isSelected = state.selectedStrategy == PrepayStrategy.REDUCE_TENURE,
                    isRecommended = comparison.recommended == PrepayStrategy.REDUCE_TENURE,
                    onClick = { vm.selectStrategy(PrepayStrategy.REDUCE_TENURE) },
                    modifier = Modifier.weight(1f),
                )
                StrategyMiniCard(
                    result = comparison.reduceEmi,
                    loan = currentLoan,
                    isSelected = state.selectedStrategy == PrepayStrategy.REDUCE_EMI,
                    isRecommended = comparison.recommended == PrepayStrategy.REDUCE_EMI,
                    onClick = { vm.selectStrategy(PrepayStrategy.REDUCE_EMI) },
                    modifier = Modifier.weight(1f),
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SimColors.GreenTile),
            ) {
                Text(
                    text = comparison.recommendationReason,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = SimColors.Headline,
                    modifier = Modifier.padding(14.dp),
                )
            }

            WorkingExpandable(
                rows = workingRows(currentLoan, selected, state.extraPerMonthMinor, state.lumpMinor),
                formula = "Each month: interest = round(balance × r), where r = ${formatRatePct(currentLoan.annualRatePct)} / 1200. " +
                    "Principal = payment − interest. The last EMI absorbs the rounding residue so the balance lands exactly on zero.",
            )

            // Actions.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = vm::saveScenario,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        ShareCardRenderer.share(
                            context, currentLoan, state.extraPerMonthMinor,
                            state.lumpMinor, selected, vm.shareText(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Share") }
                if (state.canApply) {
                    Button(
                        onClick = { showApplyDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SimColors.PurpleSolid),
                    ) { Text("Apply") }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SimColors.LavenderTile),
            ) {
                Text(
                    text = "Move the slider or add a one-time amount above to see your finish date and interest saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SimColors.Headline,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }

        // Saved scenarios.
        if (state.savedScenarios.isNotEmpty()) {
            SectionHeader(title = "Saved scenarios")
            state.savedScenarios.forEach { s ->
                SavedScenarioRow(
                    scenario = s,
                    currencyCode = currentLoan.currencyCode,
                    onLoad = { vm.loadScenario(s) },
                    onDelete = { vm.deleteScenario(s.id) },
                )
            }
        }

        state.message?.let { msg ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SimColors.Navy),
                onClick = vm::clearMessage,
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showApplyDialog) {
        val s = state.selectedResult
        val l = state.loan
        if (s != null && l != null) {
            AlertDialog(
                onDismissRequest = { showApplyDialog = false },
                title = { Text("Apply this plan?") },
                text = {
                    Text(applySummaryText(l, s, state.extraPerMonthMinor, state.lumpMinor))
                },
                confirmButton = {
                    Button(
                        onClick = { showApplyDialog = false; vm.applyScenario() },
                        colors = ButtonDefaults.buttonColors(containerColor = SimColors.PurpleSolid),
                    ) { Text(if (state.applying) "Applying…" else "Apply") }
                },
                dismissButton = {
                    TextButton(onClick = { showApplyDialog = false }) { Text("Cancel") }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun LoanSummaryCard(loan: SimulatorLoan) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = loan.lenderName,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SimColors.Secondary,
            )
            Text(
                text = formatMoneyCompact(loan.outstandingMinor, loan.currencyCode),
                style = MaterialTheme.typography.headlineMedium.merge(TnumStyle)
                    .copy(fontWeight = FontWeight.ExtraBold),
                color = SimColors.Headline,
            )
            Text(
                text = "outstanding",
                style = MaterialTheme.typography.labelSmall,
                color = SimColors.Secondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryChip("EMI ${formatMoneyCompact(loan.currentEmiMinor, loan.currencyCode)}")
                SummaryChip("Rate ${formatRatePct(loan.annualRatePct)}")
                SummaryChip("${loan.remainingMonths} mo left")
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.merge(TnumStyle).copy(fontWeight = FontWeight.Medium),
        color = SimColors.Headline,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(SimColors.TrackBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ExtraSlider(
    extraMinor: Long,
    emiMinor: Long,
    currencyCode: String,
    onChange: (Long) -> Unit,
) {
    val isInr = currencyCode.equals("INR", ignoreCase = true)
    val stepRupees = if (isInr) 500 else 10
    val emiRupees = (emiMinor / 100).toInt().coerceAtLeast(1)
    val maxRupees = maxOf(emiRupees * 3, if (isInr) 25000 else 500).toFloat()
    val steps = ((maxRupees / stepRupees).toInt() - 1).coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Extra per month",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = SimColors.Headline,
        )
        Text(
            text = formatMoneyCompact(extraMinor, currencyCode),
            style = MaterialTheme.typography.titleLarge.merge(TnumStyle).copy(fontWeight = FontWeight.Bold),
            color = SimColors.PurpleSolid,
        )
    }
    Slider(
        value = (extraMinor / 100).toFloat().coerceIn(0f, maxRupees),
        onValueChange = { onChange((it.roundToInt() * 100).toLong()) },
        valueRange = 0f..maxRupees,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = SimColors.PurpleSolid,
            activeTrackColor = SimColors.PurpleSolid,
            inactiveTrackColor = SimColors.TrackBg,
        ),
    )
    Text(
        text = "On top of your regular EMI, every month.",
        style = MaterialTheme.typography.labelSmall,
        color = SimColors.Secondary,
    )
}

@Composable
private fun LumpInput(
    lumpMinor: Long,
    currencyCode: String,
    onChange: (Long) -> Unit,
) {
    val symbol = if (currencyCode.equals("INR", ignoreCase = true)) "₹" else "£"
    val text = if (lumpMinor == 0L) "" else (lumpMinor / 100).toString()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "One-time prepayment",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = SimColors.Headline,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(10)
                onChange(if (digits.isEmpty()) 0L else digits.toLong() * 100)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Text(symbol, color = SimColors.Secondary) },
            placeholder = { Text("e.g. 100000", color = SimColors.Secondary) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "A lump sum right now, from savings or a bonus.",
            style = MaterialTheme.typography.labelSmall,
            color = SimColors.Secondary,
        )
    }
}

/** Hero reveal: big count-up numbers with a springy entrance. */
@Composable
private fun HeroResultCard(
    loan: SimulatorLoan,
    result: OverpaymentSimulation,
    isRecommended: Boolean,
    extraMinor: Long,
    lumpMinor: Long,
) {
    val animatedSaved by animateFloatAsState(
        targetValue = result.interestSavedMinor.toFloat(),
        animationSpec = tween(700),
        label = "saved",
    )
    val animatedMonths by animateFloatAsState(
        targetValue = result.monthsEarly.toFloat(),
        animationSpec = tween(700),
        label = "months",
    )
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM yyyy") }
    val strategyName = if (result.strategy == PrepayStrategy.REDUCE_TENURE) "Reduce tenure" else "Reduce EMI"

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        ),
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SimColors.Navy),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C6CF5), Color(0xFF4A3FD1)),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f, 1000f),
                        ),
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strategyName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    if (isRecommended) {
                        Text(
                            text = "RECOMMENDED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = SimColors.Navy,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                HeroNumber(
                    label = "You finish early by",
                    value = formatDuration(animatedMonths.roundToInt()),
                    accent = Color.White,
                )
                Text(
                    text = "new payoff ${result.newPayoffDate.format(dateFmt)} (was ${result.basePayoffDate.format(dateFmt)})",
                    style = MaterialTheme.typography.bodySmall.merge(TnumStyle),
                    color = Color.White.copy(alpha = 0.75f),
                )
                HeroNumber(
                    label = "Interest you save",
                    value = formatMoneyCompact(animatedSaved.toLong(), loan.currencyCode),
                    accent = Color.White,
                )
                Text(
                    text = formatMoney(result.interestSavedMinor, loan.currencyCode),
                    style = MaterialTheme.typography.bodyMedium.merge(TnumStyle)
                        .copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.85f),
                )
                if (result.strategy == PrepayStrategy.REDUCE_EMI) {
                    Text(
                        text = "New EMI ${formatMoney(result.newEmiMinor, loan.currencyCode)} " +
                            "(was ${formatMoney(result.baseEmiMinor, loan.currencyCode)})",
                        style = MaterialTheme.typography.bodySmall.merge(TnumStyle),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
                Text(
                    text = describeInputs(extraMinor, lumpMinor, loan.currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun describeInputs(extraMinor: Long, lumpMinor: Long, currencyCode: String): String {
    val parts = mutableListOf<String>()
    if (extraMinor > 0) parts.add("${formatMoneyCompact(extraMinor, currencyCode)} extra every month")
    if (lumpMinor > 0) parts.add("${formatMoneyCompact(lumpMinor, currencyCode)} one-time now")
    return "Based on " + parts.joinToString(" + ") + "."
}

@Composable
private fun StrategyMiniCard(
    result: OverpaymentSimulation,
    loan: SimulatorLoan,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (result.strategy == PrepayStrategy.REDUCE_TENURE) "Reduce tenure" else "Reduce EMI"
    val headline = if (result.strategy == PrepayStrategy.REDUCE_TENURE) {
        formatDuration(result.monthsEarly) + " early"
    } else {
        formatMoneyCompact(result.newEmiMinor, loan.currencyCode) + "/mo"
    }
    Card(
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) SimColors.PurpleSolid else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = SimColors.Headline,
                )
                if (isRecommended) {
                    Text(
                        text = "★",
                        color = SimColors.PurpleSolid,
                        fontSize = 14.sp,
                    )
                }
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.merge(TnumStyle)
                    .copy(fontWeight = FontWeight.ExtraBold),
                color = SimColors.PurpleSolid,
            )
            Text(
                text = "Saves ${formatMoneyCompact(result.interestSavedMinor, loan.currencyCode)}",
                style = MaterialTheme.typography.labelSmall.merge(TnumStyle),
                color = SimColors.Secondary,
            )
            if (isSelected) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SimColors.Good,
                )
            }
        }
    }
}

private fun workingRows(
    loan: SimulatorLoan,
    result: OverpaymentSimulation,
    extraMinor: Long,
    lumpMinor: Long,
): List<Pair<String, String>> {
    val dateFmt = DateTimeFormatter.ofPattern("MMM yyyy")
    return listOf(
        "Outstanding considered" to formatMoney(loan.outstandingMinor, loan.currencyCode),
        "Annual rate" to formatRatePct(loan.annualRatePct),
        "Current EMI" to formatMoney(result.baseEmiMinor, loan.currencyCode),
        "Extra per month" to formatMoney(extraMinor, loan.currencyCode),
        "One-time prepayment" to formatMoney(lumpMinor, loan.currencyCode),
        "Strategy" to if (result.strategy == PrepayStrategy.REDUCE_TENURE) "Reduce tenure (EMI unchanged)" else "Reduce EMI (tenure unchanged)",
        "Payoff without prepay" to result.basePayoffDate.format(dateFmt),
        "Payoff with prepay" to result.newPayoffDate.format(dateFmt),
        "Interest without prepay" to formatMoney(result.baseTotalInterestMinor, loan.currencyCode),
        "Interest with prepay" to formatMoney(result.newTotalInterestMinor, loan.currencyCode),
    )
}

private fun applySummaryText(
    loan: SimulatorLoan,
    result: OverpaymentSimulation,
    extraMinor: Long,
    lumpMinor: Long,
): String {
    val dateFmt = DateTimeFormatter.ofPattern("MMM yyyy")
    val strat = if (result.strategy == PrepayStrategy.REDUCE_TENURE) "tenure cut" else "EMI cut"
    return buildString {
        append("This rewrites your schedule with a $strat: ")
        if (extraMinor > 0) append("${formatMoney(extraMinor, loan.currencyCode)}/month extra. ")
        if (lumpMinor > 0) append("${formatMoney(lumpMinor, loan.currencyCode)} one-time prepay. ")
        append("New payoff ${result.newPayoffDate.format(dateFmt)}, ")
        append("interest saved ${formatMoney(result.interestSavedMinor, loan.currencyCode)}. ")
        append("This is recorded in your audit log and cannot be silently undone.")
    }
}

@Composable
private fun SavedScenarioRow(
    scenario: SavedScenario,
    currencyCode: String,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SimColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = SimColors.Headline,
                )
                Text(
                    text = "${formatDuration(scenario.monthsEarly)} early · saves " +
                        formatMoneyCompact(scenario.interestSavedMinor, currencyCode),
                    style = MaterialTheme.typography.labelSmall.merge(TnumStyle),
                    color = SimColors.Secondary,
                )
            }
            Text(
                text = "Load",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = SimColors.PurpleSolid,
                modifier = Modifier.clickable(onClick = onLoad).padding(8.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "×",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SimColors.Secondary,
                modifier = Modifier.clickable(onClick = onDelete).padding(8.dp),
            )
        }
    }
}
