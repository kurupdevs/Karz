package com.kurupdevs.karz.ui.screens.addloan

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.data.model.Loan
import com.kurupdevs.karz.data.model.LoanStatus
import com.kurupdevs.karz.data.model.LoanType
import com.kurupdevs.karz.data.model.RateType
import com.kurupdevs.karz.math.emiFor
import com.kurupdevs.karz.ui.components.ExplainerText
import com.kurupdevs.karz.ui.components.MortgageCard
import com.kurupdevs.karz.ui.components.PillButton
import com.kurupdevs.karz.ui.components.ShimmerBox
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.screens.common.MortgageIcons
import com.kurupdevs.karz.ui.screens.common.currencySymbol
import com.kurupdevs.karz.ui.screens.common.mortgageCardSharedModifier
import com.kurupdevs.karz.ui.screens.common.parseMajorToMinor
import com.kurupdevs.karz.ui.screens.common.rememberMinorFormatter
import com.kurupdevs.karz.ui.screens.data.LoanDraft
import com.kurupdevs.karz.ui.screens.data.LoanRepository
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PastelBlue
import com.kurupdevs.karz.ui.theme.PastelGreen
import com.kurupdevs.karz.ui.theme.PastelLavender
import com.kurupdevs.karz.ui.theme.PastelPink
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.TextHeadline
import com.kurupdevs.karz.ui.theme.TextSecondary
import com.kurupdevs.karz.ui.theme.money
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val COMMON_LENDERS = listOf(
    "State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank",
    "Kotak Mahindra Bank", "Punjab National Bank", "Bank of Baroda",
    "LIC Housing Finance", "Canara Bank", "Union Bank of India"
)

private sealed interface AddLoanUiState {
    data object Form : AddLoanUiState
    data class Searching(val draft: LoanDraft) : AddLoanUiState
    data class Found(val loanId: String, val loan: Loan) : AddLoanUiState
}

/**
 * S1: manual add-loan form -> "Searching for your mortgage" skeleton (>=900ms dwell)
 * -> "We've found your mortgage" card (mockup layout, honest copy: data comes
 * from the user's own entries, never claimed from TransUnion).
 *
 * [existingLoan] turns the screen into edit mode.
 */
@Composable
fun AddLoanScreen(
    loans: LoanRepository,
    currency: String,
    onConfirmed: (loanId: String) -> Unit,
    onBack: () -> Unit = {},
    onDeleted: () -> Unit = {},
    existingLoan: Loan? = null
) {
    var uiState by remember { mutableStateOf<AddLoanUiState>(AddLoanUiState.Form) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val formatMinor = rememberMinorFormatter(currency)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppBg
    ) { padding ->
        Box(Modifier.padding(padding)) {
            Crossfade(
                targetState = uiState,
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                label = "addLoanPhase"
            ) { state ->
                when (state) {
                    is AddLoanUiState.Form -> LoanForm(
                        loans = loans,
                        currency = currency,
                        existingLoan = existingLoan,
                        onBack = onBack,
                        onSave = { draft -> uiState = AddLoanUiState.Searching(draft) }
                    )
                    is AddLoanUiState.Searching -> SearchingTheater(
                        draft = state.draft,
                        loans = loans,
                        existingLoan = existingLoan,
                        onFound = { id, loan -> uiState = AddLoanUiState.Found(id, loan) },
                        onError = {
                            scope.launch { snackbar.showSnackbar("Could not save the loan. Please try again.") }
                            uiState = AddLoanUiState.Form
                        }
                    )
                    is AddLoanUiState.Found -> FoundCard(
                        loan = state.loan,
                        formatMinor = formatMinor,
                        onEdit = { uiState = AddLoanUiState.Form },
                        onDelete = {
                            scope.launch {
                                loans.archiveLoan(state.loanId)
                                onDeleted()
                            }
                        },
                        onConfirm = { onConfirmed(state.loanId) }
                    )
                }
            }
        }
    }
}

/* --------------------------------- the form --------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanForm(
    loans: LoanRepository,
    currency: String,
    existingLoan: Loan?,
    onBack: () -> Unit,
    onSave: (LoanDraft) -> Unit
) {
    val scope = rememberCoroutineScope()
    val tick = rememberHapticTick()
    val symbol = currencySymbol(currency)

    var lenderName by remember { mutableStateOf(existingLoan?.lenderName ?: "") }
    var lenderFocused by remember { mutableStateOf(false) }
    var accountLast4 by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf(existingLoan?.loanType ?: LoanType.HOME) }
    var principal by remember { mutableStateOf(existingLoan?.let { (it.principalOriginalMinor / 100).toString() } ?: "") }
    var outstanding by remember { mutableStateOf(existingLoan?.let { (it.currentBalanceMinor / 100).toString() } ?: "") }
    var homeValue by remember { mutableStateOf(existingLoan?.homeValueMinor?.let { (it / 100).toString() } ?: "") }
    var rate by remember { mutableStateOf(existingLoan?.annualRatePct?.toString() ?: "") }
    var rateType by remember { mutableStateOf(existingLoan?.rateType ?: RateType.FLOATING) }
    var tenure by remember { mutableStateOf(existingLoan?.tenureMonthsTotal?.toString() ?: "") }
    var startDate by remember {
        mutableStateOf(
            existingLoan?.startDate ?: LocalDate.now().minusYears(1)
        )
    }
    var emiDay by remember { mutableIntStateOf(existingLoan?.emiDayOfMonth ?: 5) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var pendingDraft by remember { mutableStateOf<LoanDraft?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }

    fun validate(): Map<String, String> {
        val e = mutableMapOf<String, String>()
        if (lenderName.trim().length < 2) e["lender"] = "Tell us who lent you the money."
        val pMinor = parseMajorToMinor(principal)
        if (pMinor == null) e["principal"] = "Enter the original loan amount."
        val oMinor = parseMajorToMinor(outstanding)
        if (oMinor == null) e["outstanding"] = "Enter what you still owe."
        if (pMinor != null && oMinor != null && oMinor > pMinor) e["outstanding"] = "Outstanding cannot be more than the original amount."
        val r = rate.trim().toDoubleOrNull()
        if (r == null || r < 0 || r > 50) e["rate"] = "Rate must be between 0 and 50%."
        val t = tenure.trim().toIntOrNull()
        if (t == null || t < 6 || t > 360) e["tenure"] = "Tenure must be 6 to 360 months."
        if (startDate.isAfter(LocalDate.now())) e["start"] = "Start date cannot be in the future."
        if (emiDay !in 1..28) e["emiDay"] = "EMI day must be 1 to 28."
        if (homeValue.isNotBlank() && parseMajorToMinor(homeValue) == null) e["homeValue"] = "Enter a valid home value, or leave it empty."
        if (accountLast4.isNotBlank() && (accountLast4.length != 4 || accountLast4.any { !it.isDigit() })) {
            e["account"] = "Enter the last 4 digits, or leave it empty."
        }
        return e
    }

    fun buildDraft(): LoanDraft? {
        val errs = validate()
        errors = errs
        if (errs.isNotEmpty()) {
            tick(HapticEvent.ValidationFail)
            return null
        }
        val pMinor = parseMajorToMinor(principal)!!
        val oMinor = parseMajorToMinor(outstanding)!!
        val r = rate.trim().toDouble()
        val t = tenure.trim().toInt()
        val emi = emiFor(oMinor, BigDecimal.valueOf(r), t)
        return LoanDraft(
            lenderName = lenderName.trim(),
            loanType = loanType,
            accountRefMasked = accountLast4.takeIf { it.length == 4 }?.let { "•••• $it" } ?: "",
            principalOriginalMinor = pMinor,
            currentBalanceMinor = oMinor,
            annualRatePct = r,
            rateType = rateType,
            tenureMonthsTotal = t,
            emiDayOfMonth = emiDay,
            emiAmountMinor = emi,
            startDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            homeValueMinor = homeValue.takeIf { it.isNotBlank() }?.let { parseMajorToMinor(it) },
            currency = currency
        )
    }

    fun attemptSave() {
        val draft = buildDraft() ?: return
        saving = true
        scope.launch {
            val dup = loans.isDuplicate(
                draft.lenderName, draft.accountRefMasked, draft.loanType,
                excludeLoanId = existingLoan?.id
            )
            saving = false
            if (dup && existingLoan == null) {
                pendingDraft = draft
                showDuplicateDialog = true
            } else {
                onSave(draft)
            }
        }
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Already added?", style = MortgageTypography.titleLarge) },
            text = { Text("A loan with this lender and account looks like one you already added. Add it again anyway?", style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicateDialog = false
                    pendingDraft?.let { onSave(it) }
                }) { Text("Add anyway", color = PurpleSolid, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) { Text("Go back") }
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        errors = errors - "start"
                    }
                    showDatePicker = false
                }) { Text("OK", color = PurpleSolid) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextHeadline)
            }
            Text(
                if (existingLoan == null) "Add your loan" else "Edit loan",
                style = MortgageTypography.headlineLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FormSectionCard(title = "Lender", tileColor = PastelBlue) {
                OutlinedTextField(
                    value = lenderName,
                    onValueChange = { lenderName = it; lenderFocused = true; errors = errors - "lender" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Lender name") },
                    placeholder = { Text("e.g. HDFC Bank") },
                    singleLine = true,
                    isError = errors.containsKey("lender"),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                if (lenderFocused && lenderName.length >= 2) {
                    val matches = COMMON_LENDERS.filter {
                        it.contains(lenderName.trim(), ignoreCase = true) && !it.equals(lenderName.trim(), ignoreCase = true)
                    }.take(4)
                    if (matches.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF5F5FA)).padding(vertical = 4.dp)
                        ) {
                            matches.forEach { m ->
                                Text(
                                    m, style = MortgageTypography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            lenderName = m
                                            lenderFocused = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
                FieldError(errors["lender"])
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = accountLast4,
                    onValueChange = { accountLast4 = it.filter { c -> c.isDigit() }.take(4); errors = errors - "account" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Account last 4 digits (optional)") },
                    placeholder = { Text("4821") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = errors.containsKey("account"),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                FieldError(errors["account"])
            }

            FormSectionCard(title = "Loan type", tileColor = PastelLavender) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoanType.entries.forEach { type ->
                        val selected = loanType == type
                        Box(
                            Modifier.weight(1f)
                                .pressScale(0.95f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) PurpleSolid else Color(0xFFF0F0F7))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { loanType = type }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MortgageTypography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else TextHeadline
                                )
                            )
                        }
                    }
                }
            }

            FormSectionCard(title = "Amounts", tileColor = PastelGreen) {
                MoneyInputField(label = "Original loan amount", value = principal, symbol = symbol, error = errors["principal"]) {
                    principal = it; errors = errors - "principal"
                }
                Spacer(Modifier.height(8.dp))
                MoneyInputField(label = "Current outstanding", value = outstanding, symbol = symbol, error = errors["outstanding"]) {
                    outstanding = it; errors = errors - "outstanding"
                }
                Spacer(Modifier.height(8.dp))
                MoneyInputField(label = "Home value (optional, for LTV)", value = homeValue, symbol = symbol, error = errors["homeValue"]) {
                    homeValue = it; errors = errors - "homeValue"
                }
            }

            FormSectionCard(title = "Rate and tenure", tileColor = PastelPink) {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' }.take(5); errors = errors - "rate" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Annual interest rate") },
                    suffix = { Text("% p.a.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errors.containsKey("rate"),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                FieldError(errors["rate"])
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RateType.entries.forEach { rt ->
                        val selected = rateType == rt
                        Box(
                            Modifier.weight(1f)
                                .pressScale(0.95f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) PurpleSolid else Color(0xFFF0F0F7))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { rateType = rt }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (rt == RateType.FIXED) "Fixed" else "Floating",
                                style = MortgageTypography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else TextHeadline
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tenure,
                    onValueChange = { tenure = it.filter { c -> c.isDigit() }.take(3); errors = errors - "tenure" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tenure") },
                    suffix = { Text("months") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = errors.containsKey("tenure"),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                FieldError(errors["tenure"])
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(120, 180, 240, 360).forEach { quick ->
                        Box(
                            Modifier.pressScale(0.95f)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF0F0F7))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    tenure = quick.toString()
                                    errors = errors - "tenure"
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "${quick / 12}y",
                                style = MortgageTypography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextHeadline
                                )
                            )
                        }
                    }
                }
            }

            FormSectionCard(title = "Repayment schedule", tileColor = PastelBlue) {
                val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5FA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showDatePicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(MortgageIcons.Calendar, contentDescription = null, tint = PurpleSolid)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Loan start date", style = MortgageTypography.labelLarge)
                        Text(startDate.format(dateFmt), style = MortgageTypography.titleMedium)
                    }
                }
                FieldError(errors["start"])
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5FA))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("EMI day of month", style = MortgageTypography.labelLarge)
                        Text("$emiDay of each month", style = MortgageTypography.titleMedium)
                    }
                    StepperButton(Icons.Filled.Remove) { if (emiDay > 1) { emiDay -= 1; errors = errors - "emiDay" } }
                    Spacer(Modifier.width(8.dp))
                    StepperButton(Icons.Filled.Add) { if (emiDay < 28) { emiDay += 1; errors = errors - "emiDay" } }
                }
                FieldError(errors["emiDay"])
            }

            val estEmi = remember(principal, rate, tenure) {
                val p = parseMajorToMinor(principal)
                val r = rate.trim().toDoubleOrNull()
                val t = tenure.trim().toIntOrNull()
                if (p != null && r != null && r in 0.0..50.0 && t != null && t in 6..360) {
                    emiFor(p, BigDecimal.valueOf(r), t)
                } else null
            }
            if (estEmi != null) {
                val formatMinor = rememberMinorFormatter(currency)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PastelLavender)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Estimated monthly repayment", style = MortgageTypography.labelLarge)
                        Text(
                            formatMinor(estEmi),
                            style = MortgageTypography.headlineLarge.money()
                        )
                    }
                    Text(
                        "Estimate only.\nYour lender sets\nthe final EMI.",
                        style = MortgageTypography.labelMedium.copy(textAlign = TextAlign.End)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        PillButton(
            text = if (existingLoan == null) "Find my mortgage" else "Save changes",
            onClick = ::attemptSave,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurpleSolid,
    cursorColor = PurpleSolid
)

@Composable
private fun FieldError(msg: String?) {
    if (msg != null) {
        Text(
            msg, color = Color(0xFFD33F3F), style = MortgageTypography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    tileColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(MortgageRadii.CardShape)
            .background(CardWhite)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tileColor),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(PurpleSolid.copy(alpha = 0.55f)))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MortgageTypography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MoneyInputField(
    label: String,
    value: String,
    symbol: String,
    error: String?,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { c -> c.isDigit() }.take(12)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("$symbol ", fontWeight = FontWeight.SemiBold) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = error != null,
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors()
    )
    FieldError(error)
}

@Composable
private fun StepperButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp)
            .pressScale(0.9f)
            .clip(CircleShape)
            .background(CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = PurpleSolid, modifier = Modifier.size(18.dp))
    }
}

/* ------------------------------ searching theater ---------------------------- */

@Composable
private fun SearchingTheater(
    draft: LoanDraft,
    loans: LoanRepository,
    existingLoan: Loan?,
    onFound: (String, Loan) -> Unit,
    onError: () -> Unit
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(draft) {
        val started = System.currentTimeMillis()
        scope.launch {
            val result = if (existingLoan == null) {
                loans.addLoan(draft)
            } else {
                loans.updateLoan(existingLoan.id, draft).map { existingLoan.id }
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 900) kotlinx.coroutines.delay(900 - elapsed)
            result.onSuccess { id ->
                val loan = Loan(
                    id = id,
                    lenderName = draft.lenderName,
                    loanType = draft.loanType,
                    accountRefMasked = draft.accountRefMasked.takeIf { it.isNotBlank() },
                    principalOriginalMinor = draft.principalOriginalMinor,
                    currentBalanceMinor = draft.currentBalanceMinor,
                    annualRatePct = draft.annualRatePct,
                    rateType = draft.rateType,
                    tenureMonthsTotal = draft.tenureMonthsTotal,
                    emiDayOfMonth = draft.emiDayOfMonth,
                    emiAmountMinor = draft.emiAmountMinor,
                    startDate = Instant.ofEpochMilli(draft.startDateMillis)
                        .atZone(ZoneId.systemDefault()).toLocalDate(),
                    nextDueDate = Instant.ofEpochMilli(System.currentTimeMillis()),
                    homeValueMinor = draft.homeValueMinor,
                    currency = draft.currency,
                    status = LoanStatus.ACTIVE
                )
                onFound(id, loan)
            }.onFailure { onError() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Searching for your mortgage", style = MortgageTypography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("Pulling together what you told us…", style = MortgageTypography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(320.dp), shape = MortgageRadii.CardShape)
        Spacer(Modifier.height(16.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.6f).height(28.dp), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(56.dp), shape = CircleShape)
    }
}

/* --------------------------------- found card -------------------------------- */

@Composable
private fun FoundCard(
    loan: Loan,
    formatMinor: (Long) -> String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    val reported = remember {
        Instant.ofEpochMilli(loan.updatedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
            .atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this loan?", style = MortgageTypography.titleLarge) },
            text = { Text("It will be archived. Your payment history stays on your account.", style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = Color(0xFFD33F3F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Keep it") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().background(AppBg)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("We've found your mortgage", style = MortgageTypography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "We get this information from your entries. If it isn't up to date, you can edit it below.",
            style = MortgageTypography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        MortgageCard(
            lenderName = loan.lenderName,
            lenderMarkText = loan.lenderName.trim().firstOrNull()?.uppercase() ?: "?",
            balanceText = formatMinor(loan.currentBalanceMinor),
            lastReportedText = reported,
            monthlyRepaymentText = formatMinor(loan.emiAmountMinor),
            yourShareText = formatMinor(loan.emiAmountMinor),
            onEdit = onEdit,
            onDelete = { showDeleteDialog = true },
            modifier = mortgageCardSharedModifier(loan.id)
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Total monthly", style = MortgageTypography.labelLarge)
                Text(formatMinor(loan.emiAmountMinor), style = MortgageTypography.displayLarge.money())
            }
            PillButton(text = "Confirm", onClick = onConfirm)
        }
        Spacer(Modifier.height(12.dp))
        ExplainerText(
            "This is based on what you entered, not a bank feed. " +
                "Edit any detail and the numbers update everywhere.",
            Modifier.fillMaxWidth()
        )
    }
}
