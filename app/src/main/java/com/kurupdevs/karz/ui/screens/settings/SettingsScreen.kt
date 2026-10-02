package com.kurupdevs.karz.ui.screens.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.kurupdevs.karz.ui.components.ExplainerText
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.screens.common.currencySymbol
import com.kurupdevs.karz.ui.screens.data.ProfileRepository
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.NavyPrimary
import com.kurupdevs.karz.ui.theme.OnDark
import com.kurupdevs.karz.ui.theme.OnDarkSecondary
import com.kurupdevs.karz.ui.theme.PastelLavender
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.TextHeadline
import com.kurupdevs.karz.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * S8 Settings: profile, currency, timezone, notification prefs, sign out, about.
 */
@Composable
fun SettingsScreen(
    profile: ProfileRepository,
    onBack: () -> Unit = {},
    onSignedOut: () -> Unit = {}
) {
    val user by profile.getProfile().collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNameDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppBg
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextHeadline)
                }
                Text(
                    "Settings", style = MortgageTypography.headlineLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(MortgageRadii.CardShape)
                    .background(CardWhite)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(PastelLavender),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user?.displayName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                            style = MortgageTypography.headlineMedium.copy(color = PurpleSolid)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user?.displayName ?: "…", style = MortgageTypography.titleLarge)
                        Text(user?.phoneNumber ?: "", style = MortgageTypography.bodyMedium)
                    }
                    TextButton(onClick = { showNameDialog = true }) {
                        Text("Edit", color = PurpleSolid, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(MortgageRadii.CardShape)
                    .background(CardWhite)
                    .padding(vertical = 6.dp)
            ) {
                SettingsRow(
                    title = "Currency",
                    value = "${currencySymbol(user?.homeCurrency ?: "INR")} ${user?.homeCurrency ?: "INR"}",
                    onClick = { showCurrencyDialog = true }
                )
                SettingsRow(
                    title = "Timezone",
                    value = user?.timezone ?: ZoneId.systemDefault().id,
                    onClick = {
                        scope.launch {
                            profile.updateTimezone(ZoneId.systemDefault().id)
                            snackbar.showSnackbar("Timezone updated.")
                        }
                    }
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Payment reminders", style = MortgageTypography.titleMedium)
                        Text("EMI due dates and milestones", style = MortgageTypography.bodyMedium)
                    }
                    Switch(
                        checked = user?.notificationsEnabled ?: true,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                profile.updateNotificationsEnabled(enabled)
                                    .onFailure { snackbar.showSnackbar("Could not save that preference.") }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = PurpleSolid)
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth()
                    .pressScale(0.98f)
                    .clip(MortgageRadii.CardShape)
                    .background(CardWhite)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showSignOutDialog = true }
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Sign out", style = MortgageTypography.titleMedium.copy(color = Color(0xFFD33F3F)))
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(MortgageRadii.CardShape)
                    .background(NavyPrimary)
                    .padding(20.dp)
            ) {
                Text("Karz", style = MortgageTypography.titleLarge.copy(color = OnDark))
                Text("Version 1.0.0", style = MortgageTypography.labelLarge.copy(color = OnDarkSecondary))
                Spacer(Modifier.height(10.dp))
                AboutCheck("No ads.")
                AboutCheck("Your data stays yours. Nothing is sold.")
                AboutCheck("Made by kurupdevs.")
                Spacer(Modifier.height(10.dp))
                ExplainerText(
                    "Karz shows you what your loan really costs. " +
                        "Numbers here are estimates from your entries; your lender's figures are final.",
                    Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf(user?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Display name", style = MortgageTypography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleSolid,
                        cursorColor = PurpleSolid
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNameDialog = false
                    scope.launch {
                        profile.updateDisplayName(name.trim())
                            .onFailure { snackbar.showSnackbar("Could not save the name.") }
                    }
                }) { Text("Save", color = PurpleSolid, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Currency", style = MortgageTypography.titleLarge) },
            text = {
                Column {
                    listOf("INR" to "Indian Rupee", "GBP" to "British Pound").forEach { (code, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showCurrencyDialog = false
                                    scope.launch {
                                        profile.updateCurrency(code)
                                            .onFailure { snackbar.showSnackbar("Could not change currency.") }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currencySymbol(code), style = MortgageTypography.headlineMedium.copy(color = PurpleSolid))
                            Spacer(Modifier.width(12.dp))
                            Text("$label ($code)", style = MortgageTypography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCurrencyDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?", style = MortgageTypography.titleLarge) },
            text = { Text("Your loans stay safe in your account. You can sign back in any time.", style = MortgageTypography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    scope.launch {
                        profile.signOut()
                            .onSuccess { onSignedOut() }
                            .onFailure { snackbar.showSnackbar("Could not sign out. Try again.") }
                    }
                }) { Text("Sign out", color = Color(0xFFD33F3F), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Stay") } }
        )
    }
}

@Composable
private fun AboutCheck(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MortgageTypography.bodyLarge.copy(color = OnDark))
    }
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MortgageTypography.titleMedium)
            Text(value, style = MortgageTypography.bodyMedium)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}
