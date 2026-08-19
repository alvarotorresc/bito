package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** Perfect-day count over the habit's whole history, restricted to today's calendar month, and restricted to today's calendar year. */
data class PerfectDaysSummary(val total: Int, val thisMonth: Int, val thisYear: Int)

/**
 * One habit's dot row for the current week (Monday..Sunday), for the Stats week strip.
 *
 * [done] and [target] are the row's trailing "N/M" tally, whose meaning depends on the habit's
 * own period (see [StatsEngine.weekSummary]):
 * - DAY habits: [done] is the count of this-week days [ComplianceStatus.FULFILLED] (a FROZEN day
 *   still reads as its underlying FAILED, same as [PerfectDays.isPerfectDay]); [target] is the
 *   count of this-week days the habit was actually requirable on (alive, unpaused) — a habit
 *   paused for the whole week renders "0/0".
 * - WEEK/MONTH habits: [done] and [target] are the habit's own current-period progress and target
 *   (e.g. "3x per week" habit mid-week is "2/3"), independent of the week strip's Monday..Sunday
 *   window — a MONTH habit's tally is its month-to-date progress, not anything about this ISO week.
 */
data class WeekRow(val habitId: String, val name: String, val dots: List<DayDot>, val done: Int, val target: Int)

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
     * same count restricted to today's calendar month and to today's calendar
     * year (the accent card's headline number — rule 1 of the 3a mockup).
     */
    fun perfectDays(
        state: DomainState,
        today: LogicalDay,
    ): PerfectDaysSummary {
        val firstDay = state.habits.minOfOrNull { it.createdOnDay } ?: return PerfectDaysSummary(0, 0, 0)
        if (firstDay > today) return PerfectDaysSummary(0, 0, 0)
        val perfectDays = (firstDay..today).filter { PerfectDays.isPerfectDay(state, it, today) }
        val thisMonthKey = LogicalDays.periodKeyOf(today, Period.MONTH)
        val thisMonth = perfectDays.count { LogicalDays.periodKeyOf(it, Period.MONTH) == thisMonthKey }
        val thisYearValue = LogicalDays.yearOf(today)
        val thisYear = perfectDays.count { LogicalDays.yearOf(it) == thisYearValue }
        return PerfectDaysSummary(total = perfectDays.size, thisMonth = thisMonth, thisYear = thisYear)
    }

    /**
     * One dot row per habit that was requirable on at least one day of the ISO week containing
     * [today] (Monday..Sunday, [Heatmap.dayDotOf]'s mapping — future days render OFF) — the same
     * [Compliance.isRequirableOn] rule [dailyComplianceRate] judges its days by below, so a habit
     * archived or paused mid-week still gets a row for the days it was alive and unpaused, instead
     * of vanishing from the list while [WeekSummary.thisWeekPercent] still counts those days.
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
                .filter { habit -> thisWeekDays.any { day -> Compliance.isRequirableOn(state, habit, day) } }
                .map { habit ->
                    val (done, target) = weekTallyOf(state, habit, thisWeekDays, today)
                    WeekRow(
                        habitId = habit.id,
                        name = habit.name,
                        dots = thisWeekDays.map { day -> Heatmap.dayDotOf(state, habit, day, today) },
                        done = done,
                        target = target,
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

    /**
     * [WeekRow.done] and [WeekRow.target] for one habit — see [WeekRow]'s KDoc for what each
     * number means per period. DAY habits are tallied over [thisWeekDays] directly (there is no
     * single [Compliance.requirableDaysOf] periodKey spanning 7 daily periods); WEEK/MONTH habits
     * reuse [Compliance.requirableDaysOf]/[Compliance.progressOf]/[Compliance.targetOf] against
     * the habit's OWN current period — never [thisWeekDays] — so a MONTH habit reports
     * month-to-date progress even though the dot row above it only draws the current ISO week.
     */
    private fun weekTallyOf(
        state: DomainState,
        habit: Habit,
        thisWeekDays: IntRange,
        today: LogicalDay,
    ): Pair<Int, Int> =
        if (habit.period == Period.DAY) {
            val requirable = thisWeekDays.count { Compliance.isRequirableOn(state, habit, it) }
            val fulfilled = thisWeekDays.count { Compliance.complianceOf(state, habit, it, today) == ComplianceStatus.FULFILLED }
            fulfilled to requirable
        } else {
            val periodKey = LogicalDays.periodKeyOf(today, habit.period)
            val periodDays = LogicalDays.daysOf(periodKey, habit.period)
            val requirableDays = Compliance.requirableDaysOf(state, habit, periodKey)
            val entries = state.entries.filter { it.habitId == habit.id && it.logicalDay in requirableDays }
            val progress = Compliance.progressOf(habit, entries)
            val target = Compliance.targetOf(state, habit, periodDays.last)
            progress to target
        }

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
