package com.kurupdevs.karz.ui.screens.loandetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.ui.components.ExplainerText
import com.kurupdevs.karz.ui.components.MortgageCard
import com.kurupdevs.karz.ui.components.SectionHeader
import com.kurupdevs.karz.ui.components.ShimmerBox
import com.kurupdevs.karz.ui.screens.common.LineChart
import com.kurupdevs.karz.ui.screens.common.mortgageCardSharedModifier
import com.kurupdevs.karz.ui.screens.common.rememberMinorFormatter
import com.kurupdevs.karz.ui.screens.data.LoanRepository
import com.kurupdevs.karz.ui.screens.data.ScheduleRow
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.TextHeadline
import com.kurupdevs.karz.ui.theme.money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * S4 loan detail: shared-element expansion of the purple mortgage card
 * (key "mortgage-card-$loanId"), balance chart, full amortization schedule
 * (lazy, keyed), Edit details, Delete (soft: status=ARCHIVED).
 */
@Composable
fun LoanDetailScreen(
    loanId: String,
    loans: LoanRepository,
    currency: String,
    onBack: () -> Unit,
    onEdit: (Loan) -> Unit,
    onDeleted: () -> Unit
) {
    val loan by loans.getLoan(loanId).collectAsStateWithLifecycle(initialValue = null)
    val schedule by loans.getSchedule(loanId).collectAsStateWithLifecycle(initialValue = emptyList())
    val formatMinor = rememberMinorFormatter(currency)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yyyy") }

    val current = loan
    if (showDeleteDialog && current != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this loan?", style = MortgageTypography.titleLarge) },
            text = { Text("It will be archived. Your payment history stays on your account.", style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        loans.archiveLoan(current.id)
                            .onSuccess { onDeleted() }
                            .onFailure { snackbar.showSnackbar("Could not delete the loan.") }
                    }
                }) { Text("Delete", color = Color(0xFFD33F3F), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Keep it") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppBg
    ) { padding ->
        if (current == null) {
            Column(Modifier.padding(padding).fillMaxSize().padding(20.dp)) {
                ShimmerBox(Modifier.fillMaxWidth().height(300.dp), shape = MortgageRadii.CardShape)
                Spacer(Modifier.height(14.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(140.dp), shape = MortgageRadii.CardShape)
            }
            return@Scaffold
        }
        val reported = remember(current) {
            Instant.ofEpochMilli(
                current.updatedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
            ).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextHeadline)
                    }
                    Text(
                        "Loan details",
                        style = MortgageTypography.headlineLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            item {
                MortgageCard(
                    lenderName = current.lenderName,
                    lenderMarkText = current.lenderName.trim().firstOrNull()?.uppercase() ?: "?",
                    balanceText = formatMinor(current.currentBalanceMinor),
                    lastReportedText = reported,
                    monthlyRepaymentText = formatMinor(current.emiAmountMinor),
                    yourShareText = formatMinor(current.emiAmountMinor),
                    onEdit = { onEdit(current) },
                    onDelete = { showDeleteDialog = true },
                    modifier = mortgageCardSharedModifier(current.id)
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailStat("Interest rate", "${current.annualRatePct}% p.a.", Modifier.weight(1f))
                    DetailStat("Tenure", "${current.tenureMonthsTotal} months", Modifier.weight(1f))
                    DetailStat(
                        "Type",
                        current.loanType.name.lowercase().replaceFirstChar { it.uppercase() },
                        Modifier.weight(1f)
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(MortgageRadii.CardShape)
                        .background(CardWhite)
                        .padding(20.dp)
                ) {
                    SectionHeader(title = "Balance over time")
                    Spacer(Modifier.height(12.dp))
                    if (schedule.isEmpty()) {
                        ShimmerBox(Modifier.fillMaxWidth().height(140.dp), shape = MortgageRadii.InnerCardShape)
                    } else {
                        val points = schedule.map { it.balanceAfterMinor.toFloat() }
                        LineChart(points = points, modifier = Modifier.fillMaxWidth().height(140.dp))
                        Spacer(Modifier.height(8.dp))
                        ExplainerText("How your outstanding balance falls with every EMI, assuming no extra payments.")
                    }
                }
            }
            item {
                SectionHeader(title = "Amortization schedule")
            }
            if (schedule.isEmpty()) {
                items(5) {
                    ShimmerBox(Modifier.fillMaxWidth().height(64.dp), shape = MortgageRadii.InnerCardShape)
                }
            } else {
                items(schedule, key = { it.n }, contentType = { "row" }) { row ->
                    ScheduleRowItem(row, formatMinor, monthFmt)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MortgageRadii.InnerCardShape)
            .background(CardWhite)
            .padding(14.dp)
    ) {
        Text(label, style = MortgageTypography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MortgageTypography.titleMedium)
    }
}

@Composable
private fun ScheduleRowItem(
    row: ScheduleRow,
    formatMinor: (Long) -> String,
    monthFmt: DateTimeFormatter
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MortgageRadii.InnerCardShape)
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val due = Instant.ofEpochMilli(row.dueDateMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate().format(monthFmt)
            Text(
                "Month ${row.n} · $due",
                style = MortgageTypography.titleMedium
            )
            Text(
                "Principal ${formatMinor(row.principalMinor)} · Interest ${formatMinor(row.interestMinor)}",
                style = MortgageTypography.bodyMedium
            )
        }
        Text(formatMinor(row.emiMinor), style = MortgageTypography.titleMedium.money())
    }
}
