package com.alvarotc.bito.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.alvarotc.bito.ui.review.ReviewScreen
import com.alvarotc.bito.ui.review.ReviewViewModel
import com.alvarotc.bito.ui.settings.ArchivedScreen
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.SettingsScreen
import com.alvarotc.bito.ui.settings.SettingsViewModel
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
            startDestination = "today",
            // Consume the outer Scaffold's insets, not just offset for them — otherwise Today's
            // and Settings' own Scaffolds (default contentWindowInsets = systemBars) re-apply
            // the status bar gap on top of this padding under edge-to-edge.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
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
            // No bottom nav: the review is its own flow, not a bar destination (see the
            // bottomBar route set above, which deliberately omits "review").
            composable("review") {
                ReviewScreen(
                    viewModel = viewModel(factory = ReviewViewModel.factory(container)),
                    onClose = { nav.popBackStack() },
                )
            }
        }

        // T11: the one bridge from an Intent (a notification tap) to this NavHost. Declared
        // alongside NavHost, not as a sibling of the outer Scaffold — Scaffold subcomposes its
        // content (this whole block) lazily during measurement, so an effect placed outside it
        // would fire before NavHost has set the nav graph and crash navigating anywhere. A route
        // already pending when this composes (MainActivity decoded it before setContent) fires
        // on the very first LaunchedEffect run, same as one that arrives later via onNewIntent.
        val pendingRoute by NavRequests.pending.collectAsStateWithLifecycle()
        LaunchedEffect(pendingRoute) {
            pendingRoute?.let {
                nav.navigate(it) { launchSingleTop = true }
                NavRequests.consume()
            }
        }

        // T12: the two global celebration sheets, overlaid above the NavHost everywhere except
        // the `review` route — E2's SealedDayContent already owns that beat there (its own
        // LaunchedEffect fires the cue and marks it celebrated). The perfect day always wins
        // first: dismissing it re-evaluates this `when`, and the badge sheet (if any) follows.
        val celebrations: CelebrationsViewModel = viewModel(factory = CelebrationsViewModel.factory(container))
        val cState by celebrations.uiState.collectAsStateWithLifecycle()
        if (currentRoute != "review") {
            when {
                cState.perfectDayPending -> PerfectDaySheet(cState, onDismiss = celebrations::dismissPerfectDay)
                cState.newBadges.isNotEmpty() -> BadgeUnlockSheet(cState, onDismiss = celebrations::dismissBadges)
            }
        }
        LaunchedEffect(cState.perfectDayPending, cState.newBadges.isNotEmpty(), currentRoute) {
            if (currentRoute != "review" && (cState.perfectDayPending || cState.newBadges.isNotEmpty())) {
                celebrations.cue()
            }
        }
    }
}
