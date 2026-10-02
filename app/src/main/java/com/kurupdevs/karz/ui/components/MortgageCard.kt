package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.OnDark
import com.kurupdevs.karz.ui.theme.OnDarkSecondary
import com.kurupdevs.karz.ui.theme.PurpleGradientStart
import com.kurupdevs.karz.ui.theme.brandGradient
import com.kurupdevs.karz.ui.theme.money

/**
 * Purple gradient mortgage card (SPEC S1/S2 layout): lender mark + name,
 * Delete, Balance, last reported date, Monthly Repayment | Your Share,
 * white Edit details pill.
 *
 * Pass a shared-element modifier (sharedBounds key "mortgage-card-$id")
 * when navigating to the loan detail screen.
 */
@Composable
fun MortgageCard(
    lenderName: String,
    lenderMarkText: String,
    balanceText: String,
    lastReportedText: String,
    monthlyRepaymentText: String,
    yourShareText: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val gradient = remember(cardSize) {
        brandGradient(cardSize.width.toFloat(), cardSize.height.toFloat())
    }
    Box(
        modifier = modifier
            .onSizeChanged { cardSize = it }
            .shadow(
                elevation = 16.dp,
                shape = MortgageRadii.CardShape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = PurpleGradientStart.copy(alpha = 0.35f)
            )
            .clip(MortgageRadii.CardShape)
            .background(brush = gradient)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lenderMarkText,
                        style = MortgageTypography.titleLarge.copy(color = OnDark)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = lenderName,
                    style = MortgageTypography.titleMedium.copy(color = OnDark),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Delete",
                    style = MortgageTypography.labelLarge.copy(
                        color = OnDark,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Balance",
                style = MortgageTypography.labelLarge.copy(color = OnDarkSecondary)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = balanceText,
                style = MortgageTypography.displayLarge.copy(color = OnDark).money()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Last reported $lastReportedText",
                style = MortgageTypography.labelMedium.copy(color = OnDarkSecondary)
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Repayment",
                        style = MortgageTypography.labelMedium.copy(color = OnDarkSecondary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = monthlyRepaymentText,
                        style = MortgageTypography.titleMedium.copy(color = OnDark).money()
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Your Share",
                        style = MortgageTypography.labelMedium.copy(color = OnDarkSecondary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = yourShareText,
                        style = MortgageTypography.titleMedium.copy(color = OnDark).money()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = "Edit details",
                onClick = onEdit,
                style = PillStyle.OnGradient,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MortgageCardPreview() {
    KarzTheme {
        MortgageCard(
            lenderName = "Lloyds Bank",
            lenderMarkText = "L",
            balanceText = "₹42,50,000",
            lastReportedText = "12 Sep 2026",
            monthlyRepaymentText = "₹43,391",
            yourShareText = "₹43,391",
            onEdit = {},
            onDelete = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
