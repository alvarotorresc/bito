package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DaySeal
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.FreezerUse
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PauseInterval
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TargetChange
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/*
 * Shared scenario builders for the domain-engine tests.
 *
 * Every date is fixed: the engine never reads the clock, so neither do the
 * tests. The calendar anchor is Friday 2026-08-14 ([TODAY]); the epoch-day
 * constants below are asserted against the real calendar in LogicalDaysTest.
 */

/** Friday 2026-08-14 — the "today" of every scenario. */
internal const val TODAY: LogicalDay = 20679

/** Monday 2026-08-10 — first day of the ISO week containing [TODAY]. */
internal const val THIS_MONDAY: LogicalDay = 20675

/** Sunday 2026-08-16 — last day of the ISO week containing [TODAY]. */
internal const val THIS_SUNDAY: LogicalDay = 20681

/** Monday 2026-08-03 — first day of the last closed ISO week. */
internal const val LAST_MONDAY: LogicalDay = 20668

/** Wednesday 2026-08-05. */
internal const val LAST_WEDNESDAY: LogicalDay = 20670

/** Friday 2026-08-07. */
internal const val LAST_FRIDAY: LogicalDay = 20672

/** Sunday 2026-08-09 — last day of the last closed ISO week. */
internal const val LAST_SUNDAY: LogicalDay = 20674

/** Monday 2026-07-27. */
internal const val TWO_WEEKS_AGO_MONDAY: LogicalDay = 20661

/** Sunday 2026-08-02. */
internal const val TWO_WEEKS_AGO_SUNDAY: LogicalDay = 20667

/** Monday 2026-07-20. */
internal const val THREE_WEEKS_AGO_MONDAY: LogicalDay = 20654

/** Sunday 2026-07-26. */
internal const val THREE_WEEKS_AGO_SUNDAY: LogicalDay = 20660

/** Wednesday 2026-07-01. */
internal const val JULY_FIRST: LogicalDay = 20635

/** Friday 2026-07-31. */
internal const val JULY_LAST: LogicalDay = 20665

/** Saturday 2026-08-01. */
internal const val AUGUST_FIRST: LogicalDay = 20666

/** Monday 2026-08-31. */
internal const val AUGUST_LAST: LogicalDay = 20696

/** Monday 2026-06-01. */
internal const val JUNE_FIRST: LogicalDay = 20605

/**
 * Thursday 2026-01-01 — default creation day of every fixture habit, far
 * enough back that no scenario ever bumps into the start of the habit's life
 * unless it says so explicitly.
 */
internal const val HABIT_BIRTH: LogicalDay = 20454

/** Local zone of the simulated device. Fixed so tests never depend on the machine. */
internal val testZone: ZoneId = ZoneId.of("Europe/Madrid")

private val idSequence = AtomicInteger(0)

private fun nextId(prefix: String): String = "$prefix-${idSequence.incrementAndGet()}"

/** A real instant inside [day], at [hour]:[minute] local wall-clock time. */
internal fun millisOn(
    day: LogicalDay,
    hour: Int = 12,
    minute: Int = 0,
): Long =
    LocalDate
        .ofEpochDay(day.toLong())
        .atTime(hour, minute)
        .atZone(testZone)
        .toInstant()
        .toEpochMilli()

/** Epoch day of a calendar date, used to assert the constants above. */
internal fun dayOf(
    year: Int,
    month: Int,
    dayOfMonth: Int,
): LogicalDay = LocalDate.of(year, month, dayOfMonth).toEpochDay().toInt()

// ---------------------------------------------------------------------------
// Records
// ---------------------------------------------------------------------------

/** One log record for [habit] on [day]. Default value 1 = a check or a relapse. */
internal fun entryOn(
    habit: Habit,
    day: LogicalDay,
    value: Int = 1,
    hour: Int = 12,
): Entry =
    Entry(
        id = nextId("entry"),
        habitId = habit.id,
        logicalDay = day,
        value = value,
        createdAtMillis = millisOn(day, hour),
    )

/** One record of [value] on each of [days]. */
internal fun entriesOn(
    habit: Habit,
    days: Iterable<LogicalDay>,
    value: Int = 1,
): List<Entry> = days.map { entryOn(habit, it, value) }

internal fun sealOn(day: LogicalDay): DaySeal = DaySeal(logicalDay = day, sealedAtMillis = millisOn(day, hour = 22))

internal fun sealsOn(days: Iterable<LogicalDay>): List<DaySeal> = days.map { sealOn(it) }

/** A pause of [habit]; both bounds inclusive, [endDay] null = still open. */
internal fun pauseOn(
    habit: Habit,
    startDay: LogicalDay,
    endDay: LogicalDay? = null,
    note: String? = null,
): PauseInterval = PauseInterval(habitId = habit.id, startDay = startDay, endDay = endDay, note = note)

/** A spent freezer protecting [habit] on [day]. */
internal fun freezerOn(
    habit: Habit,
    day: LogicalDay,
): FreezerUse =
    FreezerUse(
        id = nextId("freezer"),
        habitId = habit.id,
        protectedDay = day,
        usedAtMillis = millisOn(day, hour = 21),
    )

/** A target edit of [habit] taking effect on [day]. */
internal fun targetFrom(
    habit: Habit,
    day: LogicalDay,
    target: Int,
): TargetChange = TargetChange(habitId = habit.id, effectiveFromDay = day, target = target)

internal fun ledgerEntry(
    delta: Int,
    reason: PointsReason,
    refId: String? = null,
    day: LogicalDay = TODAY,
): PointsLedgerEntry =
    PointsLedgerEntry(
        id = nextId("ledger"),
        delta = delta,
        reason = reason,
        refId = refId,
        logicalDay = day,
        createdAtMillis = millisOn(day),
    )

/** The ledger row the data layer would append for a derived grant. */
internal fun PointsEvent.asLedgerEntry(): PointsLedgerEntry = ledgerEntry(delta, reason, refId, logicalDay)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

internal fun domainState(
    habits: List<Habit> = emptyList(),
    entries: List<Entry> = emptyList(),
    sealedDays: List<LogicalDay> = emptyList(),
    pauses: List<PauseInterval> = emptyList(),
    freezerUses: List<FreezerUse> = emptyList(),
    targetChanges: List<TargetChange> = emptyList(),
    ledger: List<PointsLedgerEntry> = emptyList(),
): DomainState =
    DomainState(
        habits = habits,
        targetChanges = targetChanges,
        entries = entries,
        daySeals = sealsOn(sealedDays),
        pauseIntervals = pauses,
        freezerUses = freezerUses,
        pointsLedger = ledger,
    )

// ---------------------------------------------------------------------------
// Habit modifiers
// ---------------------------------------------------------------------------

internal fun Habit.createdOn(day: LogicalDay): Habit = copy(createdOnDay = day)

internal fun Habit.archivedOn(day: LogicalDay): Habit = copy(status = HabitStatus.ARCHIVED, archivedOnDay = day)

/** Flags the habit as currently paused; the matching [PauseInterval] is added to the state. */
internal fun Habit.markedPaused(): Habit = copy(status = HabitStatus.PAUSED)

internal fun Habit.withTarget(target: Int): Habit = copy(target = target)

// ---------------------------------------------------------------------------
// Engine shorthands
// ---------------------------------------------------------------------------

/** Period key of the ISO week containing [day]. */
internal fun weekKey(day: LogicalDay): Int = LogicalDays.periodKeyOf(day, Period.WEEK)

/** Period key of the calendar month containing [day]. */
internal fun monthKey(day: LogicalDay): Int = LogicalDays.periodKeyOf(day, Period.MONTH)

/** Compliance of a DAY-period habit on [day] (the period key of a day is the day itself). */
internal fun DomainState.dayStatus(
    habit: Habit,
    day: LogicalDay,
    today: LogicalDay = TODAY,
): ComplianceStatus = Compliance.complianceOf(this, habit, day, today)

/** Compliance of [habit] over the period that contains [anyDayOfPeriod]. */
internal fun DomainState.periodStatus(
    habit: Habit,
    anyDayOfPeriod: LogicalDay,
    today: LogicalDay = TODAY,
): ComplianceStatus = Compliance.complianceOf(this, habit, LogicalDays.periodKeyOf(anyDayOfPeriod, habit.period), today)
