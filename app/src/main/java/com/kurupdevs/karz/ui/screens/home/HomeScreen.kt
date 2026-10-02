package com.kurupdevs.karz.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.data.model.LoanStatus
import com.kurupdevs.karz.data.model.ltv
import com.kurupdevs.karz.ui.components.ExplainerText
import com.kurupdevs.karz.ui.components.LtvRing
import com.kurupdevs.karz.ui.components.MortgageCard
import com.kurupdevs.karz.ui.components.PillButton
import com.kurupdevs.karz.ui.motion.Motion
import com.kurupdevs.karz.ui.screens.common.ChoreoScope
import com.kurupdevs.karz.ui.screens.common.HouseGlyph
import com.kurupdevs.karz.ui.screens.common.LineChart
import com.kurupdevs.karz.ui.screens.common.mortgageCardSharedModifier
import com.kurupdevs.karz.ui.screens.common.rememberMinorFormatter
import com.kurupdevs.karz.ui.screens.data.LoanRepository
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PastelLavender
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.TextSecondary
import com.kurupdevs.karz.ui.theme.money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * S2 Home. Hero white card with animated LTV ring + band explainer,
 * purple mortgage card (shared-element morph into S4), total-monthly row.
 * The bottom pill nav is owned by the TabScaffold; this screen only
 * renders content. Entrance choreography: one enter flag, 75ms stagger
 * capped 375ms, 400ms easeOutQuint, no re-run on tab switch-back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    loans: LoanRepository,
    currency: String,
    onEditLoan: (Loan) -> Unit,
    onLoanClick: (Loan) -> Unit,
    onManage: () -> Unit,
    onAddLoan: () -> Unit
) {
    val allLoans by loans.getLoans().collectAsStateWithLifecycle(initialValue = emptyList())
    val active = allLoans.filter { it.status == LoanStatus.ACTIVE }
    val loan = active.firstOrNull()
    val formatMinor = rememberMinorFormatter(currency)
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var choreoToken by remember { mutableStateOf(0) }
    var loanToDelete by remember { mutableStateOf<Loan?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    loanToDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { loanToDelete = null },
            title = { Text("Delete this loan?", style = MortgageTypography.titleLarge) },
            text = { Text("It will be archived. Your payment history stays on your account.", style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    loanToDelete = null
                    scope.launch { loans.archiveLoan(doomed.id) }
                }) { Text("Delete", color = Color(0xFFD33F3F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { loanToDelete = null }) { Text("Keep it") } }
        )
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                delay(700)
                choreoToken += 1 // replays entrance at reduced stagger
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = refreshing,
                containerColor = CardWhite,
                color = PurpleSolid,
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(AppBg)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (loan == null) {
                EmptyHome(onAddLoan)
            } else {
                ChoreoScope(itemCount = 3, screenToken = choreoToken, heroIndex = 0) { index, enterMod ->
                    when (index) {
                        0 -> LtvHeroCard(loan, formatMinor, loans, Modifier.then(enterMod))
                        1 -> Box(
                            Modifier
                                .then(enterMod)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onLoanClick(loan) }
                        ) {
                            MortgageCard(
                                lenderName = loan.lenderName,
                                lenderMarkText = loan.lenderName.trim().firstOrNull()?.uppercase() ?: "?",
                                balanceText = formatMinor(loan.currentBalanceMinor),
                                lastReportedText = "today",
                                monthlyRepaymentText = formatMinor(loan.emiAmountMinor),
                                yourShareText = formatMinor(loan.emiAmountMinor),
                                onEdit = { onEditLoan(loan) },
                                onDelete = { loanToDelete = loan },
                                modifier = mortgageCardSharedModifier(loan.id)
                            )
                        }
                        2 -> TotalMonthlyRow(
                            totalMinor = active.sumOf { it.emiAmountMinor },
                            formatMinor = formatMinor,
                            onConfirm = onManage,
                            modifier = enterMod
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyHome(onAddLoan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(MortgageRadii.CardShape)
            .background(CardWhite)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HouseGlyph(Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("No loans yet", style = MortgageTypography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your first loan and we will break down every payment, show your LTV, and find what extra payments save you.",
            style = MortgageTypography.bodyMedium.copy(textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(18.dp))
        PillButton("Add my loan", onClick = onAddLoan)
    }
}

@Composable
private fun LtvHeroCard(
    loan: Loan,
    formatMinor: (Long) -> String,
    loans: LoanRepository,
    modifier: Modifier = Modifier
) {
    val ltv = loan.ltv
    var showHistory by remember { mutableStateOf(false) }
    val schedule by loans.getSchedule(loan.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier
            .fillMaxWidth()
            .clip(MortgageRadii.CardShape)
            .background(CardWhite)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Home value", style = MortgageTypography.labelLarge)
                if (loan.homeValueMinor != null) {
                    Text(formatMinor(loan.homeValueMinor), style = MortgageTypography.headlineLarge.money())
                } else {
                    Text("Not set", style = MortgageTypography.headlineLarge.copy(color = TextSecondary))
                }
            }
            HouseGlyph(Modifier.size(56.dp), tint = PastelLavender)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LtvRing(ltv = (ltv ?: 0.0).toFloat(), diameter = 132.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                if (ltv != null) {
                    Text(
                        "${(ltv * 100).roundToInt()}% loan to value (LTV)",
                        style = MortgageTypography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    ExplainerText(ltvBandCopy(ltv))
                } else {
                    Text(
                        "Add your home value to unlock your LTV ring.",
                        style = MortgageTypography.bodyMedium
                    )
                }
            }
        }
        if (ltv != null && schedule.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (showHistory) "Hide progress" else "See progress over time",
                style = MortgageTypography.labelLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = PurpleSolid
                ),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showHistory = !showHistory }
            )
            AnimatedVisibility(
                visible = showHistory,
                enter = expandVertically(animationSpec = tween(300, easing = Motion.EaseInOut)) +
                    fadeIn(tween(300)),
                exit = shrinkVertically(animationSpec = tween(300, easing = Motion.EaseInOut)) +
                    fadeOut(tween(300))
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    val home = loan.homeValueMinor!!.toFloat()
                    val points = schedule
                        .filterIndexed { i, _ -> i % 6 == 0 || i == schedule.lastIndex }
                        .map { (it.balanceAfterMinor / home * 100f).coerceIn(0f, 100f) }
                    LineChart(points = points, modifier = Modifier.fillMaxWidth().height(120.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start", style = MortgageTypography.labelMedium)
                        Text("LTV over time", style = MortgageTypography.labelMedium)
                        Text("Paid off", style = MortgageTypography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun ltvBandCopy(ltv: Double): String = when {
    ltv <= 0.60 -> "Strong equity. You own most of your home outright."
    ltv <= 0.75 -> "Healthy. Extra payments in this band cut your interest the fastest."
    ltv <= 0.90 -> "High leverage. Every extra payment here buys you real breathing room."
    else -> "Thin equity. Small extra payments still move the needle."
}

@Composable
private fun TotalMonthlyRow(
    totalMinor: Long,
    formatMinor: (Long) -> String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MortgageRadii.CardShape)
            .background(CardWhite)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Total monthly", style = MortgageTypography.labelLarge)
            Text(formatMinor(totalMinor), style = MortgageTypography.displayLarge.money())
        }
        PillButton(text = "Confirm", onClick = onConfirm)
    }
}
