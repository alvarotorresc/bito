package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Whether a spent freezer can protect one day of one habit. */
enum class FreezerEligibility { ELIGIBLE, NOT_DAILY, NOT_FAILED, ALREADY_PROTECTED, FUTURE_DAY }

/**
 * A freezer only protects days FAILED past of DAY-period habits
 * ([Streaks.isFreezerProtected] only ever looks at [Period.DAY] habits).
 * Inventory (owning one to spend) is checked separately via
 * [PointsEngine.freezersOwned].
 */
object FreezerEngine {
    fun eligibilityOf(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
        today: LogicalDay,
    ): FreezerEligibility =
        when {
            habit.period != Period.DAY -> FreezerEligibility.NOT_DAILY
            day >= today -> FreezerEligibility.FUTURE_DAY
            state.freezerUses.any { it.habitId == habit.id && it.protectedDay == day } ->
                FreezerEligibility.ALREADY_PROTECTED
            Compliance.complianceOf(state, habit, day, today) != ComplianceStatus.FAILED ->
                FreezerEligibility.NOT_FAILED
            else -> FreezerEligibility.ELIGIBLE
        }
}
