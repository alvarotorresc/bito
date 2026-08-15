package com.alvarotc.bito.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ui.habitform.HabitFormScreen
import com.alvarotc.bito.ui.habitform.HabitFormViewModel
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.SettingsScreen
import com.alvarotc.bito.ui.settings.SettingsViewModel
import com.alvarotc.bito.ui.today.TodayScreen
import com.alvarotc.bito.ui.today.TodayViewModel

@Composable
fun BitoNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "today") {
        composable("today") {
            TodayScreen(
                viewModel = viewModel(factory = TodayViewModel.factory(container)),
                onCreateHabit = { nav.navigate("habit") },
                onEditHabit = { nav.navigate("habit?id=$it") },
                onOpenSettings = { nav.navigate("settings") },
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
        composable("settings") {
            SettingsScreen(
                backupViewModel = viewModel(factory = BackupViewModel.factory(container)),
                settingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
