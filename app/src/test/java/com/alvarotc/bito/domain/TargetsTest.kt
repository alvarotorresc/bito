package com.alvarotc.bito.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rule E2 — editing a target applies from today onward and the history keeps
 * the target that was in force on each date.
 */
class TargetsTest {
    private val water = RealHabits.water

    @Test
    fun `a habit without target changes falls back to its current target`() {
        assertEquals(8, Targets.targetOn(water, emptyList(), TODAY))
        assertEquals(8, Targets.targetOn(water, emptyList(), HABIT_BIRTH))
    }

    @Test
    fun `raising the water goal from 8 to 10 applies from today onward`() {
        val edited = water.withTarget(10)
        val changes =
            listOf(
                targetFrom(water, HABIT_BIRTH, 8),
                targetFrom(water, TODAY, 10),
            )

        assertEquals(10, Targets.targetOn(edited, changes, TODAY))
        assertEquals(10, Targets.targetOn(edited, changes, TODAY + 1))
        assertEquals(10, Targets.targetOn(edited, changes, TODAY + 30))
    }

    @Test
    fun `the history keeps the old goal for the days before the edit`() {
        val edited = water.withTarget(10)
        val changes =
            listOf(
                targetFrom(water, HABIT_BIRTH, 8),
                targetFrom(water, TODAY, 10),
            )

        assertEquals(8, Targets.targetOn(edited, changes, TODAY - 1))
        assertEquals(8, Targets.targetOn(edited, changes, LAST_MONDAY))
        assertEquals(8, Targets.targetOn(edited, changes, HABIT_BIRTH))
    }

    @Test
    fun `each stretch of the history keeps the target that was in force then`() {
        val edited = water.withTarget(12)
        val changes =
            listOf(
                targetFrom(water, HABIT_BIRTH, 6),
                targetFrom(water, JULY_FIRST, 8),
                targetFrom(water, AUGUST_FIRST, 12),
            )

        assertEquals(6, Targets.targetOn(edited, changes, JULY_FIRST - 1))
        assertEquals(8, Targets.targetOn(edited, changes, JULY_FIRST))
        assertEquals(8, Targets.targetOn(edited, changes, JULY_LAST))
        assertEquals(12, Targets.targetOn(edited, changes, AUGUST_FIRST))
        assertEquals(12, Targets.targetOn(edited, changes, TODAY))
    }

    @Test
    fun `a day before the first recorded change uses the habit fallback target`() {
        val edited = water.withTarget(10)
        val changes = listOf(targetFrom(water, TODAY - 3, 12))

        assertEquals(10, Targets.targetOn(edited, changes, TODAY - 4))
        assertEquals(12, Targets.targetOn(edited, changes, TODAY - 3))
    }

    @Test
    fun `a change effective exactly on the day is already in force that day`() {
        val changes = listOf(targetFrom(water, TODAY, 10))

        assertEquals(10, Targets.targetOn(water, changes, TODAY))
    }

    @Test
    fun `target changes of other habits are ignored`() {
        val changes =
            listOf(
                targetFrom(RealHabits.guitar, HABIT_BIRTH, 45),
                targetFrom(RealHabits.pagesRead, HABIT_BIRTH, 99),
            )

        assertEquals(8, Targets.targetOn(water, changes, TODAY))
    }

    @Test
    fun `the latest applicable change wins whatever the order of the list`() {
        val edited = water.withTarget(12)
        val changes =
            listOf(
                targetFrom(water, AUGUST_FIRST, 12),
                targetFrom(water, HABIT_BIRTH, 6),
                targetFrom(RealHabits.guitar, TODAY, 40),
                targetFrom(water, JULY_FIRST, 8),
            )

        assertEquals(8, Targets.targetOn(edited, changes, JULY_LAST))
        assertEquals(12, Targets.targetOn(edited, changes, TODAY))
    }

    @Test
    fun `a duration goal resolves the same way as an amount goal`() {
        val guitar = RealHabits.guitar.withTarget(30)
        val changes =
            listOf(
                targetFrom(RealHabits.guitar, HABIT_BIRTH, 20),
                targetFrom(RealHabits.guitar, AUGUST_FIRST, 30),
            )

        assertEquals(20, Targets.targetOn(guitar, changes, JULY_LAST))
        assertEquals(30, Targets.targetOn(guitar, changes, TODAY))
    }
}
