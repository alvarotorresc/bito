package com.alvarotc.bito.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BadgeEngineTest {
    private fun earned(state: com.alvarotc.bito.domain.model.DomainState) = BadgeEngine.earnedBadges(state, TODAY)

    @Test
    fun `an empty state earns nothing`() {
        assertEquals(emptySet<String>(), earned(domainState()))
    }

    @Test
    fun `creating the first habit earns first-habit and nothing else`() {
        assertEquals(setOf("first-habit"), earned(domainState(habits = listOf(RealHabits.makeBed.createdOn(TODAY)))))
    }

    @Test
    fun `a 7-day streak earns streak-7, a 30-day streak also streak-30`() {
        val habit = RealHabits.makeBed
        val seven = domainState(habits = listOf(habit), entries = entriesOn(habit, (TODAY - 6)..TODAY))
        assertTrue("streak-7" in earned(seven))
        assertFalse("streak-30" in earned(seven))
        val thirty = domainState(habits = listOf(habit), entries = entriesOn(habit, (TODAY - 29)..TODAY))
        assertTrue(setOf("streak-7", "streak-30").all { it in earned(thirty) })
    }

    @Test
    fun `weekly habits earn streak badges in week units`() {
        val habit = RealHabits.strengthTraining
        val entries =
            (0 until 7).flatMap {
                    w ->
                entriesOn(habit, listOf(LAST_MONDAY - w * 7, LAST_MONDAY - w * 7 + 2, LAST_MONDAY - w * 7 + 4))
            }
        assertTrue("streak-7" in earned(domainState(habits = listOf(habit), entries = entries)))
    }

    @Test
    fun `a perfect day earns perfect-day-1, ten earn perfect-days-10`() {
        val habit = RealHabits.makeBed.createdOn(TODAY - 9)
        val one = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY)))
        assertTrue("perfect-day-1" in earned(one))
        assertFalse("perfect-days-10" in earned(one))
        val ten = domainState(habits = listOf(habit), entries = entriesOn(habit, (TODAY - 9)..TODAY))
        assertTrue("perfect-days-10" in earned(ten))
    }

    @Test
    fun `a full perfect ISO week earns perfect-week, a partial first week does not`() {
        val habit = RealHabits.makeBed.createdOn(LAST_MONDAY)
        val full = domainState(habits = listOf(habit), entries = entriesOn(habit, LAST_MONDAY..LAST_SUNDAY))
        assertTrue("perfect-week" in earned(full))
        val bornWednesday = RealHabits.makeBed.createdOn(LAST_WEDNESDAY)
        val partial = domainState(habits = listOf(bornWednesday), entries = entriesOn(bornWednesday, LAST_WEDNESDAY..LAST_SUNDAY))
        assertFalse("perfect-week" in earned(partial))
    }

    @Test
    fun `a full perfect calendar month earns perfect-month`() {
        val habit = RealHabits.makeBed.createdOn(JULY_FIRST)
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, JULY_FIRST..JULY_LAST))
        assertTrue("perfect-month" in earned(state))
    }

    @Test
    fun `seven consecutive active days earn first-week even across habits, gaps do not`() {
        val a = RealHabits.makeBed
        val b = RealHabits.noSmoking
        val mixed =
            domainState(
                habits = listOf(a, b),
                entries = entriesOn(a, listOf(TODAY - 6, TODAY - 4, TODAY - 2, TODAY)),
                sealedDays = listOf(TODAY - 5, TODAY - 3, TODAY - 1),
            )
        assertTrue("first-week" in earned(mixed))
        val gap =
            domainState(habits = listOf(a), entries = entriesOn(a, listOf(TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 2, TODAY - 1, TODAY)))
        assertFalse("first-week" in earned(gap))
    }

    @Test
    fun `coming back after breaking a 30-run earns resurrection`() {
        val habit = RealHabits.makeBed
        val entries = entriesOn(habit, (TODAY - 34)..(TODAY - 5)) + entriesOn(habit, listOf(TODAY - 3))
        assertTrue("resurrection" in earned(domainState(habits = listOf(habit), entries = entries)))
    }

    @Test
    fun `using a freezer earns first-freezer`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), freezerUses = listOf(freezerOn(habit, TODAY - 1)))
        assertTrue("first-freezer" in earned(state))
    }

    @Test
    fun `every earned id exists in the catalog`() {
        val habit = RealHabits.makeBed.createdOn(TODAY - 40)
        val state =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, (TODAY - 40)..TODAY),
                freezerUses = listOf(freezerOn(habit, TODAY - 41)),
            )
        assertTrue(earned(state).all { com.alvarotc.bito.domain.model.BadgeCatalog.byId(it) != null })
    }

    @Test
    fun `missingBadges never proposes revoking`() {
        assertEquals(emptySet<String>(), BadgeEngine.missingBadges(emptySet(), setOf("first-habit")))
        assertEquals(setOf("streak-7"), BadgeEngine.missingBadges(setOf("streak-7", "first-habit"), setOf("first-habit")))
    }
}
