package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.OnDark
import com.kurupdevs.karz.ui.theme.OnDarkSecondary
import com.kurupdevs.karz.ui.theme.PurpleGradientStart
import com.kurupdevs.karz.ui.theme.brandGradient

/**
 * Gradient banner with headline, sub copy, an illustration slot and a
 * white CTA pill. Used for the simulator teaser on Manage.
 */
@Composable
fun BannerCard(
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable BoxScope.() -> Unit = {}
) {
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val gradient = remember(cardSize) {
        brandGradient(cardSize.width.toFloat(), cardSize.height.toFloat())
    }
    Box(
        modifier = modifier
            .onSizeChanged { cardSize = it }
            .shadow(
                elevation = 12.dp,
                shape = MortgageRadii.CardShape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = PurpleGradientStart.copy(alpha = 0.35f)
            )
            .clip(MortgageRadii.CardShape)
            .background(brush = gradient)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MortgageTypography.headlineMedium.copy(
                        color = OnDark,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MortgageTypography.bodyMedium.copy(color = OnDarkSecondary)
                )
                Spacer(Modifier.height(14.dp))
                PillButton(
                    text = ctaText,
                    onClick = onCta,
                    style = PillStyle.OnGradient
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(0.35f),
                contentAlignment = Alignment.Center,
                content = illustration
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerCardPreview() {
    KarzTheme {
        BannerCard(
            title = "Simulate a prepayment",
            subtitle = "See how much interest you would save",
            ctaText = "Try it",
            onCta = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
