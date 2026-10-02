package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.TextHeadline
import com.kurupdevs.karz.ui.theme.TextSecondary

/**
 * T9-style amount keypad for payment entry. Digits 1-9, then decimal,
 * 0 and backspace on the last row.
 */
@Composable
fun AmountKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onDecimal: () -> Unit = {}
) {
    val tick = rememberHapticTick()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    KeypadKey(label = digit.toString()) {
                        tick(HapticEvent.Chip)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeypadKey(label = ".") {
                tick(HapticEvent.Chip)
                onDecimal()
            }
            KeypadKey(label = "0") {
                tick(HapticEvent.Chip)
                onDigit(0)
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .pressScale(0.92f)
                    .clickable(onClick = {
                        tick(HapticEvent.Chip)
                        onBackspace()
                    })
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .pressScale(0.92f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MortgageTypography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                color = TextHeadline
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AmountKeypadPreview() {
    KarzTheme {
        AmountKeypad(
            onDigit = {},
            onBackspace = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
