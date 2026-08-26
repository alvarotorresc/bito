package com.alvarotc.bito.ui.onboarding

import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Deterministic reconcile hook (M9.5 final-review flag, `⚑CALL`): a v1/v2 backup restored onto a
 * fresh install writes habits straight into Room through
 * [com.alvarotc.bito.data.backup.BackupRepository.import], which replaces
 * [com.alvarotc.bito.data.settings.Settings] wholesale with whatever the backup carries — and every
 * backup predating this milestone never carried `onboardingDone` at all, so the restored value is
 * always the DataStore default, `false`. Left alone, that would send someone who just restored real
 * history straight into onboarding on their very next launch
 * ([com.alvarotc.bito.ui.BitoNavHost]'s start-destination gate reads `onboardingDone` once, at
 * startup, off the very [Settings] this seeds). Their `userName` already travels in the backup
 * since v1, so nothing about their identity is lost by skipping the flow — only the flow itself.
 *
 * [reconcile] is called directly, as a suspend function, from
 * [com.alvarotc.bito.ui.BitoNavHost]'s own first-frame gate — BEFORE that gate starts collecting
 * [Settings] and BEFORE `startDestination` is decided from the first value it sees. That ordering
 * (a plain suspend call the gate awaits, not a fire-and-forget coroutine racing it) is what makes
 * this deterministic: a v1/v2 restorer's seeded `onboardingDone = true` is committed to DataStore
 * before anything reads it for the start-destination decision — no more "loses the race, restored
 * user sees onboarding once" as a documented acceptable outcome. [reconcile] is `internal`, not
 * `private`, so both [com.alvarotc.bito.ui.BitoNavHost] (a different package, same module) and
 * [OnboardingReconcilerTest] (same module's `src/test`, which — like
 * [com.alvarotc.bito.AppStartup]'s own `internal` test seams — can see `internal` declarations
 * from `src/main`) can reach it directly against a plain [SettingsRepository]/[HabitsRepository]
 * pair instead of a full [com.alvarotc.bito.AppContainer].
 *
 * A restore mid-session (an already-running app, not a cold start) resets `onboardingDone` via
 * [com.alvarotc.bito.data.backup.BackupRepository.import]'s wholesale [Settings] replacement.
 * [reconcile] only ever runs from [com.alvarotc.bito.ui.BitoNavHost]'s own first-frame gate, so a
 * mid-session restore does not get corrected by it until that gate runs again — in practice, the
 * next cold start. No in-session harm: the language reconciles immediately
 * ([com.alvarotc.bito.ui.settings.BackupViewModel.confirmImport] applies it right away) while only
 * the onboarding flag waits for the next launch.
 */
object OnboardingReconciler {
    internal suspend fun reconcile(
        settings: SettingsRepository,
        habits: HabitsRepository,
    ) {
        // Short-circuits on the settings read alone for the overwhelmingly common case (onboarding
        // already done) — the habits query only runs when it's actually relevant, not on every
        // ordinary launch.
        if (!settings.settings.first().onboardingDone && habits.observeHabits().first().isNotEmpty()) {
            settings.update { it.copy(onboardingDone = true) }
        }
    }
}
