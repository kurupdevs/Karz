package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.PastelGreen

/**
 * Green check row for onboarding / confirmation checklists.
 */
@Composable
fun ChecklistRow(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(PastelGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF1E9E5A),
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MortgageTypography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChecklistRowPreview() {
    KarzTheme {
        ChecklistRow(
            text = "Your loan details are saved securely",
            modifier = Modifier.padding(16.dp)
        )
    }
}
