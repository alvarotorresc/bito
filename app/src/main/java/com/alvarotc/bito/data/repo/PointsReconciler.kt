package com.alvarotc.bito.data.repo

import com.alvarotc.bito.domain.BadgeEngine
import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsReason

/**
 * What a [PointsReconciler.reconcile] pass granted: nothing here was owned
 * before the call, so callers can react to it (e.g. celebrate a badge) without
 * re-deriving state themselves.
 */
data class ReconcileResult(
    val newEvents: List<PointsEvent> = emptyList(),
    val newItems: Set<String> = emptySet(),
    val newBadges: Set<String> = emptySet(),
) {
    /** Whether this pass just granted the perfect-day points event for [day]. */
    fun reachedPerfectDay(day: LogicalDay): Boolean = newEvents.any { it.reason == PointsReason.PERFECT_DAY && it.refId == "day:$day" }
}

/**
 * Appends engine-derived grants the ledger and the inventory are missing:
 * points, streak exclusives and badges alike. All three are append-only
 * (never confiscated) and idempotent, so callers run this after every write —
 * points via the ledger's unique (reason, refId) index, exclusives and
 * badges via an insert-ignore keyed on their id.
 */
class PointsReconciler(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val economy: EconomyConfig = EconomyConfig(),
) {
    suspend fun reconcile(
        today: LogicalDay,
        nowMillis: Long,
    ): ReconcileResult {
        val state = domainState.snapshot()
        val missing = PointsEngine.missingEvents(PointsEngine.earnedEvents(state, today, economy), state.pointsLedger)
        if (missing.isNotEmpty()) rewards.append(missing, nowMillis)

        val earned = HabiEngine.earnedExclusives(state, today)
        val missingItems = HabiEngine.missingExclusives(earned, rewards.ownedItemIds())
        if (missingItems.isNotEmpty()) rewards.grantItems(missingItems, nowMillis)

        val missingBadges = BadgeEngine.missingBadges(BadgeEngine.earnedBadges(state, today), rewards.unlockedBadgeIds())
        if (missingBadges.isNotEmpty()) rewards.unlockBadges(missingBadges, nowMillis)

        return ReconcileResult(newEvents = missing, newItems = missingItems, newBadges = missingBadges)
    }
}
