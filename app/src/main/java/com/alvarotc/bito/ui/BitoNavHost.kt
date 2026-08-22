package com.alvarotc.bito.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ui.celebration.BadgeUnlockSheet
import com.alvarotc.bito.ui.celebration.CelebrationsViewModel
import com.alvarotc.bito.ui.celebration.PerfectDaySheet
import com.alvarotc.bito.ui.detail.DetailScreen
import com.alvarotc.bito.ui.detail.DetailViewModel
import com.alvarotc.bito.ui.habi.HabiScreen
import com.alvarotc.bito.ui.habi.HabiViewModel
import com.alvarotc.bito.ui.habitform.HabitFormScreen
import com.alvarotc.bito.ui.habitform.HabitFormViewModel
import com.alvarotc.bito.ui.onboarding.OnboardingScreen
import com.alvarotc.bito.ui.onboarding.OnboardingViewModel
import com.alvarotc.bito.ui.review.ReviewScreen
import com.alvarotc.bito.ui.review.ReviewViewModel
import com.alvarotc.bito.ui.settings.ArchivedScreen
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.SettingsScreen
import com.alvarotc.bito.ui.settings.SettingsViewModel
import com.alvarotc.bito.ui.stats.BadgesScreen
import com.alvarotc.bito.ui.stats.BadgesViewModel
import com.alvarotc.bito.ui.stats.NumbersScreen
import com.alvarotc.bito.ui.stats.NumbersViewModel
import com.alvarotc.bito.ui.stats.RecordsScreen
import com.alvarotc.bito.ui.stats.RecordsViewModel
import com.alvarotc.bito.ui.stats.StatsScreen
import com.alvarotc.bito.ui.stats.StatsViewModel
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.today.TodayScreen
import com.alvarotc.bito.ui.today.TodayViewModel

@Composable
fun BitoNavHost(container: AppContainer) {
    // The very first Settings emission decides where the app opens (today vs onboarding) — a
    // hardcoded "today" start would flash before onboardingDone is known, so nothing but the app
    // background renders until that first value lands. Same "state == null means still loading"
    // gate SettingsScreen already uses for its own DataStore-backed cards.
    val settingsState by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val loadedSettings = settingsState
    if (loadedSettings == null) {
        Box(Modifier.fillMaxSize().background(Papel).testTag("app-loading"))
        return
    }
    // Decided once, from that first resolved value: NavHost keys its graph on startDestination,
    // so re-evaluating this on every recomposition would rebuild the whole nav graph out from
    // under an in-flight finish()-triggered navigate("today") the moment onboardingDone flips.
    val startDestination = remember { if (loadedSettings.onboardingDone) "today" else "onboarding" }

    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = Papel,
        bottomBar = {
            if (currentRoute in setOf("today", "stats", "habi", "settings")) {
                BitoBottomBar(
                    currentRoute = currentRoute,
                    onToday = { nav.popBackStack("today", inclusive = false) },
                    onStats = {
                        nav.navigate("stats") {
                            popUpTo("today")
                            launchSingleTop = true
                        }
                    },
                    onCreate = { nav.navigate("habit") },
                    onHabi = {
                        nav.navigate("habi") {
                            popUpTo("today")
                            launchSingleTop = true
                        }
                    },
                    onSettings = {
                        nav.navigate("settings") {
                            popUpTo("today")
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = startDestination,
            // Consume the outer Scaffold's insets, not just offset for them — otherwise Today's
            // and Settings' own Scaffolds (default contentWindowInsets = systemBars) re-apply
            // the status bar gap on top of this padding under edge-to-edge.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            // Quiet global motion (motion spec §8: 150-250ms, ease-out) — every route change,
            // bottom-bar tab switches included, gets a subtle fade + 1/12-width slide instead of
            // the default hard cut. Every tween below carries LinearOutSlowInEasing explicitly —
            // tween()'s own default (FastOutSlowInEasing) is NOT ease-out, so leaving it implicit
            // on any of the eight calls here would quietly violate the spec on that one. Onboarding's
            // own internal pager motion lives inside OnboardingScreen and is untouched by this;
            // only entering/leaving its route uses this. The juicy celebration sheets are
            // overlays, not routes, so they never see this.
            enterTransition = {
                fadeIn(tween(150, easing = LinearOutSlowInEasing)) +
                    slideInHorizontally(tween(150, easing = LinearOutSlowInEasing)) { it / 12 }
            },
            exitTransition = { fadeOut(tween(150, easing = LinearOutSlowInEasing)) },
            popEnterTransition = {
                fadeIn(tween(150, easing = LinearOutSlowInEasing)) +
                    slideInHorizontally(tween(150, easing = LinearOutSlowInEasing)) { -it / 12 }
            },
            popExitTransition = { fadeOut(tween(150, easing = LinearOutSlowInEasing)) },
        ) {
            // No bottom nav: a fresh install's first-run flow, not a bar destination — same
            // reasoning as "review" below, and not deep-link allowlisted either. T6 builds the
            // welcome + story screens (7a-7d) against OnboardingViewModel's state/callbacks; T7-T8
            // own the rest of the flow's content. This composable's own job stays just the spine:
            // instantiate the VM and leave once it reports done.
            composable("onboarding") {
                val onboardingVm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
                val onboardingState by onboardingVm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(onboardingState.done) {
                    if (onboardingState.done) {
                        nav.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                    }
                }
                Box(Modifier.fillMaxSize().background(Papel).testTag("onboarding-screen")) {
                    OnboardingScreen(onboardingVm)
                }
            }
            composable("today") {
                TodayScreen(
                    viewModel = viewModel(factory = TodayViewModel.factory(container)),
                    onCreateHabit = { nav.navigate("habit") },
                    onOpenHabit = { nav.navigate("detail/$it") },
                    onOpenHabi = {
                        nav.navigate("habi") {
                            popUpTo("today")
                            launchSingleTop = true
                        }
                    },
                    onOpenReview = { nav.navigate("review") },
                )
            }
            composable(
                "habit?id={id}",
                arguments =
                    listOf(
                        navArgument("id") {
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { entry ->
                HabitFormScreen(
                    viewModel = viewModel(factory = HabitFormViewModel.factory(container, entry.arguments?.getString("id"))),
                    onBack = { nav.popBackStack() },
                )
            }
            composable("detail/{habitId}") { entry ->
                val habitId = entry.arguments?.getString("habitId") ?: return@composable
                DetailScreen(
                    viewModel = viewModel(factory = DetailViewModel.factory(container, habitId)),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("habit?id=$it") },
                    onOpenHabi = { nav.navigate("habi") { launchSingleTop = true } },
                )
            }
            composable("settings") {
                SettingsScreen(
                    backupViewModel = viewModel(factory = BackupViewModel.factory(container)),
                    settingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
                    onBack = { nav.popBackStack() },
                    onOpenArchived = { nav.navigate("archived") },
                )
            }
            composable("archived") {
                ArchivedScreen(
                    viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                    onBack = { nav.popBackStack() },
                    onOpenHabit = { nav.navigate("detail/$it") },
                )
            }
            composable("habi") {
                HabiScreen(viewModel = viewModel(factory = HabiViewModel.factory(container)))
            }
            composable("stats") {
                StatsScreen(
                    viewModel = viewModel(factory = StatsViewModel.factory(container)),
                    onOpenRecords = { nav.navigate("records") },
                    onOpenNumbers = { nav.navigate("numbers") },
                    onOpenBadges = { nav.navigate("badges") },
                )
            }
            composable("records") {
                RecordsScreen(
                    viewModel = viewModel(factory = RecordsViewModel.factory(container)),
                    onBack = { nav.popBackStack() },
                )
            }
            composable("numbers") {
                NumbersScreen(
                    viewModel = viewModel(factory = NumbersViewModel.factory(container)),
                    onBack = { nav.popBackStack() },
                )
            }
            // Secondary screen, not in the bottom-bar route set above and not deep-link
            // allowlisted (NavRequests) — reached only from Stats' Logros section.
            composable("badges") {
                BadgesScreen(
                    viewModel = viewModel(factory = BadgesViewModel.factory(container)),
                    onBack = { nav.popBackStack() },
                )
            }
            // No bottom nav: the review is its own flow, not a bar destination (see the
            // bottomBar route set above, which deliberately omits "review").
            composable("review") {
                ReviewScreen(
                    viewModel = viewModel(factory = ReviewViewModel.factory(container)),
                    onClose = { nav.popBackStack() },
                )
            }
        }

        // T11 (+ M9.5 T6): the one bridge from an Intent (a notification tap) to this NavHost.
        // Declared alongside NavHost, not as a sibling of the outer Scaffold — Scaffold subcomposes
        // its content (this whole block) lazily during measurement, so an effect placed outside it
        // would fire before NavHost has set the nav graph and crash navigating anywhere. A route
        // already pending when this composes (MainActivity decoded it before setContent) fires
        // on the very first LaunchedEffect run, same as one that arrives later via onNewIntent.
        //
        // While `onboarding` is the current route, a request must not navigate OVER the flow —
        // same philosophy as the celebration guard below (a mid-flow user shouldn't get sandwiched
        // into e.g. "review"), and genuinely the same MECHANISM this time too: just don't consume.
        // NavRequests.pending is a process-wide MutableStateFlow, the one source of truth for "a
        // route is waiting" — it already survives an Activity recreation on its own, so the fix is
        // to leave it alone (return@LaunchedEffect without calling NavRequests.consume()) rather
        // than copy it into ephemeral remember-scoped state, which an earlier version of this guard
        // did and which reset to nothing on rotation. Once onboarding's own `done` effect above
        // navigates to "today", `currentRoute` changes, this effect re-runs (keyed on it), and the
        // still-pending route fires through the branch below like any other request — no extra
        // state, no ordering argument between two navigate() calls to get right. `currentRoute ==
        // null` gets the same non-consuming treatment for the same reason: NavHost hasn't
        // necessarily set its first back-stack entry the very first time this composes, and a
        // request landing in that narrow window must wait for a REAL route rather than being
        // dropped.
        val pendingRoute by NavRequests.pending.collectAsStateWithLifecycle()
        LaunchedEffect(pendingRoute, currentRoute) {
            val route = pendingRoute ?: return@LaunchedEffect
            if (currentRoute == null || currentRoute == "onboarding") return@LaunchedEffect
            nav.navigate(route) { launchSingleTop = true }
            NavRequests.consume()
        }

        // T12: the two global celebration sheets, overlaid above the NavHost everywhere except
        // the `review` route — E2's SealedDayContent already owns that beat there (its own
        // LaunchedEffect fires the cue and marks it celebrated) — and the `onboarding` route,
        // where a sheet popping up over the first-run flow would be jarring and the celebration
        // hasn't been "seen" by a real user yet. Suppressed on BOTH the sheet and the cue below:
        // cueing is idempotent per pending sheet ([CelebrationsViewModel.cue]'s own
        // `lastCuedSignature` latch), so cueing it once here while hidden would mean it never
        // cues again once the sheet actually shows on Today — the sound would fire silently
        // behind onboarding and the sheet would then render mute. Suppressing both instead keeps
        // the celebration genuinely pending: it shows AND cues the first time it's actually seen,
        // right after onboarding hands off to "today". The perfect day always wins first:
        // dismissing it re-evaluates this `when`, and the badge sheet (if any) follows.
        val celebrations: CelebrationsViewModel = viewModel(factory = CelebrationsViewModel.factory(container))
        val cState by celebrations.uiState.collectAsStateWithLifecycle()
        val celebrationsSuppressed = currentRoute == "review" || currentRoute == "onboarding"
        if (!celebrationsSuppressed) {
            when {
                cState.perfectDayPending -> PerfectDaySheet(cState, onDismiss = celebrations::dismissPerfectDay)
                cState.newBadges.isNotEmpty() -> BadgeUnlockSheet(cState, onDismiss = celebrations::dismissBadges)
            }
        }
        LaunchedEffect(cState.perfectDayPending, cState.newBadges.isNotEmpty(), currentRoute) {
            if (!celebrationsSuppressed && (cState.perfectDayPending || cState.newBadges.isNotEmpty())) {
                celebrations.cue()
            }
        }
    }
}
