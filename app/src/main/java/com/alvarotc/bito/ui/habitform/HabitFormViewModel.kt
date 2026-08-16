package com.alvarotc.bito.ui.habitform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/** A WEEKLY_TIMES target counts days, not sessions: 7 is the physical weekly maximum. */
private const val MAX_WEEKLY_TIMES = 7

/** Single source of truth for target clamping, shared by [HabitFormViewModel.adjustTarget] and [HabitFormViewModel.setTarget]. */
private fun HabitFormState.clampTarget(raw: Int): Int =
    if (preset == HabitPreset.QUIT && quitMode == QuitMode.TOTAL) {
        0
    } else {
        val max = if (preset == HabitPreset.WEEKLY_TIMES) MAX_WEEKLY_TIMES else Int.MAX_VALUE
        raw.coerceIn(1, max)
    }

/** Backs the habit create/edit form: one state shape drives all five presets. */
class HabitFormViewModel(
    private val habits: HabitsRepository,
    private val settings: SettingsRepository,
    habitId: String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val formState = MutableStateFlow(HabitFormState(editingId = habitId))
    val state: StateFlow<HabitFormState> = formState.asStateFlow()

    init {
        if (habitId != null) {
            viewModelScope.launch {
                habits.habit(habitId)?.let { entity -> formState.value = entity.toFormState() }
            }
        }
    }

    fun setName(name: String) = formState.update { it.copy(name = name) }

    fun selectPreset(preset: HabitPreset) =
        formState.update {
            it.copy(
                preset = preset,
                target = defaultTargetFor(preset, it.quitMode, it.limitMetric),
                period = if (preset == HabitPreset.WEEKLY_TIMES) Period.WEEK else Period.DAY,
            )
        }

    /** Min 1, except QUIT TOTAL which has no target and stays pinned at 0; WEEKLY_TIMES caps at 7. */
    fun adjustTarget(delta: Int) = formState.update { s -> s.copy(target = s.clampTarget(s.target + delta)) }

    /** Direct numeric entry from [com.alvarotc.bito.ui.components.NumberInputSheet] — same clamp as [adjustTarget]. */
    fun setTarget(value: Int) = formState.update { s -> s.copy(target = s.clampTarget(value)) }

    fun setUnit(unit: String) = formState.update { it.copy(unit = unit) }

    fun selectPeriod(period: Period) = formState.update { it.copy(period = period) }

    fun selectQuitMode(mode: QuitMode) =
        formState.update { it.copy(quitMode = mode, target = defaultTargetFor(it.preset, mode, it.limitMetric)) }

    fun selectLimitMetric(metric: Metric) =
        formState.update { it.copy(limitMetric = metric, target = defaultTargetFor(it.preset, it.quitMode, metric)) }

    fun toggleBinary() = formState.update { it.copy(binaryMode = !it.binaryMode) }

    fun adjustStep(delta: Int) = formState.update { it.copy(step = (it.step + delta).coerceAtLeast(1)) }

    fun setReminder(minutes: Int?) = formState.update { it.copy(reminderMinutes = minutes) }

    /**
     * The double-tap guard reads and flips [HabitFormState.saving] synchronously, before
     * [viewModelScope.launch] — not inside the coroutine — so a second tap arriving before the
     * first coroutine has even started still sees `saving == true` and bails out immediately.
     *
     * `saving` is deliberately NOT reset on the happy-completion path: once a save actually goes
     * through, [onSaved] is about to navigate away and tear this ViewModel down, so there is no
     * legitimate reason to re-enable the button in the meantime. Resetting it there would reopen
     * the exact window this guard exists to close — a tap that lands after the first save
     * finished (e.g. during the nav transition) would otherwise sail past the guard and create a
     * duplicate habit with a fresh UUID. The reset only happens on the early-return branch below
     * (the habit being edited vanished mid-save), where [onSaved] does NOT run and the form stays
     * alive, so leaving `saving` latched there would strand the button disabled forever.
     */
    fun save(onSaved: () -> Unit) {
        val form = formState.value
        if (form.saving || !form.canSave) return
        formState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = LogicalDays.logicalDayOf(now(), settings.settings.first().dayCutoffMinutes, zone())
            val editingId = form.editingId
            if (editingId == null) {
                val sortOrder = (habits.observeHabits().first().maxOfOrNull { it.sortOrder } ?: -1) + 1
                habits.create(form.toNewEntity(UUID.randomUUID().toString(), today, now(), sortOrder))
            } else {
                val existing = habits.habit(editingId)
                if (existing == null) {
                    formState.update { it.copy(saving = false) }
                    return@launch
                }
                habits.update(
                    existing.copy(
                        name = form.name.trim(),
                        target = form.target,
                        unit = form.unit.trim().ifBlank { null },
                        step = form.step,
                        reminderMinutes = form.reminderMinutes,
                    ),
                    today,
                )
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) =
        viewModelScope.launch {
            val id = formState.value.editingId ?: return@launch
            habits.delete(id)
            onDeleted()
        }

    companion object {
        fun factory(
            container: AppContainer,
            habitId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabitFormViewModel(container.habits, container.settings, habitId)
                }
            }
    }
}
