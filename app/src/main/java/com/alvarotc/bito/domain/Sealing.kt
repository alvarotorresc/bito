package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay

/** Day sealing: closing days and batch-seal offers after days away. */
object Sealing {
    /** Whether [day] has a DaySeal. */
    fun isSealed(
        state: DomainState,
        day: LogicalDay,
    ): Boolean = state.daySeals.any { it.logicalDay == day }

    /**
     * Days strictly before [today] that still need sealing, oldest first:
     * unsealed days on which at least one ZERO or AT_MOST habit was requirable
     * (today itself is sealed by the nightly review, not offered here).
     * This feeds the batch-seal sheet ("These 3 days — did you stay clean?").
     * No artificial cap (rule E8): after N days away, N days are offered.
     */
    fun pendingSealDays(
        state: DomainState,
        today: LogicalDay,
    ): List<LogicalDay> {
        val sealable = state.habits.filter { it.direction == Direction.ZERO || it.direction == Direction.AT_MOST }
        if (sealable.isEmpty()) return emptyList()
        // Nothing before the oldest of those habits can ever need a seal.
        val firstDay = sealable.minOf { it.createdOnDay }
        return (firstDay until today).filter { day ->
            !isSealed(state, day) && sealable.any { Compliance.isRequirableOn(state, it, day) }
        }
    }
}
