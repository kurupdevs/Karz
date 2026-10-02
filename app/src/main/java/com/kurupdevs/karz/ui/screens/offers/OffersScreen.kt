package com.kurupdevs.karz.ui.screens.offers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.components.PillButton
import com.kurupdevs.karz.ui.components.PillStyle
import com.kurupdevs.karz.ui.components.WaitingScreen
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.OnDark

/**
 * S7 Offers (v1 teaser). Navy "Hold tight" waiting screen with the Canvas
 * ripple, honest copy, and a disabled "Get your offers" teaser.
 * Real offer catalog + eligibility engine = Phase 2. No lead-gen, ever.
 */
@Composable
fun OffersScreen() {
    Box(Modifier.fillMaxSize()) {
        WaitingScreen(
            title = "Hold tight",
            subtitle = "You might be eligible for an offer, one of the team will be in touch soon."
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PillButton(
                text = "Get your offers",
                onClick = {},
                enabled = false,
                style = PillStyle.OnGradient,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Coming soon. We will never sell your details to lenders.",
                style = MortgageTypography.bodyMedium.copy(
                    color = OnDark.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
