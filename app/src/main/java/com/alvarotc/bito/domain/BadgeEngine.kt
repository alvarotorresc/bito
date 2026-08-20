package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Badges (docs/06). History justifies a set of ids; the data layer appends the missing ones and never removes. */
object BadgeEngine {
    private val streakBadges = mapOf(7 to "streak-7", 30 to "streak-30", 100 to "streak-100", 365 to "streak-365")
    private val perfectDayBadges = mapOf(1 to "perfect-day-1", 10 to "perfect-days-10", 50 to "perfect-days-50", 100 to "perfect-days-100")
    private const val ACTIVITY_RUN_DAYS = 7
    private const val RESURRECTION_MIN_RUN = 30

    /** Every badge history justifies up to [today] (docs/06 §3). Pure, idempotent. */
    fun earnedBadges(
        state: DomainState,
        today: LogicalDay,
    ): Set<String> {
        val earned = mutableSetOf<String>()
        // Rachas — any habit, in its own period unit (same source as points milestones and store exclusives).
        state.habits
            .flatMap { Streaks.reachedMilestones(state, it, today, streakBadges.keys) }
            .mapTo(earned) { streakBadges.getValue(it) }
        // Constancia — perfect days (rule E6) and full perfect weeks/months.
        val perfectDays = PerfectDays.perfectDaysUpTo(state, today)
        perfectDayBadges.filterKeys { perfectDays.size >= it }.values.forEach { earned += it }
        val firstDay = state.habits.minOfOrNull { it.createdOnDay }
        if (firstDay != null && firstDay <= today) {
            if (PerfectDays.perfectPeriodKeys(perfectDays, Period.WEEK, firstDay, today).isNotEmpty()) earned += "perfect-week"
            if (PerfectDays.perfectPeriodKeys(perfectDays, Period.MONTH, firstDay, today).isNotEmpty()) earned += "perfect-month"
        }
        // Momentos.
        if (state.habits.isNotEmpty()) earned += "first-habit"
        if (activityRunReached(state, today, ACTIVITY_RUN_DAYS)) earned += "first-week"
        if (state.habits.any { Streaks.hasResurrected(state, it, today, RESURRECTION_MIN_RUN) }) earned += "resurrection"
        if (state.freezerUses.isNotEmpty()) earned += "first-freezer"
        return earned
    }

    /** Append-only: never proposes revoking an unlocked badge. */
    fun missingBadges(
        earned: Set<String>,
        unlocked: Set<String>,
    ): Set<String> = earned - unlocked

    /** 7 consecutive logical days ≤ today each with an entry or a seal (same activity notion as MoodEngine). */
    internal fun activityRunReached(
        state: DomainState,
        today: LogicalDay,
        length: Int,
    ): Boolean {
        val activeDays =
            (state.entries.asSequence().map { it.logicalDay } + state.daySeals.asSequence().map { it.logicalDay })
                .filter { it <= today }
                .toSortedSet()
        var run = 0
        var previous: LogicalDay? = null
        for (day in activeDays) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            if (run >= length) return true
            previous = day
        }
        return false
    }
}
