package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.TargetChange

/** Target history resolution (rule E2). */
object Targets {
    /**
     * The target in force for [habit] on [day]: the [TargetChange] with the
     * greatest effectiveFromDay <= day. Falls back to [Habit.target] when the
     * habit has no change on or before [day].
     */
    fun targetOn(
        habit: Habit,
        targetChanges: List<TargetChange>,
        day: LogicalDay,
    ): Int {
        val inForce =
            targetChanges
                .filter { it.habitId == habit.id && it.effectiveFromDay <= day }
                // Stable sort: when two changes share a day, the last one recorded wins.
                .sortedBy { it.effectiveFromDay }
                .lastOrNull()
        return inForce?.target ?: habit.target
    }
}
