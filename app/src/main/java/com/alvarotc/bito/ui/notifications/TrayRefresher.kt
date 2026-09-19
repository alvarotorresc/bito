package com.alvarotc.bito.ui.notifications

import android.app.NotificationManager
import android.content.Context
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.logicalClockAt
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Recomputes the GLOBAL reminder tray from live state after any write, in-app or not, and never
 * conjures a notification that wasn't already there: a pending payload only refreshes a tray
 * that's already showing, and an empty one is always cancelled. Shared by [QuickActionReceiver]
 * (which used to inline this) and [com.alvarotc.bito.ui.widget.WidgetRefresher]'s collector, so
 * an in-app log or edit keeps the tray honest the same way a quick-action tap already did.
 */
object TrayRefresher {
    /** Call-site contract: every caller already holds an [AppContainer]. */
    suspend fun refresh(
        context: Context,
        container: AppContainer,
        treatAsActive: Boolean = false,
    ) = refresh(context, container.settings, container.habits, container.domainState, container.rewards, treatAsActive)

    /**
     * The testable seam behind [refresh]: takes repos directly instead of a whole [AppContainer]
     * so tests don't need a real app-wide container (and its file-backed DataStore) just to
     * exercise this logic.
     *
     * [treatAsActive] preserves [QuickActionReceiver]'s original rule: an action tapped on the
     * GLOBAL tray's own notification refreshes it even if the system no longer lists it as
     * active (action buttons, unlike the content tap, don't auto-cancel).
     *
     * [minutesOfDay] picks the re-post's [ReminderFlavor] — wall-clock "now" by default, since a
     * refresh happens at the moment of the write, not at a scheduled hour. Injectable so tests
     * don't depend on when they run. `null` (the default) derives it from the SAME
     * [logicalClockAt] read that resolves [today][com.alvarotc.bito.domain.LogicalClock.today] —
     * a second, separate wall-clock read here could straddle midnight against that one, the
     * exact drift [logicalClockAt] exists to rule out.
     */
    internal suspend fun refresh(
        context: Context,
        settings: SettingsRepository,
        habits: HabitsRepository,
        domainState: DomainStateRepository,
        rewards: RewardsRepository,
        treatAsActive: Boolean = false,
        minutesOfDay: Int? = null,
    ) {
        val prefs = settings.settings.first()
        val entities = habits.observeHabits().first()
        val owned = rewards.observeOwnedItems().first()
        val clock = logicalClockAt(System.currentTimeMillis(), prefs.dayCutoffMinutes, ZoneId.systemDefault())
        val flavorMinutesOfDay = minutesOfDay ?: clock.minutesOfDay
        val state =
            buildTodayUiState(
                domainState.snapshot(),
                entities.associate { it.id to it.sortOrder },
                clock.today,
                prefs.personality,
                owned,
                prefs.userName,
                minutesOfDay = flavorMinutesOfDay,
                reviewTimeMinutes = prefs.reviewTimeMinutes,
                eyesPainted = prefs.habiEyesPainted,
            )
        if (!reviewIsPending(state)) Notifier.cancelReview(context)
        val payload = buildReminderPayload(state)
        if (payload == null) {
            Notifier.cancelReminder(context)
        } else if (treatAsActive || trayIsActive(context)) {
            Notifier.showReminder(context, payload, prefs.personality, prefs.userName, flavorMinutesOfDay, state.spec)
        }
    }

    /** Whether the GLOBAL tray is currently showing — never conjured, only refreshed or killed. */
    private fun trayIsActive(context: Context): Boolean =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .any { it.id == Notifier.REMINDER_ID }
}
