package com.kurupdevs.karz.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// SPEC section 2 design tokens. Exact values, no deviations.

// Navy surfaces
val NavyPrimary = Color(0xFF1C1C30)
val NavyNearBlack = Color(0xFF14141F)
val NavyDeep = Color(0xFF23233F)

// Brand purple
val PurpleGradientStart = Color(0xFF7C6CF5)
val PurpleGradientEnd = Color(0xFF4A3FD1)
val PurpleSolid = Color(0xFF6C5CE7)
val RipplePurple = Color(0xFF8B7CF6)

// Light surfaces
val AppBg = Color(0xFFF5F5FA)
val CardWhite = Color(0xFFFFFFFF)

// Pastel icon tiles
val PastelPink = Color(0xFFFBE3EC)
val PastelGreen = Color(0xFFDFF3E4)
val PastelBlue = Color(0xFFDCE9FD)
val PastelLavender = Color(0xFFE6E2FB)

// Text
val TextHeadline = Color(0xFF14141F)
val TextSecondary = Color(0xFF8A8A9E)
val OnDark = Color(0xFFFFFFFF)
val OnDarkSecondary = Color(0xA6FFFFFF) // 65% white

// Bottom pill nav
val NavBar = Color(0xFF101014)
val NavInactive = Color(0xFF8E8E9E)

// Bounded ripples, clipped to pill shape
val RippleOnPurple = Color(0x59FFFFFF) // white, 0.35 alpha
val RippleOnNavy = Color(0x4D8B7CF6) // #8B7CF6, 0.30 alpha

// Shimmer palettes (SPEC section 5)
val ShimmerLightBase = Color(0xFFE9EAF2)
val ShimmerLightHighlight = Color(0xFFFAFBFE)
val ShimmerBgBase = Color(0xFFE3E5EF)
val ShimmerBgHighlight = Color(0xFFF4F5FA)
val ShimmerNavyBase = Color(0xFF23233F)
val ShimmerNavyHighlight = Color(0xFF31314E)
val ShimmerGradientBase = Color(0xFF2A2A45)
val ShimmerGradientHighlight = Color(0xFF3B3B5E)

/**
 * Brand gradient at 135 degrees (top-left to bottom-right diagonal).
 * Pass the card size from the layout scope.
 */
fun brandGradient(width: Float, height: Float): Brush = Brush.linearGradient(
    colors = listOf(PurpleGradientStart, PurpleGradientEnd),
    start = Offset(0f, 0f),
    end = Offset(width, height)
)
