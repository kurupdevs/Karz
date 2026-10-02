package com.kurupdevs.karz.nav

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kurupdevs.karz.data.model.LoanStatus
import com.kurupdevs.karz.di.ServiceLocator
import com.kurupdevs.karz.math.Installment
import com.kurupdevs.karz.ui.components.BottomPillNav
import com.kurupdevs.karz.ui.components.MainTab
import com.kurupdevs.karz.ui.components.PillButton
import com.kurupdevs.karz.ui.motion.Motion
import com.kurupdevs.karz.ui.screens.addloan.AddLoanScreen
import com.kurupdevs.karz.ui.screens.data.LoanRepository
import com.kurupdevs.karz.ui.screens.documents.DocumentsScreen
import com.kurupdevs.karz.ui.screens.home.HomeScreen
import com.kurupdevs.karz.ui.screens.loandetail.LoanDetailScreen
import com.kurupdevs.karz.ui.screens.manage.ManageScreen
import com.kurupdevs.karz.ui.screens.offers.OffersScreen
import com.kurupdevs.karz.ui.screens.onboarding.OnboardingScreen
import com.kurupdevs.karz.ui.screens.settings.SettingsScreen
import com.kurupdevs.karz.ui.simulator.BalanceTransferSection
import com.kurupdevs.karz.ui.simulator.InMemoryScenarioStore
import com.kurupdevs.karz.ui.simulator.SimulatorLoan
import com.kurupdevs.karz.ui.simulator.SimulatorScreen
import com.kurupdevs.karz.ui.simulator.TaxModuleSection
import com.kurupdevs.karz.ui.theme.AppBg
import com.kurupdevs.karz.ui.theme.MortgageTypography
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.launch

private fun tabFor(destination: androidx.navigation.NavDestination?): MainTab? = when {
    destination == null -> null
    destination.hasRoute<RouteMain>() -> MainTab.Main
    destination.hasRoute<RouteManage>() -> MainTab.Manage
    destination.hasRoute<RouteSimulate>() -> MainTab.Simulate
    else -> null
}

private fun MainTab.route(): Any = when (this) {
    MainTab.Main -> RouteMain
    MainTab.Manage -> RouteManage
    MainTab.Simulate -> RouteSimulate
}

/**
 * Root navigation graph. The whole NavHost sits inside a
 * SharedTransitionLayout; destinations receive the shared-transition and
 * visibility scopes through CompositionLocals.
 *
 * Motion (SPEC section 5):
 * - Forward: fadeIn(300) + slide in from Start at 25% width, EaseNav.
 * - Pop: mirrored with SlideDirection.End.
 * - Bottom tabs: crossfade only (200ms) with saveState/restoreState.
 * - Onboarding: vertical slide (height/3) + fade, 350ms.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val editLoanHolder = remember { EditLoanHolder() }
    val profile by ServiceLocator.profile.getProfile()
        .collectAsStateWithLifecycle(initialValue = null)
    val currency = profile?.homeCurrency ?: "INR"

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = RouteMain,
                enterTransition = {
                    fadeIn(tween(300)) + slideIntoContainer(
                        SlideDirection.Start,
                        animationSpec = tween(300, easing = Motion.EaseNav),
                        initialOffset = { it / 4 }
                    )
                },
                exitTransition = {
                    fadeOut(tween(300)) + slideOutOfContainer(
                        SlideDirection.Start,
                        animationSpec = tween(300, easing = Motion.EaseNav),
                        targetOffset = { -it / 4 }
                    )
                },
                popEnterTransition = {
                    fadeIn(tween(300)) + slideIntoContainer(
                        SlideDirection.End,
                        animationSpec = tween(300, easing = Motion.EaseNav),
                        initialOffset = { -it / 4 }
                    )
                },
                popExitTransition = {
                    fadeOut(tween(300)) + slideOutOfContainer(
                        SlideDirection.End,
                        animationSpec = tween(300, easing = Motion.EaseNav),
                        targetOffset = { it / 4 }
                    )
                }
            ) {
                // S0 onboarding: vertical step transitions
                composable<RouteOnboarding>(
                    enterTransition = {
                        fadeIn(tween(350)) + slideIntoContainer(
                            SlideDirection.Up,
                            tween(350),
                            initialOffset = { it / 3 }
                        )
                    },
                    exitTransition = {
                        fadeOut(tween(300)) + slideOutOfContainer(
                            SlideDirection.Up,
                            tween(300),
                            targetOffset = { -it / 3 }
                        )
                    },
                    popEnterTransition = {
                        fadeIn(tween(350)) + slideIntoContainer(
                            SlideDirection.Down,
                            tween(350),
                            initialOffset = { it / 3 }
                        )
                    },
                    popExitTransition = {
                        fadeOut(tween(300)) + slideOutOfContainer(
                            SlideDirection.Down,
                            tween(300),
                            targetOffset = { it / 3 }
                        )
                    }
                ) {
                    CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                        val scope = rememberCoroutineScope()
                        OnboardingScreen(
                            auth = ServiceLocator.auth,
                            onComplete = { displayName, currencyCode ->
                                scope.launch {
                                    ServiceLocator.profile.completeOnboarding(
                                        displayName = displayName,
                                        homeCurrency = currencyCode,
                                        timezone = TimeZone.getDefault().id
                                    )
                                    navController.navigate(RouteAddLoan)
                                }
                            }
                        )
                    }
                }

                // S1 add-loan flow: form, searching theater and found card all
                // live in this one destination (Crossfade inside the screen).
                composable<RouteAddLoan> {
                    CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                        AddLoanScreen(
                            loans = ServiceLocator.loans,
                            currency = currency,
                            onConfirmed = {
                                editLoanHolder.loan = null
                                navController.navigate(RouteMain) {
                                    popUpTo(RouteAddLoan) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() },
                            onDeleted = {
                                editLoanHolder.loan = null
                                navController.popBackStack()
                            },
                            existingLoan = editLoanHolder.loan
                        )
                    }
                }

                // Bottom tabs: crossfade only, state survives tab switches
                tabDestination<RouteMain>(navController) {
                    HomeScreen(
                        loans = ServiceLocator.loans,
                        currency = currency,
                        onEditLoan = {
                            editLoanHolder.loan = it
                            navController.navigate(RouteAddLoan)
                        },
                        onLoanClick = { navController.navigate(RouteLoanDetail(it.id)) },
                        onManage = { navController.navigate(RouteManage) },
                        onAddLoan = {
                            editLoanHolder.loan = null
                            navController.navigate(RouteAddLoan)
                        }
                    )
                }
                tabDestination<RouteManage>(navController) {
                    ManageScreen(
                        loans = ServiceLocator.loans,
                        documents = ServiceLocator.documents,
                        currency = currency,
                        onDocuments = { navController.navigate(RouteDocuments) },
                        onSimulate = { navController.navigate(RouteSimulate) }
                    )
                }
                tabDestination<RouteSimulate>(navController) {
                    SimulateTab(
                        loans = ServiceLocator.loans,
                        onAddLoan = {
                            editLoanHolder.loan = null
                            navController.navigate(RouteAddLoan)
                        }
                    )
                }

                // S4 loan detail (shared-element target for the mortgage card)
                composable<RouteLoanDetail> { entry ->
                    CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                        val args = entry.toRoute<RouteLoanDetail>()
                        LoanDetailScreen(
                            loanId = args.loanId,
                            loans = ServiceLocator.loans,
                            currency = currency,
                            onBack = { navController.popBackStack() },
                            onEdit = {
                                editLoanHolder.loan = it
                                navController.navigate(RouteAddLoan)
                            },
                            onDeleted = { navController.popBackStack() }
                        )
                    }
                }

                // S6 documents, S7 offers, S8 settings
                composable<RouteDocuments> {
                    CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                        DocumentsScreen(
                            documents = ServiceLocator.documents,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable<RouteOffers> {
                    CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                        OffersScreen()
                    }
                }
                composable<RouteSettings> {
                    CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                        SettingsScreen(
                            profile = ServiceLocator.profile,
                            onBack = { navController.popBackStack() },
                            onSignedOut = {
                                navController.navigate(RouteOnboarding) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simulate tab: prepayment simulator plus the tax and balance-transfer
 * sections underneath, all driven by the first active loan.
 */
@Composable
private fun SimulateTab(
    loans: LoanRepository,
    onAddLoan: () -> Unit
) {
    val allLoans by loans.getLoans().collectAsStateWithLifecycle(initialValue = emptyList())
    val loan = allLoans.firstOrNull { it.status == LoanStatus.ACTIVE }

    if (loan == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No loan to simulate yet", style = MortgageTypography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add your loan first, then see exactly what extra payments save you.",
                style = MortgageTypography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            PillButton(text = "Add loan", onClick = onAddLoan)
        }
        return
    }

    val schedule by loans.getSchedule(loan.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val simLoan = remember(loan, schedule) {
        SimulatorLoan(
            loanId = loan.id,
            lenderName = loan.lenderName,
            currencyCode = loan.currency,
            annualRatePct = BigDecimal.valueOf(loan.annualRatePct),
            schedule = schedule.map { row ->
                Installment(
                    n = row.n,
                    dueDate = Instant.ofEpochMilli(row.dueDateMillis)
                        .atZone(ZoneOffset.UTC).toLocalDate(),
                    emiMinor = row.emiMinor,
                    principalMinor = row.principalMinor,
                    interestMinor = row.interestMinor,
                    balanceAfterMinor = row.balanceAfterMinor
                )
            },
            paidInstallments = loan.tenureMonthsElapsed
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SimulatorScreen(
            loan = simLoan,
            // Apply stays hidden: the data layer has not implemented
            // ScheduleApplier (batched schedule rewrite) yet.
            scheduleApplier = null,
            // TODO: swap the in-memory store for a Firestore-backed ScenarioStore
            // adapter (users/{uid}/scenarios) once the SavedScenario <-> string-map
            // mapping lands in the data layer. Saved scenarios currently live
            // for the session only.
            scenarioStore = remember { InMemoryScenarioStore() }
        )
        TaxModuleSection(simLoan)
        BalanceTransferSection(simLoan)
    }
}

/**
 * Bottom-tab destination: crossfade 200ms in every direction, wrapped in
 * the tab scaffold with the sliding pill nav.
 */
private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.tabDestination(
    navController: NavHostController,
    noinline content: @Composable () -> Unit
) {
    composable<T>(
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(200)) }
    ) {
        CompositionLocalProvider(LocalNavVisibilityScope provides this) {
            TabScaffold(navController = navController, content = content)
        }
    }
}

/**
 * Scaffold for the three bottom tabs. The pill nav switches tabs with
 * saveState/restoreState so scroll and UI state survive.
 */
@Composable
private fun TabScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = tabFor(backStackEntry?.destination)
    Scaffold(
        containerColor = AppBg,
        bottomBar = {
            if (selected != null) {
                BottomPillNav(
                    selected = selected,
                    onSelect = { tab ->
                        navController.navigate(tab.route()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            content()
        }
    }
}
