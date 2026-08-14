package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood

/** Habi's mood — a pure function of recent compliance. */
object MoodEngine {
    /** Days without a single entry or seal that turn Habi dramatic. */
    private const val DRAMATIC_SILENCE_DAYS = 3

    /** Length of the closed window the ratio looks at. */
    private const val WINDOW_DAYS = 7

    private const val RADIANT_RATIO = 0.8
    private const val WILTED_RATIO = 0.5

    /**
     * Mood on [today]:
     *
     * 1. DRAMATIC when [lastActivityDay] (the logical day of the most recent
     *    entry or seal, null if none ever) is 3+ days behind [today]
     *    [DECIDED 2026-08-14]. Null never triggers drama — the ratio below
     *    decides (a genuinely fresh state has no decided periods -> NORMAL).
     * 2. Otherwise, compliance ratio over the closed 7-day window
     *    [today-7, today-1]: FULFILLED / (FULFILLED + FAILED) habit-periods.
     *    DAY-period habits contribute each requirable day; WEEK/MONTH habits
     *    contribute once, on windows containing their period's last day.
     *    PENDING and PAUSED periods stay out of both sides of the ratio.
     * 3. ratio > 0.8 -> RADIANT; ratio < 0.5 -> WILTED; otherwise NORMAL.
     *    No decided periods in the window -> NORMAL.
     */
    fun moodOf(
        state: DomainState,
        today: LogicalDay,
        lastActivityDay: LogicalDay?,
    ): Mood {
        if (lastActivityDay != null && today - lastActivityDay >= DRAMATIC_SILENCE_DAYS) return Mood.DRAMATIC

        val window = (today - WINDOW_DAYS)..(today - 1)
        var fulfilled = 0
        var decided = 0
        for (habit in state.habits) {
            val firstKey = LogicalDays.periodKeyOf(window.first, habit.period)
            val lastKey = LogicalDays.periodKeyOf(window.last, habit.period)
            for (periodKey in firstKey..lastKey) {
                // A period weighs on the window that contains its close, so a week counts once.
                if (LogicalDays.daysOf(periodKey, habit.period).last !in window) continue
                when (Compliance.complianceOf(state, habit, periodKey, today)) {
                    ComplianceStatus.FULFILLED -> {
                        fulfilled++
                        decided++
                    }

                    ComplianceStatus.FAILED -> decided++
                    else -> Unit
                }
            }
        }
        if (decided == 0) return Mood.NORMAL

        val ratio = fulfilled.toDouble() / decided
        return when {
            ratio > RADIANT_RATIO -> Mood.RADIANT
            ratio < WILTED_RATIO -> Mood.WILTED
            else -> Mood.NORMAL
        }
    }
}
