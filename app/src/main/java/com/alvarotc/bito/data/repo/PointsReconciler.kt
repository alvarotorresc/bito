package com.alvarotc.bito.data.repo

import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.LogicalDay

/**
 * Appends engine-derived grants the ledger and the inventory are missing:
 * points and streak exclusives alike. Both are append-only (never
 * confiscated) and idempotent, so callers run this after every write —
 * points via the ledger's unique (reason, refId) index, exclusives via
 * an insert-ignore keyed on the item id.
 */
class PointsReconciler(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val economy: EconomyConfig = EconomyConfig(),
) {
    suspend fun reconcile(
        today: LogicalDay,
        nowMillis: Long,
    ) {
        val state = domainState.snapshot()
        val missing = PointsEngine.missingEvents(PointsEngine.earnedEvents(state, today, economy), state.pointsLedger)
        if (missing.isNotEmpty()) rewards.append(missing, nowMillis)

        val earned = HabiEngine.earnedExclusives(state, today)
        val missingItems = HabiEngine.missingExclusives(earned, rewards.ownedItemIds())
        if (missingItems.isNotEmpty()) rewards.grantItems(missingItems, nowMillis)
    }
}
