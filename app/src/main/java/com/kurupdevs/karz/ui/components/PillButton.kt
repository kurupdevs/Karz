package com.kurupdevs.karz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.karz.ui.motion.HapticEvent
import com.kurupdevs.karz.ui.motion.pressScale
import com.kurupdevs.karz.ui.motion.rememberHapticTick
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.NavyPrimary
import com.kurupdevs.karz.ui.theme.PurpleSolid
import com.kurupdevs.karz.ui.theme.RippleOnNavy
import com.kurupdevs.karz.ui.theme.RippleOnPurple

enum class PillStyle {
    /** Solid purple, white text (Confirm / Done). */
    Primary,
    /** White pill on gradient cards. */
    OnGradient,
    /** Transparent, purple text. */
    Ghost
}

/**
 * Pill CTA in the three SPEC styles. Press scale 0.96 (0.98 ghost),
 * bounded ripple clipped to the pill shape.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val tick = rememberHapticTick()
    val background = when (style) {
        PillStyle.Primary -> PurpleSolid
        PillStyle.OnGradient -> Color.White
        PillStyle.Ghost -> Color.Transparent
    }
    val contentColor = when (style) {
        PillStyle.Primary -> Color.White
        PillStyle.OnGradient -> NavyPrimary
        PillStyle.Ghost -> PurpleSolid
    }
    val rippleColor = when (style) {
        PillStyle.Primary -> RippleOnPurple
        PillStyle.OnGradient -> RippleOnNavy
        PillStyle.Ghost -> RippleOnNavy
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(if (style == PillStyle.Ghost) 0.98f else 0.96f)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = rippleColor),
                enabled = enabled,
                onClick = {
                    tick(HapticEvent.CtaTap)
                    onClick()
                }
            )
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                style = MortgageTypography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = contentColor
                )
            )
        }
    }
}
