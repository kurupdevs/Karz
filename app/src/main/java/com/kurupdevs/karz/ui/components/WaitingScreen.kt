package com.kurupdevs.karz.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.MortgageTypography
import com.kurupdevs.karz.ui.theme.NavyPrimary
import com.kurupdevs.karz.ui.theme.OnDark
import com.kurupdevs.karz.ui.theme.OnDarkSecondary
import com.kurupdevs.karz.ui.theme.RipplePurple

/**
 * Canvas-drawn animated ripple loader (NOT Lottie/Rive).
 * Three phased rings (2400ms, 800ms offsets) + radial glow pulse +
 * a thin rotating 300-degree arc for the "working" signal.
 * All animated values are read inside the draw lambda: zero recomposition.
 */
@Composable
fun RippleLoader(
    modifier: Modifier = Modifier,
    maxRadius: Dp = 120.dp,
    color: Color = RipplePurple
) {
    val rings = rememberInfiniteTransition(label = "rippleRings")
    val phaseA by rings.animateFloat(0f, 1f, rippleSpec(0), label = "ringA")
    val phaseB by rings.animateFloat(0f, 1f, rippleSpec(800), label = "ringB")
    val phaseC by rings.animateFloat(0f, 1f, rippleSpec(1600), label = "ringC")

    val spinner = rememberInfiniteTransition(label = "rippleSpin")
    val rotation by spinner.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "arcRotation"
    )
    val glow by spinner.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "glowPulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(maxRadius * 2f)) {
            val maxR = maxRadius.toPx()
            // Phased ripple rings: radius = phase * maxRadius, alpha = (1 - phase) * 0.5
            listOf(phaseA, phaseB, phaseC).forEach { phase ->
                val p = phase // read inside draw lambda
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.5f),
                    radius = maxR * p,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            // Radial glow pulse
            val g = glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.25f * (1f - g)),
                        Color.Transparent
                    ),
                    radius = maxR * 0.6f
                ),
                radius = maxR * 0.6f
            )
            // Thin rotating arc, 300 degree sweep
            val r = rotation
            drawArc(
                color = color,
                startAngle = r,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Icon(
            Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = OnDark,
            modifier = Modifier.size(56.dp)
        )
    }
}

private fun rippleSpec(delayMs: Int) = infiniteRepeatable<Float>(
    animation = tween(2400, easing = LinearEasing, delayMillis = delayMs),
    repeatMode = RepeatMode.Restart
)

/**
 * Full-screen "Hold tight" waiting screen (SPEC S7). Navy background,
 * ripple loader, headline and sub copy.
 */
@Composable
fun WaitingScreen(
    title: String = "Hold tight",
    subtitle: String = "We are getting things ready for you",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyPrimary)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RippleLoader()
            Spacer(Modifier.height(32.dp))
            Text(
                text = title,
                style = MortgageTypography.displayLarge.copy(
                    color = OnDark,
                    fontWeight = FontWeight.ExtraBold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MortgageTypography.bodyLarge.copy(color = OnDarkSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaitingScreenPreview() {
    KarzTheme {
        WaitingScreen()
    }
}
