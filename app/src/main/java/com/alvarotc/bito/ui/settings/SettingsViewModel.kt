package com.alvarotc.bito.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// internal, not private: CutoffSheet (SettingsScreen.kt, same package) clamps its ± stepper to
// the same window so the single source of truth for "how far the cutoff can move" lives here.
internal const val MAX_CUTOFF_MINUTES = 6 * 60
internal const val CUTOFF_STEP_MINUTES = 30

/** One archived habit's row on the Ajustes "archived" list and screen. */
data class ArchivedHabitUi(val id: String, val name: String, val archivedOnDay: LogicalDay?)

/**
 * Backs the Ajustes "day" and "reminders" cards (day cutoff, global reminder hours, review
 * time) plus the archived-habits row/list. `state` is null until the repository's first
 * emission lands — the screen renders those cards only once it has a real value to show.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val habits: HabitsRepository,
) : ViewModel() {
    private val settingsState = MutableStateFlow<Settings?>(null)
    val state: StateFlow<Settings?> = settingsState.asStateFlow()

    private val archivedState = MutableStateFlow<List<ArchivedHabitUi>>(emptyList())
    val archivedHabits: StateFlow<List<ArchivedHabitUi>> = archivedState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { settingsState.value = it }
        }
        viewModelScope.launch {
            habits.observeHabits().collect { entities ->
                archivedState.value =
                    entities
                        .filter { it.status == HabitStatus.ARCHIVED }
                        .map { ArchivedHabitUi(it.id, it.name, it.archivedOnDay) }
            }
        }
    }

    /** Clamped to 0:00–6:00 and rounded to the nearest half hour, in that order. */
    fun setCutoff(minutes: Int) {
        val clamped = minutes.coerceIn(0, MAX_CUTOFF_MINUTES)
        val rounded = (clamped.toDouble() / CUTOFF_STEP_MINUTES).roundToInt() * CUTOFF_STEP_MINUTES
        write { it.copy(dayCutoffMinutes = rounded) }
    }

    fun addReminder(minutes: Int) = write { it.copy(globalReminderMinutes = (it.globalReminderMinutes + minutes).distinct().sorted()) }

    fun removeReminder(minutes: Int) = write { it.copy(globalReminderMinutes = it.globalReminderMinutes.filterNot { m -> m == minutes }) }

    fun setReviewTime(minutes: Int) = write { it.copy(reviewTimeMinutes = minutes) }

    fun setHabiSounds(enabled: Boolean) = write { it.copy(habiSoundsEnabled = enabled) }

    fun setPerfectDayCelebration(enabled: Boolean) = write { it.copy(perfectDayCelebration = enabled) }

    /** null → follow the system. DataStore stays the source of truth (and what travels in backups); AppLocale only applies it. */
    fun setLanguage(tag: String?) {
        write { it.copy(languageTag = tag) }
        AppLocale.apply(tag)
    }

    /** Trimmed, mirroring [com.alvarotc.bito.ui.onboarding.OnboardingViewModel.finish]'s own write of this field. */
    fun setUserName(value: String) = write { it.copy(userName = value.trim()) }

    private fun write(transform: (Settings) -> Settings) {
        viewModelScope.launch { settings.update(transform) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SettingsViewModel(container.settings, container.habits) }
            }
    }
}
