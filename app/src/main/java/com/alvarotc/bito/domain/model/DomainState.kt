package com.alvarotc.bito.domain.model

/**
 * Immutable snapshot of everything the engine needs. The engine never talks to
 * storage or the clock: callers load state, pass `today`/`now` explicitly, and
 * get deterministic results. Nothing derived (streaks, stats, mood, balance)
 * is ever stored — it is always recomputed from this state.
 */
data class DomainState(
    val habits: List<Habit> = emptyList(),
    val targetChanges: List<TargetChange> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val daySeals: List<DaySeal> = emptyList(),
    val pauseIntervals: List<PauseInterval> = emptyList(),
    val freezerUses: List<FreezerUse> = emptyList(),
    val pointsLedger: List<PointsLedgerEntry> = emptyList(),
)
