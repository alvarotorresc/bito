package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import java.time.YearMonth

/** What a single day looks like on a habit's heatmap. */
enum class DayDot { FULFILLED, FAILED, FROZEN, PAUSED, PENDING, ACTIVITY, EMPTY, OFF }

/** One cell of a month heatmap. */
data class HeatmapDay(val day: LogicalDay, val dot: DayDot, val isToday: Boolean)

/** Month heatmap of one habit, one dot per calendar day. */
object Heatmap {
    /**
     * One [HeatmapDay] per day of [month] (calendar month, not the habit's
     * period). DAY-period habits render their own daily compliance (FROZEN
     * instead of FAILED when a freezer protects that day); WEEK/MONTH habits
     * cannot be judged one day at a time, so they render ACTIVITY/EMPTY —
     * whether an entry landed on that day. OFF marks days outside the
     * habit's life or in the future, before any of the above applies.
     */
    fun monthOf(
        state: DomainState,
        habit: Habit,
        month: YearMonth,
        today: LogicalDay,
    ): List<HeatmapDay> {
        val firstDay = month.atDay(1).toEpochDay().toInt()
        val lastDay = month.atEndOfMonth().toEpochDay().toInt()
        return (firstDay..lastDay).map { day ->
            HeatmapDay(day = day, dot = dayDotOf(state, habit, day, today), isToday = day == today)
        }
    }

    /**
     * The dot a single [day] wears for [habit], the same mapping [monthOf]
     * uses per cell. Exposed so callers that need one day at a time (the
     * weekly dot row in Stats) share it instead of re-deriving it.
     */
    fun dayDotOf(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
        today: LogicalDay,
    ): DayDot {
        if (day > today || !Compliance.isAliveOn(habit, day)) return DayDot.OFF
        if (Compliance.isPausedOn(state, habit.id, day)) return DayDot.PAUSED
        return if (habit.period == Period.DAY) {
            dailyDotOf(state, habit, day, today)
        } else if (hasEntryOn(state, habit, day)) {
            DayDot.ACTIVITY
        } else {
            DayDot.EMPTY
        }
    }

    private fun dailyDotOf(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
        today: LogicalDay,
    ): DayDot =
        when (Compliance.complianceOf(state, habit, day, today)) {
            ComplianceStatus.FULFILLED -> DayDot.FULFILLED
            ComplianceStatus.FAILED -> if (isFrozenOn(state, habit, day)) DayDot.FROZEN else DayDot.FAILED
            ComplianceStatus.PENDING -> DayDot.PENDING
            // Alive and unpaused was already established by the caller.
            ComplianceStatus.PAUSED, ComplianceStatus.NOT_APPLICABLE -> DayDot.OFF
        }

    private fun hasEntryOn(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
    ): Boolean = state.entries.any { it.habitId == habit.id && it.logicalDay == day }

    private fun isFrozenOn(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
    ): Boolean = state.freezerUses.any { it.habitId == habit.id && it.protectedDay == day }
}
