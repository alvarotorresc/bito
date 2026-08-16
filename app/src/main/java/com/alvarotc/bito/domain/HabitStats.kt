package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay

/**
 * A habit's compliance over a rolling window: how many of its judged periods
 * ([fulfilled]) out of how many were decided at all ([judged]).
 */
data class WindowStats(val fulfilled: Int, val judged: Int) {
    /** Null when nothing in the window was judged yet ("—" in the UI). */
    val percent: Int? get() = if (judged == 0) null else fulfilled * 100 / judged
}

/** Compliance rolled up over a fixed window of days, for the Detail/Stats screens. */
object HabitStats {
    /**
     * [habit]'s periods whose key falls between the period containing
     * `today - windowDays` and the period containing [today], inclusive.
     * FULFILLED/FAILED judge; PENDING/PAUSED/NOT_APPLICABLE do not. For
     * WEEK/MONTH habits a period counts once — matching [Streaks], the window
     * is walked in the habit's own period unit, not in raw days — so the
     * period containing [today] (still open) only judges when it is already
     * FULFILLED, since an open period is never FAILED.
     *
     * [windowDays] is the reach back from [today], both ends inclusive: for a
     * DAY-period habit the window spans `windowDays + 1` days (e.g. 8 days
     * for `windowDays = 7`), not [windowDays] days.
     */
    fun windowStats(
        state: DomainState,
        habit: Habit,
        today: LogicalDay,
        windowDays: Int,
    ): WindowStats {
        val firstKey = LogicalDays.periodKeyOf(today - windowDays, habit.period)
        val lastKey = LogicalDays.periodKeyOf(today, habit.period)
        var fulfilled = 0
        var judged = 0
        for (periodKey in firstKey..lastKey) {
            when (Compliance.complianceOf(state, habit, periodKey, today)) {
                ComplianceStatus.FULFILLED -> {
                    fulfilled++
                    judged++
                }

                ComplianceStatus.FAILED -> judged++
                else -> Unit
            }
        }
        return WindowStats(fulfilled, judged)
    }
}
