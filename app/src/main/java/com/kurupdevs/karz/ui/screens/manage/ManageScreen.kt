package com.kurupdevs.karz.ui.screens.manage

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.data.model.LoanStatus
import com.kurupdevs.karz.data.model.Payment
import com.kurupdevs.karz.data.model.PaymentType
import com.kurupdevs.karz.ui.components.AmountKeypad
import com.kurupdevs.karz.ui.components.BannerCard
import com.kurupdevs.karz.ui.components.ExplainerText
import com.kurupdevs.karz.ui.components.PillButton
import com.kurupdevs.karz.ui.components.SectionHeader
import com.kurupdevs.karz.ui.components.ShimmerBox
import com.kurupdevs.karz.ui.components.StatTile
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.screens.common.HouseGlyph
import com.kurupdevs.karz.ui.screens.common.MortgageIcons
import com.kurupdevs.karz.ui.screens.common.currencySymbol
import com.kurupdevs.karz.ui.screens.common.rememberMinorFormatter
import com.kurupdevs.karz.ui.screens.data.DocumentRepository
import com.kurupdevs.karz.ui.screens.data.LoanRepository
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PastelBlue
import com.kurupdevs.karz.ui.theme.PastelGreen
import com.kurupdevs.karz.ui.theme.PastelPink
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.TextHeadline
import com.kurupdevs.karz.ui.theme.money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * S3 Manage: stat tiles (documents count via [DocumentRepository] flow, EMI day),
 * "Make a payment" bottom sheet (amount keypad, date, emi/part_prepayment type,
 * appends an immutable ledger entry), keyed payment history with skeleton on
 * refresh, and the simulator banner. Bottom nav is owned by the TabScaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    loans: LoanRepository,
    documents: DocumentRepository,
    currency: String,
    onDocuments: () -> Unit,
    onSimulate: () -> Unit,
    onBack: () -> Unit = {}
) {
    val allLoans by loans.getLoans().collectAsStateWithLifecycle(initialValue = emptyList())
    val loan = allLoans.firstOrNull { it.status == LoanStatus.ACTIVE }
    val docCount by documents.getDocumentCount().collectAsStateWithLifecycle(initialValue = 0)
    val formatMinor = rememberMinorFormatter(currency)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPaySheet by remember { mutableStateOf(false) }
    var showEmiDaySheet by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppBg
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (loan == null) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Nothing to manage yet", style = MortgageTypography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    ExplainerText("Add a loan first, then track payments here.", Modifier.fillMaxWidth())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextHeadline)
                        }
                        Text(
                            "Manage",
                            style = MortgageTypography.headlineLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Column(
                        Modifier.weight(1f).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SectionHeader(title = "See all payments")
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(MortgageRadii.CardShape)
                                .background(CardWhite)
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            StatTile(
                                icon = MortgageIcons.Doc,
                                iconTint = Color(0xFFD35D7E),
                                tileColor = PastelPink,
                                title = "Saved documents",
                                value = "$docCount",
                                onClick = onDocuments,
                                modifier = Modifier.fillMaxWidth()
                            )
                            StatTile(
                                icon = MortgageIcons.Calendar,
                                iconTint = Color(0xFF2E9E5B),
                                tileColor = PastelGreen,
                                title = "Date of regular payment",
                                value = "${loan.emiDayOfMonth} of each month",
                                onClick = { showEmiDaySheet = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        MakePaymentRow(currency = currency, onClick = { showPaySheet = true })
                        BannerCard(
                            title = "Simulate a prepayment",
                            subtitle = "See how much interest you would save",
                            ctaText = "Try it",
                            onCta = onSimulate,
                            illustration = {
                                HouseGlyph(
                                    Modifier.size(72.dp),
                                    tint = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        )
                        SectionHeader(title = "Payment history")
                        PaymentHistoryList(loan, loans, formatMinor, currency, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showPaySheet && loan != null) {
        PaymentSheet(
            loan = loan,
            currency = currency,
            onDismiss = { showPaySheet = false },
            onSaved = { amountMinor, paidAtMillis, type ->
                scope.launch {
                    loans.recordPayment(loan.id, amountMinor, paidAtMillis, type)
                        .onSuccess {
                            showPaySheet = false
                            snackbar.showSnackbar("Payment recorded.")
                        }
                        .onFailure { snackbar.showSnackbar("Could not record the payment. Try again.") }
                }
            }
        )
    }

    if (showEmiDaySheet && loan != null) {
        EmiDaySheet(
            current = loan.emiDayOfMonth,
            onDismiss = { showEmiDaySheet = false },
            onSave = { day ->
                scope.launch {
                    loans.updateEmiDay(loan.id, day)
                        .onSuccess {
                            showEmiDaySheet = false
                            snackbar.showSnackbar("EMI day updated to $day of each month.")
                        }
                        .onFailure { snackbar.showSnackbar("Could not update the EMI day.") }
                }
            }
        )
    }
}

@Composable
private fun MakePaymentRow(currency: String, onClick: () -> Unit) {
    val tick = rememberHapticTick()
    Row(
        Modifier.fillMaxWidth()
            .pressScale(0.97f)
            .clip(MortgageRadii.InnerCardShape)
            .background(CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                tick(HapticEvent.Chip)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PastelBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                currencySymbol(currency),
                style = MortgageTypography.headlineMedium.copy(color = Color(0xFF3E6FD8))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Make a payment", style = MortgageTypography.titleMedium)
            Text("Deposit money early", style = MortgageTypography.bodyMedium)
        }
        Icon(Icons.Filled.Add, contentDescription = null, tint = PurpleSolid, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheet(
    loan: Loan,
    currency: String,
    onDismiss: () -> Unit,
    onSaved: (amountMinor: Long, paidAtMillis: Long, type: PaymentType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountStr by remember { mutableStateOf("") }
    var paidDate by remember { mutableStateOf(LocalDate.now()) }
    var type by remember { mutableStateOf(PaymentType.EMI) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tick = rememberHapticTick()
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = paidDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        paidDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK", color = PurpleSolid) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = CardWhite) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Make a payment", style = MortgageTypography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text("Deposit money early", style = MortgageTypography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentType.EMI to "EMI", PaymentType.PART_PREPAYMENT to "Part prepayment").forEach { (t, label) ->
                    val selected = type == t
                    Box(
                        Modifier.weight(1f)
                            .pressScale(0.95f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) PurpleSolid else Color(0xFFF0F0F7))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { type = t }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MortgageTypography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else TextHeadline
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text(
                if (amountStr.isBlank()) "${currencySymbol(currency)}0" else "${currencySymbol(currency)}$amountStr",
                style = MortgageTypography.displayLarge.copy(fontSize = androidx.compose.ui.unit.sp(38))
            )
            Spacer(Modifier.height(6.dp))
            AmountKeypad(
                onDigit = { d ->
                    val next = amountStr + d.toString()
                    if (next.replace(".", "").length <= 12) {
                        amountStr = next
                        error = null
                    }
                },
                onBackspace = { amountStr = amountStr.dropLast(1) },
                onDecimal = {
                    if (!amountStr.contains(".")) {
                        amountStr = if (amountStr.isEmpty()) "0." else "$amountStr."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF5F5FA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showDatePicker = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(MortgageIcons.Calendar, contentDescription = null, tint = PurpleSolid, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(paidDate.format(dateFmt), style = MortgageTypography.titleMedium)
            }
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error!!, color = Color(0xFFD33F3F), style = MortgageTypography.bodyMedium)
            }
            Spacer(Modifier.height(14.dp))
            PillButton(
                text = "Record payment",
                onClick = {
                    val minor = amountStr.toBigDecimalOrNull()
                        ?.multiply(BigDecimal(100))
                        ?.setScale(0, RoundingMode.HALF_UP)
                        ?.toLong()
                    if (minor == null || minor <= 0) {
                        tick(HapticEvent.ValidationFail)
                        error = "Enter an amount greater than zero."
                        return@PillButton
                    }
                    onSaved(
                        minor,
                        paidDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        type
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Payments are recorded permanently and cannot be edited later.",
                style = MortgageTypography.bodyMedium.copy(textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmiDaySheet(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var day by remember { mutableIntStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = CardWhite) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Date of regular payment", style = MortgageTypography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("Which day of the month does your EMI go out?", style = MortgageTypography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperBtn(Icons.Filled.Remove) { if (day > 1) day-- }
                Spacer(Modifier.width(20.dp))
                Text("$day", style = MortgageTypography.displayLarge.copy(fontSize = androidx.compose.ui.unit.sp(44)))
                Spacer(Modifier.width(20.dp))
                StepperBtn(Icons.Filled.Add) { if (day < 28) day++ }
            }
            Spacer(Modifier.height(6.dp))
            Text("of each month", style = MortgageTypography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            PillButton("Save", onClick = { onSave(day) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepperBtn(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp)
            .pressScale(0.9f)
            .clip(CircleShape)
            .background(com.kurupdevs.karz.ui.theme.PastelLavender)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = PurpleSolid, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentHistoryList(
    loan: Loan,
    loans: LoanRepository,
    formatMinor: (Long) -> String,
    currency: String,
    modifier: Modifier = Modifier
) {
    val payments by loans.getPayments(loan.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                delay(600)
                refreshing = false
            }
        },
        modifier = modifier,
        state = rememberPullToRefreshState(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                isRefreshing = refreshing,
                containerColor = CardWhite,
                color = PurpleSolid,
                threshold = 64.dp
            )
        }
    ) {
        Crossfade(
            targetState = refreshing,
            animationSpec = tween(200),
            label = "historySkeleton"
        ) { skeleton ->
            if (skeleton) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { ShimmerBox(Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(18.dp)) }
                }
            } else if (payments.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardWhite)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No payments recorded yet.", style = MortgageTypography.bodyMedium)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(payments, key = { it.id }, contentType = { "payment" }) { p ->
                        PaymentRow(p, formatMinor, currency, dateFmt)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(
    payment: Payment,
    formatMinor: (Long) -> String,
    currency: String,
    dateFmt: DateTimeFormatter
) {
    val date = payment.paidAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    } ?: "Scheduled"
    val typeLabel = when (payment.type) {
        PaymentType.EMI -> "EMI"
        PaymentType.PART_PREPAYMENT -> "Part prepayment"
        PaymentType.FEE -> "Fee"
        PaymentType.OTHER -> "Other"
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(
                    if (payment.type == PaymentType.PART_PREPAYMENT) PastelGreen
                    else com.kurupdevs.karz.ui.theme.PastelLavender
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (payment.type == PaymentType.PART_PREPAYMENT) "+" else currencySymbol(currency),
                style = MortgageTypography.titleMedium.copy(
                    color = if (payment.type == PaymentType.PART_PREPAYMENT) Color(0xFF2E9E5B) else PurpleSolid
                )
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(typeLabel, style = MortgageTypography.titleMedium)
            Text(date, style = MortgageTypography.bodyMedium)
        }
        Text(formatMinor(payment.amountMinor), style = MortgageTypography.titleMedium.money())
    }
}
