package com.alvarotc.bito.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** Freezer eligibility: which days a spent freezer can protect. */
class FreezerEngineTest {
    @Test
    fun `a failed past day of a daily habit is eligible`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit))

        assertEquals(FreezerEligibility.ELIGIBLE, FreezerEngine.eligibilityOf(state, habit, TODAY - 1, TODAY))
    }

    @Test
    fun `a fulfilled day is not eligible`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY - 1)))

        assertEquals(FreezerEligibility.NOT_FAILED, FreezerEngine.eligibilityOf(state, habit, TODAY - 1, TODAY))
    }

    @Test
    fun `an already protected day is not eligible`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), freezerUses = listOf(freezerOn(habit, TODAY - 1)))

        assertEquals(FreezerEligibility.ALREADY_PROTECTED, FreezerEngine.eligibilityOf(state, habit, TODAY - 1, TODAY))
    }

    @Test
    fun `weekly habits are never eligible`() {
        val habit = RealHabits.strengthTraining
        val state = domainState(habits = listOf(habit))

        assertEquals(FreezerEligibility.NOT_DAILY, FreezerEngine.eligibilityOf(state, habit, TODAY - 1, TODAY))
    }

    @Test
    fun `today and future days are not eligible`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit))

        assertEquals(FreezerEligibility.FUTURE_DAY, FreezerEngine.eligibilityOf(state, habit, TODAY, TODAY))
        assertEquals(FreezerEligibility.FUTURE_DAY, FreezerEngine.eligibilityOf(state, habit, TODAY + 1, TODAY))
    }
}
