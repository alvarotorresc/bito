package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Perfect day / week / month evaluation (rule E6). */
object PerfectDays {
    /**
     * A day is perfect when it has at least one demandable DAY-period habit
     * (daily positives, limits and abstinences — active, alive, not paused)
     * and every one of them is FULFILLED for that day. Weekly/monthly habits
     * never penalize single days: they are judged at their period's close
     * (rule E6). A day with no demandable daily habit is NOT perfect.
     */
    fun isPerfectDay(
        state: DomainState,
        day: LogicalDay,
        today: LogicalDay,
    ): Boolean {
        val demandable =
            state.habits.filter {
                it.period == Period.DAY && Compliance.isRequirableOn(state, it, day)
            }
        if (demandable.isEmpty()) return false
        val periodKey = LogicalDays.periodKeyOf(day, Period.DAY)
        return demandable.all {
            Compliance.complianceOf(state, it, periodKey, today) == ComplianceStatus.FULFILLED
        }
    }

    /** Every perfect day from the first habit's creation up to [today]. */
    fun perfectDaysUpTo(
        state: DomainState,
        today: LogicalDay,
    ): Set<LogicalDay> {
        val firstDay = state.habits.minOfOrNull { it.createdOnDay } ?: return emptySet()
        if (firstDay > today) return emptySet()
        return (firstDay..today).filterTo(mutableSetOf()) { isPerfectDay(state, it, today) }
    }

    /** Period keys (ISO weeks / calendar months) between [firstDay] and [today] whose EVERY day is in [perfectDays]. */
    fun perfectPeriodKeys(
        perfectDays: Set<LogicalDay>,
        period: Period,
        firstDay: LogicalDay,
        today: LogicalDay,
    ): List<Int> {
        val firstKey = LogicalDays.periodKeyOf(firstDay, period)
        val lastKey = LogicalDays.periodKeyOf(today, period)
        return (firstKey..lastKey).filter { key -> LogicalDays.daysOf(key, period).all { it in perfectDays } }
    }
}
