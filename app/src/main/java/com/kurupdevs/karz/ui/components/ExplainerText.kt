package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.TextSecondary

/**
 * Small secondary explainer line, e.g. the LTV band copy.
 */
@Composable
fun ExplainerText(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        if (showIcon) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MortgageTypography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExplainerTextPreview() {
    KarzTheme {
        ExplainerText(
            text = "You will reach the 70% LTV band before your current deal ends.",
            showIcon = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}
