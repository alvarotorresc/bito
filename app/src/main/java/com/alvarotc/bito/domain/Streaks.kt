package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Current and best streak, measured in the habit's period unit (rule E1). */
data class StreakResult(val current: Int, val best: Int)

/** What a single period does to a streak run. */
private enum class PeriodOutcome { COUNTS, BRIDGES, BREAKS, ENDS_WALK }

object Streaks {
    /**
     * Streaks are strict: a FAILED period breaks them. Anti-sergeant valves:
     *
     * - Retroactive entries repair the past: everything is recomputed here,
     *   nothing is stored ("it just works").
     * - PAUSED periods bridge: they neither break nor extend the streak.
     * - A used freezer on a FAILED day (DAY-period habits only) bridges too.
     * - PENDING periods bridge: the current period is still winnable, and past
     *   unsealed ZERO/AT_MOST periods await batch sealing — silence neither
     *   inflates nor breaks.
     * - NOT_APPLICABLE periods (before creation / after archive) end the walk.
     *
     * [StreakResult.current] walks back from the period containing [today];
     * [StreakResult.best] is the best FULFILLED run (with the same bridges)
     * over the habit's whole life.
     */
    fun streaksOf(
        state: DomainState,
        habit: Habit,
        today: LogicalDay,
    ): StreakResult =
        StreakResult(
            current = currentStreak(state, habit, today),
            best = bestStreak(state, habit, today),
        )

    /**
     * Every streak-milestone length from [milestones] that some run in the
     * habit's history has ever reached, e.g. reaching a 30-run reports [7, 30].
     * Used for STREAK_MILESTONE grants: once granted, never confiscated, even
     * if the streak later breaks.
     */
    fun reachedMilestones(
        state: DomainState,
        habit: Habit,
        today: LogicalDay,
        milestones: Set<Int>,
    ): Set<Int> {
        // A run of length N passed through every shorter milestone on its way, so the longest
        // run ever is enough to know which lengths history has reached.
        val longestRun = bestStreak(state, habit, today)
        return milestones.filter { it <= longestRun }.toSet()
    }

    private fun currentStreak(
        state: DomainState,
        habit: Habit,
        today: LogicalDay,
    ): Int {
        val firstKey = LogicalDays.periodKeyOf(habit.createdOnDay, habit.period)
        var key = LogicalDays.periodKeyOf(today, habit.period)
        var current = 0
        while (key >= firstKey) {
            when (outcomeOf(state, habit, key, today)) {
                PeriodOutcome.COUNTS -> current++
                PeriodOutcome.BRIDGES -> Unit
                PeriodOutcome.BREAKS, PeriodOutcome.ENDS_WALK -> return current
            }
            key--
        }
        return current
    }

    private fun bestStreak(
        state: DomainState,
        habit: Habit,
        today: LogicalDay,
    ): Int {
        val firstKey = LogicalDays.periodKeyOf(habit.createdOnDay, habit.period)
        val lastKey = LogicalDays.periodKeyOf(today, habit.period)
        var run = 0
        var best = 0
        for (key in firstKey..lastKey) {
            when (outcomeOf(state, habit, key, today)) {
                PeriodOutcome.COUNTS -> {
                    run++
                    if (run > best) best = run
                }

                PeriodOutcome.BRIDGES, PeriodOutcome.ENDS_WALK -> Unit
                PeriodOutcome.BREAKS -> run = 0
            }
        }
        return best
    }

    private fun outcomeOf(
        state: DomainState,
        habit: Habit,
        periodKey: Int,
        today: LogicalDay,
    ): PeriodOutcome =
        when (Compliance.complianceOf(state, habit, periodKey, today)) {
            ComplianceStatus.FULFILLED -> PeriodOutcome.COUNTS
            ComplianceStatus.PAUSED, ComplianceStatus.PENDING -> PeriodOutcome.BRIDGES
            ComplianceStatus.FAILED ->
                if (isFreezerProtected(state, habit, periodKey)) PeriodOutcome.BRIDGES else PeriodOutcome.BREAKS

            ComplianceStatus.NOT_APPLICABLE -> PeriodOutcome.ENDS_WALK
        }

    /** Freezers protect one habit on one concrete day, so only DAY-period habits can use them. */
    private fun isFreezerProtected(
        state: DomainState,
        habit: Habit,
        periodKey: Int,
    ): Boolean {
        if (habit.period != Period.DAY) return false
        val day = LogicalDays.daysOf(periodKey, Period.DAY).first
        return state.freezerUses.any { it.habitId == habit.id && it.protectedDay == day }
    }
}
