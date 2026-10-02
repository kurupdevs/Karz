package com.kurupdevs.karz.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.components.AmountKeypad
import com.kurupdevs.karz.ui.components.ChecklistRow
import com.kurupdevs.karz.ui.components.ExplainerText
import com.kurupdevs.karz.ui.components.PillButton
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.screens.common.HouseGlyph
import com.kurupdevs.karz.ui.screens.common.currencySymbol
import com.kurupdevs.karz.ui.screens.data.AuthGateway
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.NavyPrimary
import com.kurupdevs.karz.ui.theme.OnDark
import com.kurupdevs.karz.ui.theme.OnDarkSecondary
import com.kurupdevs.karz.ui.theme.PastelGreen
import com.kurupdevs.karz.ui.theme.PastelLavender
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.TextHeadline
import com.kurupdevs.karz.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEP_PHONE = 0
private const val STEP_OTP = 1
private const val STEP_NAME = 2
private const val STEP_CURRENCY = 3
private const val STEP_DONE = 4

/**
 * S0 onboarding: phone -> OTP (T9 keypad, Firebase via [AuthGateway]) ->
 * display name -> currency picker (default INR) -> done.
 * Vertical-step transitions (SPEC §5). No phone gating anywhere else in the app.
 */
@Composable
fun OnboardingScreen(
    auth: AuthGateway,
    onComplete: (displayName: String, currency: String) -> Unit
) {
    var step by remember { mutableIntStateOf(STEP_PHONE) }
    var prevStep by remember { mutableIntStateOf(STEP_PHONE) }
    var phone by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }

    fun go(next: Int) {
        prevStep = step
        step = next
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(20.dp)
    ) {
        // navy intro card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MortgageRadii.CardShape)
                .background(NavyPrimary)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mortgages,",
                        style = MortgageTypography.headlineLarge.copy(color = OnDark)
                    )
                    Text(
                        "minus the mystery.",
                        style = MortgageTypography.headlineLarge.copy(color = OnDarkSecondary)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Track your loan, see every rupee of interest, and find out what extra payments really save you.",
                        style = MortgageTypography.bodyMedium.copy(color = OnDarkSecondary)
                    )
                }
                HouseGlyph(Modifier.size(84.dp), tint = PurpleSolid)
            }
        }
        Spacer(Modifier.height(16.dp))

        // white form card with vertical-step transitions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(MortgageRadii.CardShape)
                .background(CardWhite)
                .padding(24.dp)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val up = targetState > prevStep
                    (slideInVertically(tween(350)) { if (up) it / 3 else -it / 3 } + fadeIn(tween(350))) togetherWith
                        (slideOutVertically(tween(350)) { if (up) -it / 3 else it / 3 } + fadeOut(tween(350)))
                },
                label = "onboardingStep"
            ) { s ->
                when (s) {
                    STEP_PHONE -> PhoneStep(auth, phone, onPhone = { phone = it }) { go(STEP_OTP) }
                    STEP_OTP -> OtpStep(auth, phone) { go(STEP_NAME) }
                    STEP_NAME -> NameStep(displayName, onName = { displayName = it }) { go(STEP_CURRENCY) }
                    STEP_CURRENCY -> CurrencyStep(currency, onCurrency = { currency = it }) { go(STEP_DONE) }
                    else -> DoneStep(displayName) { onComplete(displayName, currency) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StepDots(step, 5, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == current) 22.dp else 8.dp, 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) PurpleSolid else Color(0xFFD8D9E6))
            )
        }
    }
}

@Composable
private fun PhoneStep(
    auth: AuthGateway,
    phone: String,
    onPhone: (String) -> Unit,
    onNext: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val tick = rememberHapticTick()
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    Column {
        Text("Your mobile number", style = MortgageTypography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "We will text you a one-time code. That is the only time we ever contact you unprompted.",
            style = MortgageTypography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { onPhone(it.filter { c -> c.isDigit() }.take(10)); error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mobile number") },
            prefix = { Text("+91 ", fontWeight = FontWeight.SemiBold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleSolid,
                cursorColor = PurpleSolid
            )
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = Color(0xFFD33F3F), style = MortgageTypography.bodyMedium)
        }
        Spacer(Modifier.height(18.dp))
        PillButton(
            text = if (sending) "Sending…" else "Send code",
            enabled = phone.length == 10 && !sending,
            onClick = {
                val digits = phone.filter { it.isDigit() }
                if (digits.length != 10) {
                    tick(HapticEvent.ValidationFail)
                    error = "Enter a valid 10-digit mobile number."
                    return@PillButton
                }
                sending = true
                scope.launch {
                    val e164 = "+91$digits"
                    auth.sendOtp(e164)
                        .onSuccess { onNext(e164) }
                        .onFailure { error = "Could not send the code. Check your connection and try again." }
                    sending = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "No spam calls. No selling your number. Ever.",
            style = MortgageTypography.bodyMedium.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OtpStep(auth: AuthGateway, phone: String, onVerified: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tick = rememberHapticTick()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }
    LaunchedEffect(Unit) { cooldown = 60 }

    fun verify(next: String) {
        if (next.length != 6 || verifying) return
        verifying = true
        error = null
        scope.launch {
            auth.verifyOtp(next)
                .onSuccess { onVerified() }
                .onFailure {
                    tick(HapticEvent.ValidationFail)
                    error = "That code did not match. Try again."
                    code = ""
                }
            verifying = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter the 6-digit code", style = MortgageTypography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Sent to $phone", style = MortgageTypography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { i ->
                val ch = code.getOrNull(i)?.toString() ?: ""
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (ch.isEmpty()) Color(0xFFF0F0F7) else PastelLavender),
                    contentAlignment = Alignment.Center
                ) {
                    Text(ch, style = MortgageTypography.headlineMedium)
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Color(0xFFD33F3F), style = MortgageTypography.bodyMedium)
        }
        if (verifying) {
            Spacer(Modifier.height(8.dp))
            Text("Checking…", style = MortgageTypography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        AmountKeypad(
            onDigit = { d ->
                if (code.length < 6) {
                    val next = code + d.toString()
                    code = next
                    verify(next)
                }
            },
            onBackspace = { code = code.dropLast(1) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code",
            style = MortgageTypography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (cooldown > 0) TextSecondary else PurpleSolid
            ),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = cooldown == 0
            ) {
                scope.launch {
                    auth.sendOtp(phone)
                    cooldown = 60
                }
            }
        )
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    val tick = rememberHapticTick()
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("What should we call you?", style = MortgageTypography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Just a display name. Nothing official.", style = MortgageTypography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(40)); error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurpleSolid,
                cursorColor = PurpleSolid
            )
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = Color(0xFFD33F3F), style = MortgageTypography.bodyMedium)
        }
        Spacer(Modifier.height(18.dp))
        PillButton(
            text = "Continue",
            onClick = {
                if (name.trim().length < 2) {
                    tick(HapticEvent.ValidationFail)
                    error = "Give us at least 2 characters."
                } else onNext()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CurrencyStep(currency: String, onCurrency: (String) -> Unit, onNext: () -> Unit) {
    Column {
        Text("Pick your currency", style = MortgageTypography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("You can change this later in Settings.", style = MortgageTypography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        listOf("INR", "GBP").forEach { code ->
            val selected = currency == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale(0.97f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) PastelLavender else Color(0xFFF5F5FA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCurrency(code) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .background(if (selected) PurpleSolid else Color(0xFFE4E4EE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currencySymbol(code),
                        style = MortgageTypography.headlineMedium.copy(
                            color = if (selected) Color.White else TextHeadline
                        )
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (code == "INR") "Indian Rupee" else "British Pound",
                        style = MortgageTypography.titleMedium
                    )
                    Text(code, style = MortgageTypography.bodyMedium)
                }
                if (selected) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(PurpleSolid),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        PillButton(text = "Continue", onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DoneStep(name: String, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(PastelGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E9E5B), modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (name.isNotBlank()) "All set, ${name.trim()}." else "All set.",
            style = MortgageTypography.headlineLarge.copy(textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(10.dp))
        ChecklistRow("Your data stays on your account. Nothing is sold.", Modifier.fillMaxWidth())
        ChecklistRow("No ads. No spam. Just your loan, clearly.", Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        PillButton(text = "Add my loan", onClick = onNext, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ExplainerText(
            "Heads up: the next screen asks for your loan details by hand. " +
                "We never pull your data from a credit bureau.",
            Modifier.fillMaxWidth()
        )
    }
}
