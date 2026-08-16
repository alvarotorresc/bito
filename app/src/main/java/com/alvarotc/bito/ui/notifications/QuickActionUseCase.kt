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
    /** Logs [amount] for [habitId] and returns the recalculated reminder payload, or `null` when nothing pending remains. */
    suspend fun log(
        habitId: String,
        amount: Int,
    ): ReminderPayload? {
        val prefs = settings.settings.first()
        val nowMillis = now()
        val today = LogicalDays.logicalDayOf(nowMillis, prefs.dayCutoffMinutes, zone())
        journal.log(EntryEntity(UUID.randomUUID().toString(), habitId, today, amount, nowMillis))
        reconciler.reconcile(today, nowMillis)
        val entities = habits.observeHabits().first()
        val state = buildTodayUiState(domainState.snapshot(), entities.associate { it.id to it.sortOrder }, today)
        return buildReminderPayload(state)
    }
}
