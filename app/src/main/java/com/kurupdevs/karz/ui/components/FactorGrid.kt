package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.theme.CardWhite
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageRadii
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PastelBlue
import com.kurupdevs.karz.ui.theme.PastelGreen
import com.kurupdevs.karz.ui.theme.PastelLavender
import com.kurupdevs.karz.ui.theme.PastelPink
import com.kurupdevs.karz.ui.theme.TextHeadline

data class FactorTile(
    val icon: ImageVector,
    val iconTint: Color,
    val tileColor: Color,
    val title: String,
    val value: String,
    val onClick: () -> Unit
)

/**
 * 2x2 grid of pastel tiles (SPEC FactorGrid).
 */
@Composable
fun FactorGrid(
    tiles: List<FactorTile>,
    modifier: Modifier = Modifier
) {
    val tick = rememberHapticTick()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { tile ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(
                                elevation = 8.dp,
                                shape = MortgageRadii.InnerCardShape,
                                ambientColor = Color.Black.copy(alpha = 0.12f),
                                spotColor = Color.Black.copy(alpha = 0.12f)
                            )
                            .clip(MortgageRadii.InnerCardShape)
                            .background(CardWhite)
                            .pressScale(0.96f)
                            .clickable(onClick = {
                                tick(HapticEvent.Chip)
                                tile.onClick()
                            })
                            .padding(16.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(tile.tileColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tile.icon,
                                    contentDescription = null,
                                    tint = tile.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = tile.title,
                                style = MortgageTypography.labelLarge
                            )
                            Text(
                                text = tile.value,
                                style = MortgageTypography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextHeadline
                                )
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FactorGridPreview() {
    KarzTheme {
        FactorGrid(
            tiles = listOf(
                FactorTile(Icons.Filled.Home, Color(0xFF1E9E5A), PastelGreen, "Home value", "₹60,00,000") {},
                FactorTile(Icons.Filled.AccountBalanceWallet, Color(0xFFD6455B), PastelPink, "Balance", "₹42,50,000") {},
                FactorTile(Icons.Filled.Calculate, Color(0xFF3B6FD4), PastelBlue, "EMI", "₹43,391") {},
                FactorTile(Icons.Filled.Search, Color(0xFF6C5CE7), PastelLavender, "Rate", "8.5%") {}
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
