package com.kurupdevs.karz.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MortgageLightColors = lightColorScheme(
    primary = PurpleSolid,
    onPrimary = Color.White,
    primaryContainer = PurpleGradientStart,
    onPrimaryContainer = Color.White,
    secondary = PurpleGradientEnd,
    onSecondary = Color.White,
    tertiary = RipplePurple,
    background = AppBg,
    onBackground = TextHeadline,
    surface = CardWhite,
    onSurface = TextHeadline,
    surfaceVariant = Color(0xFFEDEDF4),
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = AppBg,
    surfaceContainer = Color(0xFFEFEFF5),
    outline = Color(0xFFE2E2EA),
    outlineVariant = Color(0xFFEDEDF4),
    error = Color(0xFFD6455B),
    onError = Color.White
)

/** Radii (SPEC section 2): cards 24-28dp, inner mini-cards 16-20dp, pills full. */
object MortgageRadii {
    val Card: Dp = 26.dp
    val CardSmall: Dp = 24.dp
    val InnerCard: Dp = 18.dp
    val Tile: Dp = 16.dp
    val BottomNav: Dp = 28.dp
    val Pill: Shape = CircleShape
    val CardShape: Shape = RoundedCornerShape(Card)
    val InnerCardShape: Shape = RoundedCornerShape(InnerCard)
}

/**
 * Light M3 theme. v1 is light-only per SPEC (navy cards carry
 * the dark surfaces); no dark theme needed.
 */
@Composable
fun KarzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MortgageLightColors,
        typography = MortgageTypography,
        content = content
    )
}
