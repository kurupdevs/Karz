package com.kurupdevs.karz.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.Serializable

/**
 * Type-safe routes (navigation-compose 2.9). Screens S0-S8 from SPEC section 3.
 * Feature agents own the real screens; the graph wires them together.
 */
@Serializable data object RouteOnboarding
@Serializable data object RouteAddLoan
@Serializable data object RouteSearching
@Serializable data class RouteFound(val loanId: String)
@Serializable data object RouteMain
@Serializable data object RouteManage
@Serializable data object RouteSimulate
@Serializable data class RouteLoanDetail(val loanId: String)
@Serializable data object RouteDocuments
@Serializable data object RouteOffers
@Serializable data object RouteSettings

/**
 * SharedTransitionScope of the root SharedTransitionLayout, available to
 * every destination for sharedElement / sharedBounds calls.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The AnimatedVisibilityScope of the current destination (the
 * AnimatedContentScope of its composable block). Null in previews.
 */
val LocalNavVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
