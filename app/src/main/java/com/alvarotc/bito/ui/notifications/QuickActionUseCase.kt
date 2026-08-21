package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID

/**
 * What a [QuickActionUseCase.log] call produced: the recalculated reminder payload (or `null`
 * when nothing pending remains) and whether this write is the one that just made today perfect —
 * the signal the out-of-app perfect-day notification gates on (T13's
 * [com.alvarotc.bito.ui.celebration.CelebrationGate]).
 */
data class QuickActionResult(val payload: ReminderPayload?, val perfectDayReached: Boolean)

/**
 * Logs a habit straight from a notification's quick-action button — no app launch, tab switch,
 * or screen render involved. Writes the entry on the correct logical day (settings cutoff +
 * zone, same math as [TodayViewModel]), reconciles points right after (idempotent append, same
 * contract every other write honors), then recomputes the reminder payload from that FRESH
 * state: honest by construction, never a stale copy of what was pending before this action.
 */
class QuickActionUseCase(
    private val journal: JournalRepository,
    private val reconciler: PointsReconciler,
    private val domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** Logs [amount] for [habitId] and returns the recalculated reminder payload plus whether this write reached today's perfect day. */
    suspend fun log(
        habitId: String,
        amount: Int,
    ): QuickActionResult {
        val prefs = settings.settings.first()
        val nowMillis = now()
        val today = LogicalDays.logicalDayOf(nowMillis, prefs.dayCutoffMinutes, zone())
        journal.log(EntryEntity(UUID.randomUUID().toString(), habitId, today, amount, nowMillis))
        val result = reconciler.reconcile(today, nowMillis)
        val entities = habits.observeHabits().first()
        val state = buildTodayUiState(domainState.snapshot(), entities.associate { it.id to it.sortOrder }, today)
        return QuickActionResult(buildReminderPayload(state), result.reachedPerfectDay(today))
    }
}
