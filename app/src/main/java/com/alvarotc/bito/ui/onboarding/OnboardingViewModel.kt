package com.alvarotc.bito.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habitform.HabitFormState
import com.alvarotc.bito.ui.habitform.HabitPreset
import com.alvarotc.bito.ui.habitform.QuitMode
import com.alvarotc.bito.ui.habitform.defaultTargetFor
import com.alvarotc.bito.ui.habitform.toNewEntity
import com.alvarotc.bito.ui.settings.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/** The first-run flow's beats, in the order [OnboardingViewModel.next] walks. */
enum class OnboardingStep { WELCOME, STORY_1, STORY_2, STORY_3, NAME, PERSONALITY, FIRST_HABIT }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    // null = system; the screen shows ES/EN chips for it.
    val languageTag: String? = null,
    val name: String = "",
    val personality: Personality = Personality.NEUTRA,
    val habitName: String = "",
    // HabitPreset, not the brief's placeholder "HabitKind" — the real type HabitFormViewModel
    // builds habits from (see HabitFormModel.kt). Field/callback names below are the brief's,
    // unchanged, so T6-T8 build against the same contract.
    val habitKind: HabitPreset = HabitPreset.DAILY_CHECK,
    val habitTarget: Int = 1,
    val busy: Boolean = false,
    // true -> NavHost navigates to today.
    val done: Boolean = false,
)

/**
 * Backs the first-run onboarding flow (T6-T8 build the screens against this state/callback
 * contract). [finish] is the one place that writes: it closes the two gaps nothing else in the
 * app closes today — [com.alvarotc.bito.data.settings.Settings.userName] (every voiced string
 * falls back to "campeón" until this runs) and
 * [com.alvarotc.bito.data.settings.Settings.onboardingDone] — and, if the user named a first
 * habit, creates it through the exact same write path
 * [com.alvarotc.bito.ui.habitform.HabitFormViewModel.save] uses: a [HabitFormState] built here,
 * mapped through the shared [toNewEntity] extension, then [HabitsRepository.create] followed by
 * [PointsReconciler.reconcile] — same as that VM's create branch.
 */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val habits: HabitsRepository,
    private val reconciler: PointsReconciler,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    private val steps = OnboardingStep.entries

    /** Persists + applies live, mirroring [com.alvarotc.bito.ui.settings.SettingsViewModel.setLanguage]. */
    fun setLanguage(tag: String?) {
        state.update { it.copy(languageTag = tag) }
        viewModelScope.launch { settings.update { it.copy(languageTag = tag) } }
        AppLocale.apply(tag)
    }

    fun next() {
        val idx = steps.indexOf(state.value.step)
        if (idx < steps.lastIndex) state.update { it.copy(step = steps[idx + 1]) }
    }

    fun back() {
        val idx = steps.indexOf(state.value.step)
        if (idx > 0) state.update { it.copy(step = steps[idx - 1]) }
    }

    /** From any story beat, jumps straight to the name step — skips whatever story is left. */
    fun skipStory() = state.update { it.copy(step = OnboardingStep.NAME) }

    fun setName(value: String) = state.update { it.copy(name = value) }

    fun setHabitName(value: String) = state.update { it.copy(habitName = value) }

    /**
     * Mirrors [com.alvarotc.bito.ui.habitform.HabitFormViewModel.selectPreset]'s own reset: the
     * target resets to the new preset's [defaultTargetFor] rather than carrying over whatever the
     * previous preset's stepper landed on. Onboarding never exposes a quit-mode or limit-metric
     * picker of its own (7g's QUIT pill only ever builds a TOTAL/abstinence habit — see [finish]),
     * so those two arguments are always [QuitMode.TOTAL]/[Metric.DURATION], same as the
     * [HabitFormState] [finish] itself builds.
     *
     * Without this reset, a target picked up under one preset can survive into a preset it's
     * illegal for — e.g. bumping QUANTITY's target to 15 then switching to WEEKLY_TIMES (whose own
     * form caps at 7 days/week) would otherwise carry that 15 straight into [finish]'s
     * [HabitFormState], producing a habit the real form can never create.
     */
    fun setHabitKind(kind: HabitPreset) =
        state.update {
            it.copy(habitKind = kind, habitTarget = defaultTargetFor(kind, QuitMode.TOTAL, Metric.DURATION))
        }

    fun setHabitTarget(value: Int) = state.update { it.copy(habitTarget = value.coerceAtLeast(1)) }

    /** Persists immediately, not just on [finish] — the celebration bubble (7f) speaks with the chosen voice right away. */
    fun setPersonality(personality: Personality) {
        state.update { it.copy(personality = personality) }
        viewModelScope.launch { settings.update { it.copy(personality = personality) } }
    }

    /**
     * Saves the name/personality and flips onboardingDone, then — only if [OnboardingUiState.habitName]
     * isn't blank — creates that first habit through the same path
     * [com.alvarotc.bito.ui.habitform.HabitFormViewModel.save] uses, and reconciles. A blank habit
     * name still completes onboarding; it just creates nothing (controller ruling: nothing was
     * written, so there is nothing to reconcile).
     *
     * Belt-and-braces for the NAME step's blank-name gate (the screen disables "Seguir" and the
     * pager's swipe on a blank field, but this is the last line of defense against anything that
     * still reaches `finish()` with [OnboardingUiState.name] blank): a blank trimmed name never
     * overwrites [com.alvarotc.bito.data.settings.Settings.userName] — whatever was already stored
     * (default `""`, same "campeón"/"champ" fallback everywhere else) is left as-is. Onboarding
     * still completes either way.
     *
     * Guards on `busy` OR `done`, and never clears `busy` on the happy path — same reasoning as
     * [com.alvarotc.bito.ui.habitform.HabitFormViewModel.save]'s own guard comment: a second call
     * landing during the nav-away transition must not create a second habit with a fresh UUID.
     */
    fun finish() {
        val current = state.value
        if (current.busy || current.done) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val today = LogicalDays.logicalDayOf(now(), settings.settings.first().dayCutoffMinutes, zone())
            val trimmedName = current.name.trim()
            settings.update { stored ->
                stored.copy(
                    userName = if (trimmedName.isNotEmpty()) trimmedName else stored.userName,
                    personality = current.personality,
                    onboardingDone = true,
                )
            }

            val habitName = current.habitName.trim()
            if (habitName.isNotBlank()) {
                val sortOrder = (habits.observeHabits().first().maxOfOrNull { it.sortOrder } ?: -1) + 1
                val form = HabitFormState(name = habitName, preset = current.habitKind, target = current.habitTarget)
                habits.create(form.toNewEntity(UUID.randomUUID().toString(), today, now(), sortOrder))
                reconciler.reconcile(today, now())
            }

            state.update { it.copy(done = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { OnboardingViewModel(container.settings, container.habits, container.reconciler) }
            }
    }
}
