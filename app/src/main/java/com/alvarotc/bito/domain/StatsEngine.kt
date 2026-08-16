package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Perfect-day count over the habit's whole history, and restricted to today's calendar month. */
data class PerfectDaysSummary(val total: Int, val thisMonth: Int)

/** One habit's dot row for the current week (Monday..Sunday), for the Stats week strip. */
data class WeekRow(val habitId: String, val name: String, val dots: List<DayDot>)

/** The week strip: every active habit's row, plus the global weekly rate and its trend. */
data class WeekSummary(val rows: List<WeekRow>, val thisWeekPercent: Int?, val deltaVsLastWeek: Int?)

/** A habit currently on a run, for the Stats "active streaks" list. */
data class ActiveStreak(val habitId: String, val name: String, val length: Int, val period: Period)

/** A habit's current vs. best streak, for the Stats records list. */
data class HabitRecord(val habitId: String, val name: String, val current: Int, val best: Int, val period: Period)

/** Whole-app counters for the Stats overview. */
data class Totals(
    val entriesCount: Int,
    val perfectDays: Int,
    val pointsEarned: Int,
    val balance: Int,
    val freezersUsed: Int,
    val freezersOwned: Int,
    val activeHabits: Int,
    val pausedHabits: Int,
    val archivedHabits: Int,
    val daysSinceFirstHabit: Int?,
)

/** Aggregate arithmetic for the Stats screen. Everything is derived from [DomainState] — nothing stored. */
object StatsEngine {
    /**
     * Perfect days over the habit-days that ever existed (from the earliest
     * [com.alvarotc.bito.domain.model.Habit.createdOnDay] to [today]), and the
     * same count restricted to today's calendar month.
     */
    fun perfectDays(
        state: DomainState,
        today: LogicalDay,
    ): PerfectDaysSummary {
        val firstDay = state.habits.minOfOrNull { it.createdOnDay } ?: return PerfectDaysSummary(0, 0)
        if (firstDay > today) return PerfectDaysSummary(0, 0)
        val perfectDays = (firstDay..today).filter { PerfectDays.isPerfectDay(state, it, today) }
        val thisMonthKey = LogicalDays.periodKeyOf(today, Period.MONTH)
        val thisMonth = perfectDays.count { LogicalDays.periodKeyOf(it, Period.MONTH) == thisMonthKey }
        return PerfectDaysSummary(total = perfectDays.size, thisMonth = thisMonth)
    }

    /**
     * One dot row per ACTIVE habit over the ISO week containing [today]
     * (Monday..Sunday, [Heatmap.dayDotOf]'s mapping — future days render OFF).
     * The global rate judges only DAY-period habits, on their requirable days
     * (FULFILLED / (FULFILLED + FAILED)); [WeekSummary.deltaVsLastWeek] compares
     * it against the same rate over the full previous ISO week, null if either
     * week has nothing judged yet.
     */
    fun weekSummary(
        state: DomainState,
        today: LogicalDay,
    ): WeekSummary {
        val weekKey = LogicalDays.periodKeyOf(today, Period.WEEK)
        val thisWeekDays = LogicalDays.daysOf(weekKey, Period.WEEK)
        val lastWeekDays = LogicalDays.daysOf(weekKey - 1, Period.WEEK)
        val rows =
            state.habits
                .filter { it.status == HabitStatus.ACTIVE }
                .map { habit ->
                    WeekRow(
                        habitId = habit.id,
                        name = habit.name,
                        dots = thisWeekDays.map { day -> Heatmap.dayDotOf(state, habit, day, today) },
                    )
                }
        val thisWeekPercent = dailyComplianceRate(state, thisWeekDays, today)
        val lastWeekPercent = dailyComplianceRate(state, lastWeekDays, today)
        val delta =
            if (thisWeekPercent == null || lastWeekPercent == null) null else thisWeekPercent - lastWeekPercent
        return WeekSummary(rows = rows, thisWeekPercent = thisWeekPercent, deltaVsLastWeek = delta)
    }

    /** ACTIVE habits currently on a run (current >= 1), longest first. */
    fun activeStreaks(
        state: DomainState,
        today: LogicalDay,
    ): List<ActiveStreak> =
        state.habits
            .filter { it.status == HabitStatus.ACTIVE }
            .mapNotNull { habit ->
                val current = Streaks.streaksOf(state, habit, today).current
                if (current >= 1) ActiveStreak(habit.id, habit.name, current, habit.period) else null
            }
            .sortedByDescending { it.length }

    /** Every habit with a streak in its history — archived included — sorted by best run. */
    fun records(
        state: DomainState,
        today: LogicalDay,
    ): List<HabitRecord> =
        state.habits
            .map { habit -> habit to Streaks.streaksOf(state, habit, today) }
            .filter { (_, streak) -> streak.best > 0 }
            .map { (habit, streak) -> HabitRecord(habit.id, habit.name, streak.current, streak.best, habit.period) }
            .sortedByDescending { it.best }

    /** Whole-app counters, all derived straight from [state]. */
    fun totals(
        state: DomainState,
        today: LogicalDay,
    ): Totals {
        val firstHabitDay = state.habits.minOfOrNull { it.createdOnDay }
        return Totals(
            entriesCount = state.entries.size,
            perfectDays = perfectDays(state, today).total,
            pointsEarned = state.pointsLedger.filter { it.delta > 0 }.sumOf { it.delta },
            balance = PointsEngine.balance(state.pointsLedger),
            freezersUsed = state.freezerUses.size,
            freezersOwned = PointsEngine.freezersOwned(state),
            activeHabits = state.habits.count { it.status == HabitStatus.ACTIVE },
            pausedHabits = state.habits.count { it.status == HabitStatus.PAUSED },
            archivedHabits = state.habits.count { it.status == HabitStatus.ARCHIVED },
            daysSinceFirstHabit = firstHabitDay?.let { today - it },
        )
    }

    /** The most recent day with any entry or seal — feeds [MoodEngine.moodOf]. Null when history is empty. */
    fun lastActivityDay(state: DomainState): LogicalDay? =
        (state.entries.asSequence().map { it.logicalDay } + state.daySeals.asSequence().map { it.logicalDay })
            .maxOrNull()

    /** FULFILLED / (FULFILLED + FAILED) over [days] for DAY-period habits, on the days they demand something. */
    private fun dailyComplianceRate(
        state: DomainState,
        days: IntRange,
        today: LogicalDay,
    ): Int? {
        var fulfilled = 0
        var judged = 0
        for (habit in state.habits.filter { it.period == Period.DAY }) {
            for (day in days) {
                if (!Compliance.isRequirableOn(state, habit, day)) continue
                when (Compliance.complianceOf(state, habit, day, today)) {
                    ComplianceStatus.FULFILLED -> {
                        fulfilled++
                        judged++
                    }

                    ComplianceStatus.FAILED -> judged++
                    else -> Unit
                }
            }
        }
        return if (judged == 0) null else fulfilled * 100 / judged
    }
}
