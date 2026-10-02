package com.kurupdevs.karz.ui.screens.common

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.rememberSharedContentState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kurupdevs.karz.nav.LocalNavVisibilityScope
import com.kurupdevs.karz.nav.LocalSharedTransitionScope
import com.kurupdevs.karz.ui.motion.Motion

/**
 * sharedBounds modifier for the mortgage card morph into S4
 * (key "mortgage-card-$loanId"). Reads the scopes that the NavGraph
 * publishes through CompositionLocals; falls back to a plain Modifier
 * when they are absent (e.g. previews).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun mortgageCardSharedModifier(loanId: String): Modifier {
    val scope = LocalSharedTransitionScope.current
    val visibility = LocalNavVisibilityScope.current
    if (scope == null || visibility == null) return Modifier
    return with(scope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "mortgage-card-$loanId"),
            animatedVisibilityScope = visibility,
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            boundsTransform = { _, _ -> Motion.easeOutQuint(400) }
        )
    }
}
