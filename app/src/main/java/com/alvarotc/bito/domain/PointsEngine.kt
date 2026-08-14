package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.PointsReason

/**
 * Points. The ledger is the stored truth; this engine derives which grants
 * SHOULD exist from history. Policy: points are never confiscated — the data
 * layer appends [missingEvents] and never deletes, so retroactive edits can
 * only add new grants (rule E3: deleting a habit keeps its earned points).
 */
object PointsEngine {
    /**
     * All grants history justifies up to [today]:
     *
     * - HABIT_DONE per FULFILLED period. Grant timing [DECIDED 2026-08-14]:
     *   AT_LEAST grants on the day the target was reached (immediate
     *   reinforcement — strength 3x/week grants on the 3rd session's day);
     *   AT_MOST/ZERO grant on the period's last day (or the seal day if
     *   earlier), because only sealing/closing proves compliance.
     * - PERFECT_DAY for each perfect day; perfect weeks (every day of an ISO
     *   week perfect) and perfect months likewise, granted on the period's
     *   last day, with refIds "day:D" / "week:K" / "month:K".
     * - STREAK_MILESTONE per habit and reached length (via
     *   [Streaks.reachedMilestones]), granted at most once per habit+length.
     */
    fun earnedEvents(
        state: DomainState,
        today: LogicalDay,
        config: EconomyConfig,
    ): List<PointsEvent> {
        val events =
            habitDoneEvents(state, today, config) +
                perfectPeriodEvents(state, today, config) +
                streakMilestoneEvents(state, today, config)
        // Chronological order so appending them keeps the ledger readable as a history.
        return events.sortedWith(compareBy({ it.logicalDay }, { it.reason.ordinal }, { it.refId }))
    }

    /**
     * Events from [earned] not yet present in the ledger, keyed by
     * (reason, refId). Append-only reconciliation: never proposes removals.
     */
    fun missingEvents(
        earned: List<PointsEvent>,
        ledger: List<PointsLedgerEntry>,
    ): List<PointsEvent> {
        val alreadyGranted = ledger.mapNotNull { entry -> entry.refId?.let { entry.reason to it } }.toSet()
        return earned.filterNot { (it.reason to it.refId) in alreadyGranted }
    }

    /** Current balance: the sum of all ledger deltas. Never stored. */
    fun balance(ledger: List<PointsLedgerEntry>): Int = ledger.sumOf { it.delta }

    /** Whether the balance covers [cost] (cost > 0). */
    fun canSpend(
        ledger: List<PointsLedgerEntry>,
        cost: Int,
    ): Boolean = balance(ledger) >= cost

    /** Freezer inventory: BUY_FREEZER purchases minus recorded uses. */
    fun freezersOwned(state: DomainState): Int {
        val bought = state.pointsLedger.count { it.reason == PointsReason.BUY_FREEZER }
        return bought - state.freezerUses.size
    }

    private fun habitDoneEvents(
        state: DomainState,
        today: LogicalDay,
        config: EconomyConfig,
    ): List<PointsEvent> =
        state.habits.flatMap { habit ->
            val firstKey = LogicalDays.periodKeyOf(habit.createdOnDay, habit.period)
            val lastKey = LogicalDays.periodKeyOf(today, habit.period)
            (firstKey..lastKey)
                .filter { Compliance.complianceOf(state, habit, it, today) == ComplianceStatus.FULFILLED }
                .map { periodKey ->
                    PointsEvent(
                        reason = PointsReason.HABIT_DONE,
                        refId = "${habit.id}:$periodKey",
                        logicalDay = grantDayOf(state, habit, periodKey, today),
                        delta = config.habitDonePoints,
                    )
                }
        }

    /**
     * The day a fulfilled period earns its grant: the first day of the period on which
     * history — read with only the entries and seals logged up to that day — already
     * proved the period fulfilled. That single rule produces both timings the spec asks
     * for: AT_LEAST lands on the day its target was reached (immediate reinforcement),
     * while AT_MOST/ZERO land on the day the last requirable seal arrives, because only
     * sealing proves a limit or an abstinence.
     */
    private fun grantDayOf(
        state: DomainState,
        habit: Habit,
        periodKey: Int,
        today: LogicalDay,
    ): LogicalDay {
        val days = LogicalDays.daysOf(periodKey, habit.period)
        val lastCandidate = minOf(days.last, today)
        for (day in days.first..lastCandidate) {
            val historyUpTo =
                state.copy(
                    entries = state.entries.filter { it.logicalDay <= day },
                    daySeals = state.daySeals.filter { it.logicalDay <= day },
                )
            if (Compliance.complianceOf(historyUpTo, habit, periodKey, day) == ComplianceStatus.FULFILLED) {
                return day
            }
        }
        return lastCandidate
    }

    private fun perfectPeriodEvents(
        state: DomainState,
        today: LogicalDay,
        config: EconomyConfig,
    ): List<PointsEvent> {
        val firstDay = state.habits.minOfOrNull { it.createdOnDay } ?: return emptyList()
        if (firstDay > today) return emptyList()

        val perfectDays = (firstDay..today).filter { PerfectDays.isPerfectDay(state, it, today) }.toSet()
        val dayEvents =
            perfectDays.sorted().map { day ->
                PointsEvent(PointsReason.PERFECT_DAY, "day:$day", day, config.perfectDayPoints)
            }
        return dayEvents +
            perfectSpanEvents(perfectDays, Period.WEEK, "week", config.perfectWeekPoints, firstDay, today) +
            perfectSpanEvents(perfectDays, Period.MONTH, "month", config.perfectMonthPoints, firstDay, today)
    }

    /** A week/month is perfect when every one of its days is; it is granted at its close. */
    private fun perfectSpanEvents(
        perfectDays: Set<LogicalDay>,
        period: Period,
        refPrefix: String,
        points: Int,
        firstDay: LogicalDay,
        today: LogicalDay,
    ): List<PointsEvent> {
        val firstKey = LogicalDays.periodKeyOf(firstDay, period)
        val lastKey = LogicalDays.periodKeyOf(today, period)
        return (firstKey..lastKey)
            .filter { periodKey -> LogicalDays.daysOf(periodKey, period).all { it in perfectDays } }
            .map { periodKey ->
                PointsEvent(
                    reason = PointsReason.PERFECT_DAY,
                    refId = "$refPrefix:$periodKey",
                    logicalDay = LogicalDays.daysOf(periodKey, period).last,
                    delta = points,
                )
            }
    }

    private fun streakMilestoneEvents(
        state: DomainState,
        today: LogicalDay,
        config: EconomyConfig,
    ): List<PointsEvent> =
        state.habits.flatMap { habit ->
            Streaks.reachedMilestones(state, habit, today, config.streakMilestonePoints.keys)
                .sorted()
                .map { length ->
                    PointsEvent(
                        reason = PointsReason.STREAK_MILESTONE,
                        refId = "${habit.id}:$length",
                        // Nothing dates a milestone in the spec, and dating it would mean replaying
                        // the streak walk: it is stamped on the day history first justifies it.
                        logicalDay = today,
                        delta = config.streakMilestonePoints.getValue(length),
                    )
                }
        }
}
