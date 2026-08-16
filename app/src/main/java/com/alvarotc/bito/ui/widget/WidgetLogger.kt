package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.UUID

/**
 * Logs a habit entry and reconciles points from outside the app's UI
 * (the Glance widget's tap action). Mirrors [com.alvarotc.bito.ui.today.TodayViewModel]'s
 * write path: a UUID-identified entry on the logical day resolved from the
 * current settings cutoff, followed by an immediate reconcile.
 */
class WidgetLogger(
    private val journal: JournalRepository,
    private val reconciler: PointsReconciler,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun log(
        habitId: String,
        amount: Int,
    ) {
        val prefs = settings.settings.first()
        val nowMillis = now()
        val today = LogicalDays.logicalDayOf(nowMillis, prefs.dayCutoffMinutes, zone())
        journal.log(EntryEntity(UUID.randomUUID().toString(), habitId, today, amount, nowMillis))
        reconciler.reconcile(today, nowMillis)
    }
}
