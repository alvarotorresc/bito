package com.alvarotc.bito.ui.onboarding

import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One-shot start hook (M9.5 final-review flag, `⚑CALL`): a v1/v2 backup restored onto a fresh
 * install writes habits straight into Room through
 * [com.alvarotc.bito.data.backup.BackupRepository.import], which replaces
 * [com.alvarotc.bito.data.settings.Settings] wholesale with whatever the backup carries — and every
 * backup predating this milestone never carried `onboardingDone` at all, so the restored value is
 * always the DataStore default, `false`. Left alone, that would send someone who just restored real
 * history straight into onboarding on their very next launch
 * ([com.alvarotc.bito.ui.BitoNavHost]'s start-destination gate reads `onboardingDone` once, at
 * startup, off the very [Settings] this seeds). Their `userName` already travels in the backup
 * since v1, so nothing about their identity is lost by skipping the flow — only the flow itself.
 *
 * [start] is launched from [com.alvarotc.bito.AppStartup.start], under that object's own one-shot
 * `started` guard — same idempotence contract the three continuous collectors it starts alongside
 * rely on, so this never double-fires on a second `AppStartup.start` call either. Unlike those
 * three, this isn't a standing collector: [reconcile] runs its one check and completes, it doesn't
 * keep observing. [reconcile] is `internal`, not `private`, purely so [OnboardingReconcilerTest]
 * (same module's `src/test`, which — like [com.alvarotc.bito.AppStartup]'s own `internal` test
 * seams — can see `internal` declarations from `src/main`) can drive it directly against a plain
 * [SettingsRepository]/[HabitsRepository] pair instead of a full [AppContainer].
 *
 * Best-effort against the FIRST frame, not a synchronous gate: [start]'s coroutine races
 * [com.alvarotc.bito.ui.BitoNavHost]'s own settings read, so losing that race just means a
 * restored-data user sees onboarding once — strictly better than the pre-fix behavior of always
 * seeing it.
 *
 * A restore mid-session (an already-running app, not a cold start) resets `onboardingDone` via
 * [com.alvarotc.bito.data.backup.BackupRepository.import]'s wholesale [Settings] replacement, and
 * this reconciler — a startup-only hook — does not re-run to correct it until the next cold start;
 * no in-session harm, since the language reconciles immediately
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

    fun start(
        container: AppContainer,
        scope: CoroutineScope,
    ) {
        scope.launch { reconcile(container.settings, container.habits) }
    }
}
