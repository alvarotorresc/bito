package com.alvarotc.bito.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ui.detail.DetailScreen
import com.alvarotc.bito.ui.detail.DetailViewModel
import com.alvarotc.bito.ui.habitform.HabitFormScreen
import com.alvarotc.bito.ui.habitform.HabitFormViewModel
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.SettingsScreen
import com.alvarotc.bito.ui.settings.SettingsViewModel
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
            if (currentRoute in setOf("today", "stats", "settings")) {
                BitoBottomBar(
                    currentRoute = currentRoute,
                    onToday = { nav.popBackStack("today", inclusive = false) },
                    onStats = { nav.navigate("stats") { launchSingleTop = true } },
                    onCreate = { nav.navigate("habit") },
                    onSettings = { nav.navigate("settings") { launchSingleTop = true } },
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
                )
            }
            composable("settings") {
                SettingsScreen(
                    backupViewModel = viewModel(factory = BackupViewModel.factory(container)),
                    settingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
                    onBack = { nav.popBackStack() },
                )
            }
            composable("stats") {
                StatsScreen(
                    viewModel = viewModel(factory = StatsViewModel.factory(container)),
                    onOpenRecords = { nav.navigate("records") },
                    onOpenNumbers = { nav.navigate("numbers") },
                )
            }
        }
    }
}
