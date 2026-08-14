package com.alvarotc.bito.domain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sealing and the batch-seal offer after days away (rule E8: no artificial cap).
 */
class SealingTest {
    @Test
    fun `a day with a seal is sealed and its neighbours are not`() {
        val state = domainState(habits = listOf(RealHabits.noSmoking), sealedDays = listOf(TODAY - 1))

        assertTrue(Sealing.isSealed(state, TODAY - 1))
        assertFalse(Sealing.isSealed(state, TODAY - 2))
        assertFalse(Sealing.isSealed(state, TODAY))
    }

    @Test
    fun `an empty state has nothing sealed`() {
        assertFalse(Sealing.isSealed(domainState(), TODAY))
    }

    @Test
    fun `days away are offered for batch sealing, oldest first`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 4)
        val state = domainState(habits = listOf(noSmoking))

        assertContentEquals(
            listOf(TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1),
            Sealing.pendingSealDays(state, TODAY),
        )
    }

    @Test
    fun `today is never offered — the nightly review closes it`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 2)
        val state = domainState(habits = listOf(noSmoking))

        assertFalse(TODAY in Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `already sealed days are not offered again`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 4)
        val state = domainState(habits = listOf(noSmoking), sealedDays = listOf(TODAY - 3, TODAY - 1))

        assertContentEquals(listOf(TODAY - 4, TODAY - 2), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `sealing everything leaves nothing pending`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 3)
        val state = domainState(habits = listOf(noSmoking), sealedDays = ((TODAY - 3)..TODAY).toList())

        assertEquals(emptyList(), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `days before the abstinence existed are not offered`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 2)
        val state = domainState(habits = listOf(noSmoking))

        assertContentEquals(listOf(TODAY - 2, TODAY - 1), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `positive habits alone never ask for a seal`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water.createdOn(TODAY - 5), RealHabits.strengthTraining.createdOn(TODAY - 5)),
            )

        assertEquals(emptyList(), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `a daily limit also asks for its days to be sealed`() {
        val socialMedia = RealHabits.socialMedia.createdOn(TODAY - 3)
        val state = domainState(habits = listOf(socialMedia))

        assertContentEquals(listOf(TODAY - 3, TODAY - 2, TODAY - 1), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `a weekly limit also asks for its days to be sealed`() {
        val foodDelivery = RealHabits.foodDelivery.createdOn(TODAY - 3)
        val state = domainState(habits = listOf(foodDelivery))

        assertContentEquals(listOf(TODAY - 3, TODAY - 2, TODAY - 1), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `paused days are not offered for sealing`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 4)
        val state =
            domainState(
                habits = listOf(noSmoking),
                pauses = listOf(pauseOn(noSmoking, TODAY - 3, TODAY - 2)),
            )

        assertContentEquals(listOf(TODAY - 4, TODAY - 1), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `an archived abstinence stops asking for seals from its archive day on`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 4).archivedOn(TODAY - 2)
        val state = domainState(habits = listOf(noSmoking))

        assertContentEquals(listOf(TODAY - 4, TODAY - 3), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `a day is offered once however many abstinences are demandable`() {
        val days = TODAY - 2
        val state =
            domainState(
                habits =
                    listOf(
                        RealHabits.noSmoking.createdOn(days),
                        RealHabits.noSugar.createdOn(days),
                        RealHabits.noPhoneInBed.createdOn(days),
                        RealHabits.socialMedia.createdOn(days),
                    ),
            )

        assertContentEquals(listOf(TODAY - 2, TODAY - 1), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `a month away offers every one of those days, with no artificial cap (E8)`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 30)
        val state = domainState(habits = listOf(noSmoking))

        val pending = Sealing.pendingSealDays(state, TODAY)

        assertEquals(30, pending.size)
        assertContentEquals(((TODAY - 30)..(TODAY - 1)).toList(), pending)
    }

    @Test
    fun `the offer resumes after the last sealed day`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 6)
        val state =
            domainState(
                habits = listOf(noSmoking),
                sealedDays = ((TODAY - 6)..(TODAY - 3)).toList(),
            )

        assertContentEquals(listOf(TODAY - 2, TODAY - 1), Sealing.pendingSealDays(state, TODAY))
    }

    @Test
    fun `a state without any habit has nothing to seal`() {
        assertEquals(emptyList(), Sealing.pendingSealDays(domainState(), TODAY))
    }
}
