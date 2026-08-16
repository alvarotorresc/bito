package com.alvarotc.bito.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Window arithmetic behind the Detail screen's compliance percentage. */
class HabitStatsTest {
    @Test
    fun `a daily habit fulfilled 5 of 7 judged days scores 71 percent`() {
        val habit = RealHabits.makeBed.createdOn(TODAY - 30)
        val state =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 2, TODAY - 1)),
            )
        val stats = HabitStats.windowStats(state, habit, TODAY, windowDays = 7)
        assertEquals(WindowStats(fulfilled = 5, judged = 7), stats)
        assertEquals(71, stats.percent)
    }

    @Test
    fun `pending abstinence days do not judge`() {
        val habit = RealHabits.noSmoking
        val state = domainState(habits = listOf(habit))

        val stats = HabitStats.windowStats(state, habit, TODAY, windowDays = 7)

        assertEquals(0, stats.judged)
        assertNull(stats.percent)
    }

    @Test
    fun `a weekly habit judges by weeks not days`() {
        val habit = RealHabits.strengthTraining
        val state = domainState(habits = listOf(habit))

        val stats = HabitStats.windowStats(state, habit, TODAY, windowDays = 30)

        // 4 closed weeks in the window; the running week is still PENDING (no entries) so it does not judge.
        assertEquals(4, stats.judged)
        assertEquals(0, stats.fulfilled)
    }

    @Test
    fun `an empty window has null percent`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit))

        val stats = HabitStats.windowStats(state, habit, TODAY, windowDays = 0)

        assertNull(stats.percent)
    }
}
