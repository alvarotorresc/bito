package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Badges (docs/06). History justifies a set of ids; the data layer appends the missing ones and never removes. */
object BadgeEngine {
    // internal (not private): BadgeEngineTest's `allEmittableIds` extension property below reads
    // these same maps/ids, so the "every id the engine can emit is in the catalog" assertion is
    // load-bearing against the real source of truth instead of a hand-typed duplicate list.
    internal val streakBadges = mapOf(7 to "streak-7", 30 to "streak-30", 100 to "streak-100", 365 to "streak-365")
    internal val perfectDayBadges = mapOf(1 to "perfect-day-1", 10 to "perfect-days-10", 50 to "perfect-days-50", 100 to "perfect-days-100")
    internal const val PERFECT_WEEK = "perfect-week"
    internal const val PERFECT_MONTH = "perfect-month"
    internal const val FIRST_HABIT = "first-habit"
    internal const val FIRST_WEEK = "first-week"
    internal const val RESURRECTION = "resurrection"
    internal const val FIRST_FREEZER = "first-freezer"
    private const val ACTIVITY_RUN_DAYS = 7
    private const val RESURRECTION_MIN_RUN = 30

    /** Every badge history justifies up to [today] (docs/06 §3). Pure, idempotent. */
    fun earnedBadges(
        state: DomainState,
        today: LogicalDay,
        perfectDays: Set<LogicalDay> = PerfectDays.perfectDaysUpTo(state, today),
    ): Set<String> {
        val earned = mutableSetOf<String>()
        // Rachas — any habit, in its own period unit (same source as points milestones and store exclusives).
        state.habits
            .flatMap { Streaks.reachedMilestones(state, it, today, streakBadges.keys) }
            .mapTo(earned) { streakBadges.getValue(it) }
        // Constancia — perfect days (rule E6) and full perfect weeks/months.
        perfectDayBadges.filterKeys { perfectDays.size >= it }.values.forEach { earned += it }
        val firstDay = state.habits.minOfOrNull { it.createdOnDay }
        if (firstDay != null && firstDay <= today) {
            if (PerfectDays.perfectPeriodKeys(perfectDays, Period.WEEK, firstDay, today).isNotEmpty()) earned += PERFECT_WEEK
            if (PerfectDays.perfectPeriodKeys(perfectDays, Period.MONTH, firstDay, today).isNotEmpty()) earned += PERFECT_MONTH
        }
        // Momentos.
        if (state.habits.isNotEmpty()) earned += FIRST_HABIT
        if (activityRunReached(state, today, ACTIVITY_RUN_DAYS)) earned += FIRST_WEEK
        if (state.habits.any { Streaks.hasResurrected(state, it, today, RESURRECTION_MIN_RUN) }) earned += RESURRECTION
        if (state.freezerUses.isNotEmpty()) earned += FIRST_FREEZER
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

/**
 * Every badge id [BadgeEngine.earnedBadges] can ever add to its returned set — the streak and
 * perfect-day thresholds plus the six literal "moment" ids, read straight off the same maps and
 * constants [BadgeEngine.earnedBadges] itself emits from. Lets a test assert it 1:1 against
 * [com.alvarotc.bito.domain.model.BadgeCatalog].
 */
internal val BadgeEngine.allEmittableIds: Set<String>
    get() =
        streakBadges.values.toSet() + perfectDayBadges.values.toSet() +
            setOf(
                BadgeEngine.PERFECT_WEEK,
                BadgeEngine.PERFECT_MONTH,
                BadgeEngine.FIRST_HABIT,
                BadgeEngine.FIRST_WEEK,
                BadgeEngine.RESURRECTION,
                BadgeEngine.FIRST_FREEZER,
            )
