package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.PointsReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsEngineTest {
    @Test
    fun `perfect days counts total and current month separately`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(JULY_LAST, TODAY - 1, TODAY)))

        val summary = StatsEngine.perfectDays(state, TODAY)

        assertEquals(3, summary.total)
        assertEquals(2, summary.thisMonth)
    }

    @Test
    fun `perfect days also isolates the count for the current calendar year`() {
        // Born mid-2025 so a perfect day from last December is possible alongside two this year.
        val habit = RealHabits.makeBed.createdOn(dayOf(2025, 6, 1))
        val state =
            domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(dayOf(2025, 12, 31), TODAY - 1, TODAY)))

        val summary = StatsEngine.perfectDays(state, TODAY)

        assertEquals(3, summary.total)
        assertEquals(2, summary.thisYear) // 2025-12-31 is last year; TODAY-1 and TODAY are both 2026
    }

    @Test
    fun `week row tallies fulfilled days over requirable days for a DAY habit`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, THIS_MONDAY..TODAY))

        val summary = StatsEngine.weekSummary(state, TODAY)

        val row = summary.rows.single()
        assertEquals(5, row.done) // Monday..Friday fulfilled
        assertEquals(7, row.target) // alive and unpaused the whole week
    }

    @Test
    fun `week row tallies progress over target for a WEEK habit using its own period`() {
        val habit = RealHabits.strengthTraining // AT_LEAST 3x/week

        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(THIS_MONDAY, THIS_MONDAY + 2)))

        val summary = StatsEngine.weekSummary(state, TODAY)

        val row = summary.rows.single()
        assertEquals(2, row.done)
        assertEquals(3, row.target)
    }

    @Test
    fun `week row tallies progress over target for a MONTH habit using its own period, not the ISO week`() {
        val habit = RealHabits.booksFinished // AT_LEAST 2/month

        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY - 3)))

        val summary = StatsEngine.weekSummary(state, TODAY)

        val row = summary.rows.single()
        assertEquals(1, row.done)
        assertEquals(2, row.target)
    }

    @Test
    fun `week summary delta compares this week against the full previous week`() {
        val habit = RealHabits.makeBed
        val state =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, (THIS_MONDAY..TODAY).toList() + listOf(LAST_MONDAY)),
            )

        val summary = StatsEngine.weekSummary(state, TODAY)

        assertEquals(100, summary.thisWeekPercent) // Mon..Fri all fulfilled, Sat/Sun still in the future
        assertEquals(86, summary.deltaVsLastWeek) // last week: 1/7 fulfilled -> 14%; 100 - 14 = 86
        val row = summary.rows.single()
        assertEquals(DayDot.FULFILLED, row.dots[0]) // Monday
        assertEquals(DayDot.OFF, row.dots[5]) // Saturday, still in the future
    }

    @Test
    fun `week percent agrees with rows when a habit is archived mid-week`() {
        // Archived Wednesday: only Monday and Tuesday stay requirable for this habit this week.
        // Fulfilled Monday, left Tuesday unfulfilled — the row must show 1/2, not drop out
        // of `rows` just because the habit is no longer ACTIVE (it used to: rows filtered by
        // status while thisWeekPercent counted every DAY habit regardless of status).
        val archived = RealHabits.makeBed.archivedOn(TODAY - 2)
        // A second, still-ACTIVE habit fulfilled Monday..Friday, so the aggregate percent has to
        // combine both habits' requirable days, not just echo the archived habit's own row.
        val active = RealHabits.meditate
        val state =
            domainState(
                habits = listOf(archived, active),
                entries = entriesOn(archived, listOf(THIS_MONDAY)) + entriesOn(active, (THIS_MONDAY..TODAY).toList()),
            )

        val summary = StatsEngine.weekSummary(state, TODAY)

        assertEquals(listOf(archived.id, active.id), summary.rows.map { it.habitId })
        val archivedRow = summary.rows.single { it.habitId == archived.id }
        assertEquals(1, archivedRow.done) // Monday only; Tuesday unfulfilled, Wed onward not alive
        assertEquals(2, archivedRow.target) // Mon + Tue, the only requirable days before Wednesday's archive
        val activeRow = summary.rows.single { it.habitId == active.id }
        assertEquals(5, activeRow.done) // Mon..Fri fulfilled
        assertEquals(7, activeRow.target) // alive and unpaused the whole week
        // 1 (archived, Mon) + 5 (active, Mon..Fri) fulfilled over 2 (archived, Mon+Tue) + 5
        // (active, Mon..Fri; Sat/Sun still pending) judged = 6/7 -> 85%, the same requirable
        // days the two rows above are built from.
        assertEquals(85, summary.thisWeekPercent)
    }

    @Test
    fun `active streaks lists only active habits with current streak, longest first`() {
        val onARun = RealHabits.makeBed
        val justStarted = RealHabits.meditate
        val paused = RealHabits.floss.markedPaused()
        val state =
            domainState(
                habits = listOf(onARun, justStarted, paused),
                entries = entriesOn(onARun, listOf(TODAY - 2, TODAY - 1, TODAY)) + entriesOn(justStarted, listOf(TODAY)),
            )

        val streaks = StatsEngine.activeStreaks(state, TODAY)

        assertEquals(listOf(onARun.id, justStarted.id), streaks.map { it.habitId })
        assertEquals(3, streaks[0].length)
        assertEquals(1, streaks[1].length)
    }

    @Test
    fun `records includes archived habits and sorts by best streak`() {
        val archived = RealHabits.makeBed.archivedOn(TODAY - 1)
        val active = RealHabits.meditate
        val state =
            domainState(
                habits = listOf(archived, active),
                entries =
                    entriesOn(archived, listOf(TODAY - 5, TODAY - 4, TODAY - 3)) +
                        entriesOn(active, listOf(TODAY)),
            )

        val records = StatsEngine.records(state, TODAY)

        assertEquals(listOf(archived.id, active.id), records.map { it.habitId })
        assertEquals(3, records[0].best)
        assertEquals(1, records[1].best)
    }

    @Test
    fun `totals derives counts from state alone`() {
        val active = RealHabits.makeBed
        val paused = RealHabits.meditate.markedPaused()
        val archived = RealHabits.floss.archivedOn(TODAY)
        val state =
            domainState(
                habits = listOf(active, paused, archived),
                entries = entriesOn(active, listOf(TODAY - 1, TODAY)),
                freezerUses = listOf(freezerOn(active, TODAY - 5)),
                ledger =
                    listOf(
                        ledgerEntry(delta = -30, reason = PointsReason.BUY_FREEZER),
                        ledgerEntry(delta = 5, reason = PointsReason.HABIT_DONE),
                        ledgerEntry(delta = 40, reason = PointsReason.PERFECT_DAY),
                    ),
            )

        val totals = StatsEngine.totals(state, TODAY)

        assertEquals(2, totals.entriesCount)
        assertEquals(1, totals.freezersUsed)
        assertEquals(0, totals.freezersOwned) // 1 bought - 1 used
        assertEquals(1, totals.activeHabits)
        assertEquals(1, totals.pausedHabits)
        assertEquals(1, totals.archivedHabits)
        assertEquals(TODAY - HABIT_BIRTH, totals.daysSinceFirstHabit)
        assertEquals(15, totals.balance) // -30 + 5 + 40
        assertEquals(45, totals.pointsEarned) // 5 + 40, the -30 spend excluded
    }

    @Test
    fun `last activity day is the max of entries and seals`() {
        val habit = RealHabits.makeBed
        val state =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(TODAY - 3)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(TODAY - 1, StatsEngine.lastActivityDay(state))
    }

    @Test
    fun `last activity day is null with no history`() {
        assertNull(StatsEngine.lastActivityDay(domainState()))
    }
}
