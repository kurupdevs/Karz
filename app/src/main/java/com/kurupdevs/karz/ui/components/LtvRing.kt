package com.kurupdevs.karz.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.motion.Motion
import com.kurupdevs.karz.ui.theme.KarzTheme
import com.kurupdevs.karz.ui.theme.PurpleGradientEnd
import com.kurupdevs.karz.ui.theme.PurpleGradientStart

/**
 * LTV progress ring: Canvas drawArc with a purple to blue sweep gradient.
 * The animated progress is read INSIDE the draw lambda so the ring settles
 * with springGentle at 60-120fps without recomposition.
 *
 * @param ltv loan-to-value as 0..1
 */
@Composable
fun LtvRing(
    ltv: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 148.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = Color(0xFFE9EAF2),
    label: @Composable (() -> Unit)? = null
) {
    val animated by animateFloatAsState(
        targetValue = ltv.coerceIn(0f, 1f),
        animationSpec = Motion.springGentle(),
        label = "ltvRing"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            val progress = animated // read inside the draw lambda: redraw, no recomposition
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(PurpleGradientStart, PurpleGradientEnd, PurpleGradientStart)
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        if (label != null) {
            Box(contentAlignment = Alignment.Center) { label() }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LtvRingPreview() {
    KarzTheme {
        LtvRing(ltv = 0.71f)
    }
}
