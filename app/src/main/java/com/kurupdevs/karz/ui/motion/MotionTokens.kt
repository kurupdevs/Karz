package com.kurupdevs.karz.ui.motion

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.kurupdevs.karz.ui.theme.ShimmerBgBase
import com.kurupdevs.karz.ui.theme.ShimmerBgHighlight
import com.kurupdevs.karz.ui.theme.ShimmerGradientBase
import com.kurupdevs.karz.ui.theme.ShimmerGradientHighlight
import com.kurupdevs.karz.ui.theme.ShimmerLightBase
import com.kurupdevs.karz.ui.theme.ShimmerLightHighlight
import com.kurupdevs.karz.ui.theme.ShimmerNavyBase
import com.kurupdevs.karz.ui.theme.ShimmerNavyHighlight

/**
 * Motion tokens (SPEC section 5). Every animation in the app references
 * these; no ad-hoc specs. Banned: dampingRatio < 0.5 (looks toy-like).
 */
object Motion {

    /** Button press/release, chips, tiles. */
    fun <T> springFast(): AnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 700f)

    /** Nav pill morph, slider tick snap. */
    fun <T> springSnappy(): AnimationSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = 1200f)

    /** Sheet reveal, success pop, hero card entrance. Max 1 overshoot. */
    fun <T> springBouncy(): AnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 400f)

    /** LTV ring settle, slider release. */
    fun <T> springGentle(): AnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 200f)

    /** Release spring for press scale: no bounce on the way up. */
    fun <T> springRelease(): AnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 700f)

    val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Nav transitions (0.05, 0.7, 0.1, 1). */
    val EaseNav = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Card entrances, most fades/slides. */
    fun <T> easeOutQuint(durationMs: Int = 400): AnimationSpec<T> =
        tween(durationMs, easing = EaseOutQuint)

    /** Crossfades, skeleton to content. */
    fun <T> easeInOut(durationMs: Int = 300): AnimationSpec<T> =
        tween(durationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing)
}

// ---------------------------------------------------------------------------
// Reduced motion
// ---------------------------------------------------------------------------

/** True when the user disabled animations (ANIMATOR_DURATION_SCALE == 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

// ---------------------------------------------------------------------------
// Shimmer (draw phase only, never recomposition)
// ---------------------------------------------------------------------------

enum class ShimmerPalette(val base: Color, val highlight: Color) {
    Light(ShimmerLightBase, ShimmerLightHighlight),
    Screen(ShimmerBgBase, ShimmerBgHighlight),
    Navy(ShimmerNavyBase, ShimmerNavyHighlight),
    Gradient(ShimmerGradientBase, ShimmerGradientHighlight)
}

/**
 * Themed skeleton shimmer drawn in the draw phase. Static when the user
 * has animations disabled.
 */
fun Modifier.shimmer(
    palette: ShimmerPalette = ShimmerPalette.Light,
    visible: Boolean = true
): Modifier = composed {
    if (!visible) return@composed this
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    drawWithContent {
        drawContent()
        val p = if (reducedMotion) 0.35f else sweep
        val x = (p * 2f - 1f) * size.width
        val brush = Brush.linearGradient(
            colors = listOf(palette.base, palette.highlight, palette.base),
            start = Offset(x - size.width / 2f, 0f),
            end = Offset(x + size.width / 2f, 0f)
        )
        drawRect(brush = brush)
    }
}

// ---------------------------------------------------------------------------
// Press scale
// ---------------------------------------------------------------------------

/**
 * Press feedback: scales down while pressed, springs back with
 * dampingRatio 1.0 (no bounce up). Place before clickable().
 * Primary pills 0.96, ghost 0.98, icon tiles 0.92.
 */
fun Modifier.pressScale(targetScale: Float = 0.96f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) targetScale else 1f,
        animationSpec = Motion.springRelease(),
        label = "pressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }
            )
        }
}

// ---------------------------------------------------------------------------
// Haptics (max 1 per 80ms per source)
// ---------------------------------------------------------------------------

enum class HapticEvent {
    /** Primary CTA tap. */
    CtaTap,
    /** Chips and small toggles. */
    Chip,
    /** Bottom tab switch. */
    TabSwitch,
    /** Slider tick / LTV band tick. */
    SliderTick,
    /** Bottom sheet open. */
    SheetOpen,
    /** Bottom sheet close. */
    SheetClose,
    /** Long press. */
    LongPressAction,
    /** Validation failure. */
    ValidationFail
}

private const val HAPTIC_MIN_INTERVAL_MS = 80L

private fun HapticEvent.toFeedbackConstant(): Int {
    val rPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    return when (this) {
        HapticEvent.CtaTap -> if (rPlus) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.Chip -> if (rPlus) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.TabSwitch -> HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.SliderTick -> if (rPlus) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.SheetOpen -> if (rPlus) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.SheetClose -> if (rPlus) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.LongPressAction -> HapticFeedbackConstants.LONG_PRESS
        HapticEvent.ValidationFail -> if (rPlus) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
}

/**
 * Rate-limited haptic performer. Emits at most one buzz per 80ms.
 * Never call during entrance choreography.
 */
@Composable
fun rememberHapticTick(): (HapticEvent) -> Unit {
    val view = LocalView.current
    val lastTick = remember { mutableLongStateOf(0L) }
    return remember(view) {
        { event: HapticEvent ->
            val now = SystemClock.uptimeMillis()
            if (now - lastTick.longValue >= HAPTIC_MIN_INTERVAL_MS) {
                lastTick.longValue = now
                view.performHapticFeedback(event.toFeedbackConstant())
            }
        }
    }
}
