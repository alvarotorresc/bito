package com.alvarotc.bito.domain

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Month heatmap dots, one per calendar day. */
class HeatmapTest {
    private val august = YearMonth.of(2026, 8)

    @Test
    fun `a fulfilled day renders FULFILLED and today wears its flag`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY)))

        val days = Heatmap.monthOf(state, habit, august, TODAY)

        val todayCell = days.single { it.day == TODAY }
        assertEquals(DayDot.FULFILLED, todayCell.dot)
        assertTrue(todayCell.isToday)
        assertFalse(days.first { it.day != TODAY }.isToday)
    }

    @Test
    fun `a failed day with a freezer use renders FROZEN`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), freezerUses = listOf(freezerOn(habit, TODAY - 1)))

        val days = Heatmap.monthOf(state, habit, august, TODAY)

        assertEquals(DayDot.FROZEN, days.single { it.day == TODAY - 1 }.dot)
    }

    @Test
    fun `days before creation and future days render OFF`() {
        val habit = RealHabits.makeBed.createdOn(TODAY - 2)
        val state = domainState(habits = listOf(habit))

        val days = Heatmap.monthOf(state, habit, august, TODAY)

        assertEquals(DayDot.OFF, days.single { it.day == TODAY - 5 }.dot) // before creation
        assertEquals(DayDot.OFF, days.single { it.day == AUGUST_LAST }.dot) // future
    }

    @Test
    fun `a weekly habit renders ACTIVITY on days with entries`() {
        val habit = RealHabits.strengthTraining
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(THIS_MONDAY)))

        val days = Heatmap.monthOf(state, habit, august, TODAY)

        assertEquals(DayDot.ACTIVITY, days.single { it.day == THIS_MONDAY }.dot)
        assertEquals(DayDot.EMPTY, days.single { it.day == THIS_MONDAY + 1 }.dot)
    }

    @Test
    fun `paused days render PAUSED`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), pauses = listOf(pauseOn(habit, TODAY - 1, TODAY - 1)))

        val days = Heatmap.monthOf(state, habit, august, TODAY)

        assertEquals(DayDot.PAUSED, days.single { it.day == TODAY - 1 }.dot)
    }
}
