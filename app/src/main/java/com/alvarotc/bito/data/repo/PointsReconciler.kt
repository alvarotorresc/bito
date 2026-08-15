package com.alvarotc.bito.data.repo

import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.LogicalDay

/**
 * Appends engine-derived grants the ledger is missing. Append-only (points
 * are never confiscated) and idempotent: the unique (reason, refId) index
 * makes a re-append a no-op, so callers run it after every write.
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
    }
}
