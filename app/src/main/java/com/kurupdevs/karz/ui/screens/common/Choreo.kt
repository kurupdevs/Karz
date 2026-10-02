package com.kurupdevs.karz.ui.screens.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kurupdevs.karz.ui.motion.Motion
import com.kurupdevs.karz.ui.theme.PurpleSolid
import kotlin.math.min

/**
 * Card entrance choreography (SPEC §5): one enter flag per screen entry,
 * fadeIn + slideInVertically(+32dp) in one motion, 400ms easeOutQuint,
 * 75ms stagger capped at 375ms. Hero card: delay 0, 450ms, 40dp.
 * Does NOT re-run on tab switch-back (nav uses saveState/restoreState).
 */
@Composable
fun ChoreoScope(
    itemCount: Int,
    screenToken: Any? = null,
    heroIndex: Int = -1,
    content: @Composable (index: Int, enterModifier: Modifier) -> Unit
) {
    var entered by remember(screenToken) { mutableStateOf(false) }
    LaunchedEffect(screenToken) { entered = true }
    androidx.compose.foundation.layout.Column {
        repeat(itemCount) { index ->
            val isHero = index == heroIndex
            val delay = min(index * 75, 375)
            val offsetY by animateFloatAsState(
                targetValue = if (entered) 0f else if (isHero) 40f else 32f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = if (isHero) 450 else 400,
                    delayMillis = delay,
                    easing = Motion.EaseOutQuint
                ),
                label = "choreoY$index"
            )
            val alpha by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = if (isHero) 450 else 400,
                    delayMillis = delay,
                    easing = Motion.EaseOutQuint
                ),
                label = "choreoA$index"
            )
            content(
                index,
                Modifier.graphicsLayer {
                    translationY = offsetY
                    this.alpha = alpha
                }
            )
        }
    }
}

/** Simple line chart with area fill, used for LTV history and balance curves. */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = PurpleSolid,
    fill: Boolean = true
) {
    if (points.size < 2) return
    Canvas(modifier = modifier) {
        val minV = points.min()
        val maxV = points.max()
        val span = (maxV - minV).takeIf { it > 0 } ?: 1f
        val stepX = size.width / (points.size - 1)
        val pts = points.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - minV) / span) * size.height * 0.9f - size.height * 0.05f)
        }
        if (fill) {
            val fillPath = Path().apply {
                moveTo(pts.first().x, size.height)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, size.height)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(
                    0f to lineColor.copy(alpha = 0.25f),
                    1f to lineColor.copy(alpha = 0f)
                )
            )
        }
        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(lineColor, radius = 5.dp.toPx(), center = pts.last())
        drawCircle(Color.White, radius = 2.5.dp.toPx(), center = pts.last())
    }
}

/** Small geometric house glyph used as a lightweight illustration. */
@Composable
fun HouseGlyph(modifier: Modifier = Modifier, tint: Color = PurpleSolid) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(0f, h * 0.45f); lineTo(w / 2f, 0f); lineTo(w, h * 0.45f); close()
        }
        drawPath(roof, tint)
        drawRoundRect(
            tint,
            topLeft = Offset(w * 0.18f, h * 0.45f),
            size = Size(w * 0.64f, h * 0.55f),
            cornerRadius = CornerRadius(w * 0.06f)
        )
        drawRoundRect(
            Color.White,
            topLeft = Offset(w * 0.42f, h * 0.62f),
            size = Size(w * 0.16f, h * 0.38f),
            cornerRadius = CornerRadius(w * 0.04f)
        )
    }
}
