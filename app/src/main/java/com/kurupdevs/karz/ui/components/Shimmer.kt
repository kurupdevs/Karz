package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.motion.ShimmerPalette
import com.kurupdevs.karz.ui.motion.shimmer
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PurpleSolid

/**
 * Themed skeleton box. Radii should match the real component it stands in for.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    palette: ShimmerPalette = ShimmerPalette.Light,
    shape: Shape = MortgageRadii.CardShape,
    visible: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.base)
            .shimmer(palette = palette, visible = visible)
    )
}

/**
 * Section header: bold title with an optional trailing action.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MortgageTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        if (actionText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionText,
                style = MortgageTypography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = PurpleSolid
                ),
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShimmerPreview() {
    KarzTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(title = "Payment history", actionText = "See all")
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                palette = ShimmerPalette.Light
            )
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                palette = ShimmerPalette.Navy
            )
        }
    }
}
