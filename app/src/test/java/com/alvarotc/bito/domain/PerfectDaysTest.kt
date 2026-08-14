package com.alvarotc.bito.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rule E6 — a perfect day only judges what the day demanded: daily habits,
 * limits and abstinences. Weekly and monthly habits are judged at their close
 * and never spoil a single day.
 */
class PerfectDaysTest {
    @Test
    fun `a day with every daily habit fulfilled is perfect`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.water, RealHabits.noSmoking),
                entries =
                    listOf(
                        entryOn(RealHabits.makeBed, TODAY - 1),
                        entryOn(RealHabits.water, TODAY - 1, value = 8),
                    ),
                sealedDays = listOf(TODAY - 1),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `one missed daily habit is enough to spoil the day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.water),
                entries =
                    listOf(
                        entryOn(RealHabits.makeBed, TODAY - 1),
                        entryOn(RealHabits.water, TODAY - 1, value = 5),
                    ),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a weekly habit never spoils a single day (E6)`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.strengthTraining),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a monthly habit never spoils a single day either`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.booksFinished),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a day with no demandable daily habit is not perfect`() {
        val state = domainState(habits = listOf(RealHabits.strengthTraining))

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a day without any habit at all is not perfect`() {
        assertFalse(PerfectDays.isPerfectDay(domainState(), TODAY - 1, TODAY))
    }

    @Test
    fun `an unsealed abstinence keeps the day from being perfect`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.noSmoking),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a relapse spoils the day even when it was sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.noSmoking),
                entries =
                    listOf(
                        entryOn(RealHabits.makeBed, TODAY - 1),
                        entryOn(RealHabits.noSmoking, TODAY - 1),
                    ),
                sealedDays = listOf(TODAY - 1),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a respected and sealed limit keeps the day perfect`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.socialMedia),
                entries =
                    listOf(
                        entryOn(RealHabits.makeBed, TODAY - 1),
                        entryOn(RealHabits.socialMedia, TODAY - 1, value = 20),
                    ),
                sealedDays = listOf(TODAY - 1),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `going over a limit spoils the day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.socialMedia),
                entries =
                    listOf(
                        entryOn(RealHabits.makeBed, TODAY - 1),
                        entryOn(RealHabits.socialMedia, TODAY - 1, value = 35),
                    ),
                sealedDays = listOf(TODAY - 1),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a paused habit does not spoil the day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.water),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
                pauses = listOf(pauseOn(RealHabits.water, TODAY - 2, TODAY - 1)),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a day where every daily habit was paused is not perfect`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.water),
                pauses =
                    listOf(
                        pauseOn(RealHabits.makeBed, TODAY - 2, TODAY - 1),
                        pauseOn(RealHabits.water, TODAY - 2, TODAY - 1),
                    ),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `an archived habit stops counting toward perfect days`() {
        val water = RealHabits.water.archivedOn(TODAY - 2)
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, water),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `a day before the habits existed is not perfect`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed.createdOn(TODAY - 1)),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY - 3, TODAY))
        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }

    @Test
    fun `today can already be perfect once everything is done and sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.noSmoking),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY)),
                sealedDays = listOf(TODAY),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY, TODAY))
    }

    @Test
    fun `today is not perfect while a daily habit is still pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.water),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY)),
            )

        assertFalse(PerfectDays.isPerfectDay(state, TODAY, TODAY))
    }

    @Test
    fun `a future day is not perfect`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertFalse(PerfectDays.isPerfectDay(state, TODAY + 1, TODAY))
    }

    @Test
    fun `a day fulfilled only by a binary tap is perfect`() {
        val state =
            domainState(
                habits = listOf(RealHabits.steps),
                entries = listOf(entryOn(RealHabits.steps, TODAY - 1)),
            )

        assertTrue(PerfectDays.isPerfectDay(state, TODAY - 1, TODAY))
    }
}
