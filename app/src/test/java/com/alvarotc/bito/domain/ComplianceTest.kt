package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Compliance of every real preset: the counter, the "solo cumplido" tap, the
 * weekly frequency that counts days, the daily and weekly limits, and the
 * abstinences that only silence can leave pending.
 */
class ComplianceTest {
    // -----------------------------------------------------------------------
    // Preset "Cantidad" — agua 8 vasos/día · COUNT · DAY · AT_LEAST · contador
    // -----------------------------------------------------------------------

    @Test
    fun `water short of its goal on an open day is pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY, value = 5)),
            )

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.water, TODAY))
    }

    @Test
    fun `water short of its goal on a closed day failed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY - 1, value = 5)),
            )

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.water, TODAY - 1))
    }

    @Test
    fun `water is fulfilled as soon as the eighth glass is logged`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, List(8) { TODAY }),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.water, TODAY))
    }

    @Test
    fun `counter entries add up over the day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries =
                    listOf(
                        entryOn(RealHabits.water, TODAY, value = 3, hour = 9),
                        entryOn(RealHabits.water, TODAY, value = 3, hour = 14),
                        entryOn(RealHabits.water, TODAY, value = 2, hour = 20),
                    ),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.water, TODAY))
    }

    @Test
    fun `going over the goal is still fulfilled`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY, value = 12)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.water, TODAY))
    }

    @Test
    fun `a closed day without any entry failed`() {
        val state = domainState(habits = listOf(RealHabits.water))

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.water, TODAY - 1))
    }

    @Test
    fun `today without any entry is still pending`() {
        val state = domainState(habits = listOf(RealHabits.water))

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.water, TODAY))
    }

    @Test
    fun `entries logged on another day do not count`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY - 1, value = 8)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.water, TODAY - 1))
        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.water, TODAY))
    }

    @Test
    fun `entries of another habit do not count`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water, RealHabits.pagesRead),
                entries = listOf(entryOn(RealHabits.pagesRead, TODAY, value = 20)),
            )

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.water, TODAY))
        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.pagesRead, TODAY))
    }

    @Test
    fun `each day is judged against the goal in force that day (E2)`() {
        val edited = RealHabits.water.withTarget(10)
        val state =
            domainState(
                habits = listOf(edited),
                entries =
                    listOf(
                        entryOn(edited, TODAY - 1, value = 8),
                        entryOn(edited, TODAY, value = 8),
                    ),
                targetChanges =
                    listOf(
                        targetFrom(edited, HABIT_BIRTH, 8),
                        targetFrom(edited, TODAY, 10),
                    ),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(edited, TODAY - 1))
        assertEquals(ComplianceStatus.PENDING, state.dayStatus(edited, TODAY))
    }

    @Test
    fun `a day before the habit was created is not applicable`() {
        val water = RealHabits.water.createdOn(TODAY - 2)
        val state = domainState(habits = listOf(water))

        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.dayStatus(water, TODAY - 3))
    }

    @Test
    fun `the creation day itself is judged like any other day`() {
        val water = RealHabits.water.createdOn(TODAY - 2)
        val state =
            domainState(
                habits = listOf(water),
                entries = listOf(entryOn(water, TODAY - 2, value = 8)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(water, TODAY - 2))
    }

    @Test
    fun `a day in the future is not applicable`() {
        val state = domainState(habits = listOf(RealHabits.water))

        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.dayStatus(RealHabits.water, TODAY + 1))
    }

    @Test
    fun `an archived habit stops being applicable from its archive day on`() {
        val water = RealHabits.water.archivedOn(TODAY - 2)
        val state =
            domainState(
                habits = listOf(water),
                entries = listOf(entryOn(water, TODAY - 3, value = 8)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(water, TODAY - 3))
        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.dayStatus(water, TODAY - 2))
        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.dayStatus(water, TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Preset "Cantidad" en modo solo cumplido — pasos 9.000/día · BINARY
    // -----------------------------------------------------------------------

    @Test
    fun `a binary step entry fulfills the day whatever its value`() {
        val state =
            domainState(
                habits = listOf(RealHabits.steps),
                entries = listOf(entryOn(RealHabits.steps, TODAY - 1, value = 1)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.steps, TODAY - 1))
    }

    @Test
    fun `a binary entry far below its written goal still fulfills the day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.steps),
                entries = listOf(entryOn(RealHabits.steps, TODAY, value = 500)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.steps, TODAY))
    }

    @Test
    fun `a binary habit without an entry behaves like any positive habit`() {
        val state = domainState(habits = listOf(RealHabits.steps))

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.steps, TODAY))
        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.steps, TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Preset "Duración" — guitarra 20 min/día · DURATION · DAY · AT_LEAST
    // -----------------------------------------------------------------------

    @Test
    fun `guitar minutes accumulate toward the daily goal`() {
        val state =
            domainState(
                habits = listOf(RealHabits.guitar),
                entries =
                    listOf(
                        entryOn(RealHabits.guitar, TODAY, value = 5),
                        entryOn(RealHabits.guitar, TODAY, value = 15),
                    ),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.guitar, TODAY))
    }

    @Test
    fun `guitar minutes short of the goal on a closed day failed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.guitar),
                entries = listOf(entryOn(RealHabits.guitar, TODAY - 1, value = 15)),
            )

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.guitar, TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Preset "X veces por semana" — fuerza 3x/semana · CHECK · WEEK · AT_LEAST
    // -----------------------------------------------------------------------

    @Test
    fun `three training days in a week fulfill the weekly goal`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.strengthTraining, LAST_MONDAY))
    }

    @Test
    fun `two training sessions on the same day count as one day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries =
                    listOf(
                        entryOn(RealHabits.strengthTraining, LAST_MONDAY, hour = 8),
                        entryOn(RealHabits.strengthTraining, LAST_MONDAY, hour = 19),
                        entryOn(RealHabits.strengthTraining, LAST_WEDNESDAY),
                    ),
            )

        assertEquals(ComplianceStatus.FAILED, state.periodStatus(RealHabits.strengthTraining, LAST_MONDAY))
    }

    @Test
    fun `an open week still short of its goal is pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(THIS_MONDAY, THIS_MONDAY + 1)),
            )

        assertEquals(ComplianceStatus.PENDING, state.periodStatus(RealHabits.strengthTraining, THIS_MONDAY))
    }

    @Test
    fun `a closed week short of its goal failed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY)),
            )

        assertEquals(ComplianceStatus.FAILED, state.periodStatus(RealHabits.strengthTraining, LAST_MONDAY))
    }

    @Test
    fun `reaching the weekly goal fulfills the week before it closes`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, THIS_MONDAY..(THIS_MONDAY + 2)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.strengthTraining, TODAY))
    }

    @Test
    fun `training days of a neighbouring week do not count`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY, THIS_MONDAY)),
            )

        assertEquals(ComplianceStatus.FAILED, state.periodStatus(RealHabits.strengthTraining, LAST_MONDAY))
    }

    @Test
    fun `cooking at home five days fulfills the weekly goal`() {
        val state =
            domainState(
                habits = listOf(RealHabits.cookAtHome),
                entries = entriesOn(RealHabits.cookAtHome, LAST_MONDAY..(LAST_MONDAY + 4)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.cookAtHome, LAST_SUNDAY))
    }

    @Test
    fun `a week entirely before the habit existed is not applicable`() {
        val strength = RealHabits.strengthTraining.createdOn(THIS_MONDAY)
        val state = domainState(habits = listOf(strength))

        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.periodStatus(strength, LAST_MONDAY))
    }

    @Test
    fun `a week entirely in the future is not applicable`() {
        val state = domainState(habits = listOf(RealHabits.strengthTraining))

        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.periodStatus(RealHabits.strengthTraining, THIS_MONDAY + 7))
    }

    // -----------------------------------------------------------------------
    // Período MONTH
    // -----------------------------------------------------------------------

    @Test
    fun `a closed month reaching its goal is fulfilled`() {
        val state =
            domainState(
                habits = listOf(RealHabits.booksFinished),
                entries = entriesOn(RealHabits.booksFinished, listOf(JULY_FIRST + 5, JULY_LAST)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.booksFinished, JULY_FIRST))
    }

    @Test
    fun `a closed month short of its goal failed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.booksFinished),
                entries = listOf(entryOn(RealHabits.booksFinished, JULY_FIRST + 5)),
            )

        assertEquals(ComplianceStatus.FAILED, state.periodStatus(RealHabits.booksFinished, JULY_FIRST))
    }

    @Test
    fun `the running month short of its goal is pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.booksFinished),
                entries = listOf(entryOn(RealHabits.booksFinished, AUGUST_FIRST)),
            )

        assertEquals(ComplianceStatus.PENDING, state.periodStatus(RealHabits.booksFinished, TODAY))
    }

    // -----------------------------------------------------------------------
    // Preset "Dejar de hacer" — límite diario: redes <= 30 min/día · AT_MOST
    // -----------------------------------------------------------------------

    @Test
    fun `going over the daily limit fails the day immediately`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries = listOf(entryOn(RealHabits.socialMedia, TODAY, value = 35)),
            )

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.socialMedia, TODAY))
    }

    @Test
    fun `going over the daily limit fails even after the day is sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries = listOf(entryOn(RealHabits.socialMedia, TODAY - 1, value = 45)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.socialMedia, TODAY - 1))
    }

    @Test
    fun `landing exactly on the daily limit does not fail`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries = listOf(entryOn(RealHabits.socialMedia, TODAY - 1, value = 30)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.socialMedia, TODAY - 1))
    }

    @Test
    fun `staying under the daily limit without sealing stays pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries = listOf(entryOn(RealHabits.socialMedia, TODAY - 1, value = 20)),
            )

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.socialMedia, TODAY - 1))
    }

    @Test
    fun `sealing today already fulfills a daily limit that was respected`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries = listOf(entryOn(RealHabits.socialMedia, TODAY, value = 20)),
                sealedDays = listOf(TODAY),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.socialMedia, TODAY))
    }

    @Test
    fun `an untouched limit on an unsealed past day is pending, never fulfilled`() {
        val state = domainState(habits = listOf(RealHabits.socialMedia))

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.socialMedia, TODAY - 3))
    }

    @Test
    fun `an untouched limit on a sealed day is fulfilled`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                sealedDays = listOf(TODAY - 3),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.socialMedia, TODAY - 3))
    }

    // -----------------------------------------------------------------------
    // Preset "Dejar de hacer" — límite semanal: domicilio <= 1x/semana
    // -----------------------------------------------------------------------

    @Test
    fun `a second delivery day fails the week on the spot`() {
        val state =
            domainState(
                habits = listOf(RealHabits.foodDelivery),
                entries = entriesOn(RealHabits.foodDelivery, listOf(THIS_MONDAY, THIS_MONDAY + 1)),
            )

        assertEquals(ComplianceStatus.FAILED, state.periodStatus(RealHabits.foodDelivery, TODAY))
    }

    @Test
    fun `two deliveries on the same day count as a single day against the weekly limit`() {
        val state =
            domainState(
                habits = listOf(RealHabits.foodDelivery),
                entries =
                    listOf(
                        entryOn(RealHabits.foodDelivery, LAST_MONDAY, hour = 14),
                        entryOn(RealHabits.foodDelivery, LAST_MONDAY, hour = 21),
                    ),
                sealedDays = (LAST_MONDAY..LAST_SUNDAY).toList(),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.foodDelivery, LAST_MONDAY))
    }

    @Test
    fun `one delivery in a fully sealed closed week is fulfilled`() {
        val state =
            domainState(
                habits = listOf(RealHabits.foodDelivery),
                entries = listOf(entryOn(RealHabits.foodDelivery, LAST_WEDNESDAY)),
                sealedDays = (LAST_MONDAY..LAST_SUNDAY).toList(),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.foodDelivery, LAST_WEDNESDAY))
    }

    @Test
    fun `a closed week with days left unsealed stays pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.foodDelivery),
                entries = listOf(entryOn(RealHabits.foodDelivery, LAST_WEDNESDAY)),
                sealedDays = (LAST_MONDAY..(LAST_SUNDAY - 1)).toList(),
            )

        assertEquals(ComplianceStatus.PENDING, state.periodStatus(RealHabits.foodDelivery, LAST_WEDNESDAY))
    }

    @Test
    fun `the running week cannot be fulfilled yet even with every past day sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.foodDelivery),
                entries = listOf(entryOn(RealHabits.foodDelivery, THIS_MONDAY)),
                sealedDays = (THIS_MONDAY..TODAY).toList(),
            )

        assertEquals(ComplianceStatus.PENDING, state.periodStatus(RealHabits.foodDelivery, TODAY))
    }

    // -----------------------------------------------------------------------
    // Preset "Dejar de hacer" — abstinencia: no fumar · ZERO · DAY
    // -----------------------------------------------------------------------

    @Test
    fun `a relapse fails the abstinence day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                entries = listOf(entryOn(RealHabits.noSmoking, TODAY)),
            )

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.noSmoking, TODAY))
    }

    @Test
    fun `a relapse fails the day even if the day was sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                entries = listOf(entryOn(RealHabits.noSmoking, TODAY - 1)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(ComplianceStatus.FAILED, state.dayStatus(RealHabits.noSmoking, TODAY - 1))
    }

    @Test
    fun `a clean but unsealed day stays pending — silence never inflates streaks`() {
        val state = domainState(habits = listOf(RealHabits.noSmoking))

        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.noSmoking, TODAY))
        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.noSmoking, TODAY - 1))
        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.noSmoking, TODAY - 10))
    }

    @Test
    fun `sealing a clean day fulfills the abstinence, today included`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                sealedDays = listOf(TODAY - 1, TODAY),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.noSmoking, TODAY - 1))
        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.noSmoking, TODAY))
    }

    @Test
    fun `a seal of another day does not fulfill this one`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                sealedDays = listOf(TODAY - 2),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(RealHabits.noSmoking, TODAY - 2))
        assertEquals(ComplianceStatus.PENDING, state.dayStatus(RealHabits.noSmoking, TODAY - 1))
    }

    @Test
    fun `an abstinence day before the habit existed is not applicable`() {
        val noSmoking = RealHabits.noSmoking.createdOn(TODAY - 2)
        val state =
            domainState(
                habits = listOf(noSmoking),
                sealedDays = listOf(TODAY - 3, TODAY - 2),
            )

        assertEquals(ComplianceStatus.NOT_APPLICABLE, state.dayStatus(noSmoking, TODAY - 3))
        assertEquals(ComplianceStatus.FULFILLED, state.dayStatus(noSmoking, TODAY - 2))
    }

    // -----------------------------------------------------------------------
    // Pausas
    // -----------------------------------------------------------------------

    @Test
    fun `a paused day is reported as paused, never as failure`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                pauses = listOf(pauseOn(RealHabits.water, TODAY - 3, TODAY - 2, note = "Vacaciones")),
            )

        assertEquals(ComplianceStatus.PAUSED, state.dayStatus(RealHabits.water, TODAY - 3))
        assertEquals(ComplianceStatus.PAUSED, state.dayStatus(RealHabits.water, TODAY - 2))
    }

    @Test
    fun `a paused abstinence day is paused, not pending`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                pauses = listOf(pauseOn(RealHabits.noSmoking, TODAY - 3, TODAY - 3)),
            )

        assertEquals(ComplianceStatus.PAUSED, state.dayStatus(RealHabits.noSmoking, TODAY - 3))
    }

    @Test
    fun `a habit paused right now reports today as paused`() {
        val water = RealHabits.water.markedPaused()
        val state =
            domainState(
                habits = listOf(water),
                pauses = listOf(pauseOn(water, TODAY - 1)),
            )

        assertEquals(ComplianceStatus.PAUSED, state.dayStatus(water, TODAY))
        assertEquals(ComplianceStatus.PAUSED, state.dayStatus(water, TODAY - 1))
    }

    @Test
    fun `a weekly habit paused every day of the week is paused`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                pauses = listOf(pauseOn(RealHabits.strengthTraining, LAST_MONDAY, LAST_SUNDAY)),
            )

        assertEquals(ComplianceStatus.PAUSED, state.periodStatus(RealHabits.strengthTraining, LAST_MONDAY))
    }

    @Test
    fun `a partly paused week is still judged on the days that were demandable`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY)),
                pauses = listOf(pauseOn(RealHabits.strengthTraining, LAST_SUNDAY - 1, LAST_SUNDAY)),
            )

        assertEquals(ComplianceStatus.FULFILLED, state.periodStatus(RealHabits.strengthTraining, LAST_MONDAY))
    }

    // -----------------------------------------------------------------------
    // isRequirableOn / isPausedOn
    // -----------------------------------------------------------------------

    @Test
    fun `a habit is demandable from its creation day on`() {
        val water = RealHabits.water.createdOn(TODAY - 2)
        val state = domainState(habits = listOf(water))

        assertFalse(Compliance.isRequirableOn(state, water, TODAY - 3))
        assertTrue(Compliance.isRequirableOn(state, water, TODAY - 2))
        assertTrue(Compliance.isRequirableOn(state, water, TODAY))
    }

    @Test
    fun `an archived habit stops being demandable from its archive day on`() {
        val water = RealHabits.water.archivedOn(TODAY - 1)
        val state = domainState(habits = listOf(water))

        assertTrue(Compliance.isRequirableOn(state, water, TODAY - 2))
        assertFalse(Compliance.isRequirableOn(state, water, TODAY - 1))
        assertFalse(Compliance.isRequirableOn(state, water, TODAY))
    }

    @Test
    fun `a habit is not demandable inside a pause and is again after it`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                pauses = listOf(pauseOn(RealHabits.water, TODAY - 3, TODAY - 2)),
            )

        assertTrue(Compliance.isRequirableOn(state, RealHabits.water, TODAY - 4))
        assertFalse(Compliance.isRequirableOn(state, RealHabits.water, TODAY - 3))
        assertFalse(Compliance.isRequirableOn(state, RealHabits.water, TODAY - 2))
        assertTrue(Compliance.isRequirableOn(state, RealHabits.water, TODAY - 1))
    }

    @Test
    fun `an open pause keeps the habit undemandable from its start day on`() {
        val water = RealHabits.water.markedPaused()
        val state =
            domainState(
                habits = listOf(water),
                pauses = listOf(pauseOn(water, TODAY - 2)),
            )

        assertFalse(Compliance.isRequirableOn(state, water, TODAY - 2))
        assertFalse(Compliance.isRequirableOn(state, water, TODAY))
        assertFalse(Compliance.isRequirableOn(state, water, TODAY + 5))
    }

    @Test
    fun `a weekly habit is demandable on the plain days of its period`() {
        val state = domainState(habits = listOf(RealHabits.strengthTraining))

        assertTrue(Compliance.isRequirableOn(state, RealHabits.strengthTraining, TODAY))
        assertTrue(Compliance.isRequirableOn(state, RealHabits.strengthTraining, LAST_SUNDAY))
    }

    @Test
    fun `pause bounds are inclusive on both ends`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                pauses = listOf(pauseOn(RealHabits.water, TODAY - 5, TODAY - 3)),
            )

        assertFalse(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 6))
        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 5))
        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 4))
        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 3))
        assertFalse(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 2))
    }

    @Test
    fun `an open pause covers every day from its start on`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                pauses = listOf(pauseOn(RealHabits.water, TODAY - 1)),
            )

        assertFalse(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 2))
        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 1))
        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY))
    }

    @Test
    fun `a pause of another habit never applies`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water, RealHabits.guitar),
                pauses = listOf(pauseOn(RealHabits.guitar, TODAY - 3, TODAY)),
            )

        assertFalse(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 1))
        assertTrue(Compliance.isPausedOn(state, RealHabits.guitar.id, TODAY - 1))
    }

    @Test
    fun `several pauses of the same habit all count`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                pauses =
                    listOf(
                        pauseOn(RealHabits.water, TODAY - 10, TODAY - 8),
                        pauseOn(RealHabits.water, TODAY - 3, TODAY - 2),
                    ),
            )

        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 9))
        assertFalse(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 5))
        assertTrue(Compliance.isPausedOn(state, RealHabits.water.id, TODAY - 2))
    }

    // -----------------------------------------------------------------------
    // Integridad de los fixtures reales
    // -----------------------------------------------------------------------

    @Test
    fun `every abstinence fixture keeps the daily period invariant`() {
        val abstinences = RealHabits.all.filter { it.direction == Direction.ZERO }

        assertTrue(abstinences.isNotEmpty())
        assertTrue(abstinences.all { it.period == Period.DAY })
    }

    @Test
    fun `the real habit fixtures cover every metric, period, direction and log mode`() {
        assertEquals(Metric.entries.toSet(), RealHabits.all.map { it.metric }.toSet())
        assertEquals(Period.entries.toSet(), RealHabits.all.map { it.period }.toSet())
        assertEquals(Direction.entries.toSet(), RealHabits.all.map { it.direction }.toSet())
        assertEquals(LogMode.entries.toSet(), RealHabits.all.map { it.logMode }.toSet())
    }

    @Test
    fun `every real habit fixture has a unique id`() {
        assertEquals(RealHabits.all.size, RealHabits.all.map { it.id }.toSet().size)
    }
}
