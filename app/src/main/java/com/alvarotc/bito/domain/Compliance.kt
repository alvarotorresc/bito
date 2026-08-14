package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period

/**
 * Compliance of one habit over one period.
 *
 * - [FULFILLED]: the period met its goal.
 * - [FAILED]: the period can no longer meet its goal (closed without reaching
 *   an AT_LEAST target, exceeded an AT_MOST limit, or a ZERO relapse).
 * - [PENDING]: still undecided — an open period not yet at its AT_LEAST
 *   target, or a ZERO/AT_MOST period within its limit whose days are not all
 *   sealed yet (silence never inflates streaks).
 * - [PAUSED]: every requirable day of the period was paused; excluded from
 *   streaks and stats (shown as paused, never as failure).
 * - [NOT_APPLICABLE]: the period ends before the habit existed, starts after
 *   it was archived, or lies entirely in the future.
 */
enum class ComplianceStatus { FULFILLED, FAILED, PENDING, PAUSED, NOT_APPLICABLE }

object Compliance {
    /**
     * Evaluate [habit] over the period identified by [periodKey] (in the
     * habit's own period unit) as seen on [today].
     *
     * Progress accounting:
     * - CHECK with WEEK/MONTH periods counts DISTINCT days having at least one
     *   entry (two gym sessions on one day count once); COUNT/DURATION sum
     *   entry values over the period.
     * - [com.alvarotc.bito.domain.model.LogMode.BINARY] habits: a day with an
     *   entry counts as that day's goal reached, whatever the target says.
     * - Only days within the habit's life ([Habit.createdOnDay] until
     *   [Habit.archivedOnDay]) and outside pauses count as requirable.
     *
     * Direction rules:
     * - AT_LEAST: FULFILLED as soon as progress reaches the target in force
     *   (rule E2 via [Targets.targetOn], resolved on the period's last day);
     *   FAILED once the period closed short; PENDING while open and short.
     * - AT_MOST: FAILED as soon as progress exceeds the limit; otherwise
     *   FULFILLED only when every requirable day of the period up to [today]
     *   is sealed and the period is closed (or its remaining days are sealed);
     *   PENDING otherwise.
     * - ZERO: FAILED if the period has any entry (a relapse); otherwise
     *   FULFILLED when the day is sealed, PENDING until then.
     *
     * A day's seal can arrive the same logical day (nightly review), so
     * today's ZERO/AT_MOST habit can already be FULFILLED today.
     */
    fun complianceOf(
        state: DomainState,
        habit: Habit,
        periodKey: Int,
        today: LogicalDay,
    ): ComplianceStatus {
        val days = LogicalDays.daysOf(periodKey, habit.period)
        if (days.first > today) return ComplianceStatus.NOT_APPLICABLE

        val aliveDays = days.filter { isAliveOn(habit, it) }
        if (aliveDays.isEmpty()) return ComplianceStatus.NOT_APPLICABLE

        val requirableDays = aliveDays.filterNot { isPausedOn(state, habit.id, it) }
        if (requirableDays.isEmpty()) return ComplianceStatus.PAUSED

        val entries = state.entries.filter { it.habitId == habit.id && it.logicalDay in requirableDays }
        val progress = progressOf(habit, entries)
        val target = targetOf(state, habit, days.last)
        // Every requirable day sealed is what closes a limit or an abstinence: for an open
        // period that can only happen once its remaining days are sealed too.
        val allSealed = requirableDays.all { Sealing.isSealed(state, it) }

        return when (habit.direction) {
            Direction.AT_LEAST ->
                when {
                    progress >= target -> ComplianceStatus.FULFILLED
                    days.last < today -> ComplianceStatus.FAILED
                    else -> ComplianceStatus.PENDING
                }

            Direction.AT_MOST ->
                when {
                    progress > target -> ComplianceStatus.FAILED
                    allSealed -> ComplianceStatus.FULFILLED
                    else -> ComplianceStatus.PENDING
                }

            Direction.ZERO ->
                when {
                    entries.isNotEmpty() -> ComplianceStatus.FAILED
                    allSealed -> ComplianceStatus.FULFILLED
                    else -> ComplianceStatus.PENDING
                }
        }
    }

    /**
     * Whether [habit] demands anything on [day]: ACTIVE (not archived on or
     * before [day]), created on or before [day], and not inside any pause.
     * Weekly/monthly habits are requirable on days of their period but are
     * only judged at period close (rules E1/E6).
     */
    fun isRequirableOn(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
    ): Boolean = isAliveOn(habit, day) && !isPausedOn(state, habit.id, day)

    /** Whether [day] is inside any pause interval of [habitId]. */
    fun isPausedOn(
        state: DomainState,
        habitId: String,
        day: LogicalDay,
    ): Boolean =
        state.pauseIntervals.any { pause ->
            val endDay = pause.endDay
            pause.habitId == habitId && day >= pause.startDay && (endDay == null || day <= endDay)
        }

    /** Whether the habit already existed on [day] and had not been archived yet. */
    private fun isAliveOn(
        habit: Habit,
        day: LogicalDay,
    ): Boolean {
        if (day < habit.createdOnDay) return false
        val archivedOnDay = habit.archivedOnDay
        return when {
            archivedOnDay != null -> day < archivedOnDay
            // Archived without a recorded archive day: it demands nothing from anyone.
            habit.status == HabitStatus.ARCHIVED -> false
            else -> true
        }
    }

    private fun progressOf(
        habit: Habit,
        entries: List<Entry>,
    ): Int =
        if (habit.logMode == LogMode.BINARY || habit.metric == Metric.CHECK) {
            // Checks and done/not-done taps measure DAYS, not repetitions: two sessions on
            // the same day are one day of progress ("3x per week" counts days).
            entries.distinctBy { it.logicalDay }.size
        } else {
            entries.sumOf { it.value }
        }

    private fun targetOf(
        state: DomainState,
        habit: Habit,
        lastDayOfPeriod: LogicalDay,
    ): Int =
        if (habit.logMode == LogMode.BINARY && habit.period == Period.DAY) {
            // A BINARY target is a written reference ("9,000 steps"), never a value to
            // accumulate: the single tap of that day IS the day's goal.
            1
        } else {
            Targets.targetOn(habit, state.targetChanges, lastDayOfPeriod)
        }
}
