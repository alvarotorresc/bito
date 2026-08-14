package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Mood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Habi's mood: a pure function of the closed 7-day window [TODAY-7, TODAY-1]
 * plus the prolonged-absence rule. Pending and paused periods take part in
 * neither side of the ratio.
 */
class MoodEngineTest {
    private val firstWindowDay = TODAY - 7
    private val lastWindowDay = TODAY - 1

    /** Days of the window on which the bed was made. */
    private fun windowWith(vararg madeBedOn: Int) =
        domainState(
            habits = listOf(RealHabits.makeBed),
            entries = entriesOn(RealHabits.makeBed, madeBedOn.toList()),
        )

    // -----------------------------------------------------------------------
    // Ausencia prolongada
    // -----------------------------------------------------------------------

    @Test
    fun `three logical days without any activity turn Habi dramatic`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3)

        assertEquals(Mood.DRAMATIC, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 3))
    }

    @Test
    fun `a long absence stays dramatic whatever the window says`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1)

        assertEquals(Mood.DRAMATIC, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 10))
    }

    @Test
    fun `two days without activity are not drama yet`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 2))
    }

    @Test
    fun `activity today is never drama`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY))
    }

    @Test
    fun `a fresh start with no history at all is normal, never drama`() {
        assertEquals(Mood.NORMAL, MoodEngine.moodOf(domainState(), TODAY, lastActivityDay = null))
    }

    @Test
    fun `a user who has never logged anything is never dramatic`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertNotEquals(Mood.DRAMATIC, MoodEngine.moodOf(state, TODAY, lastActivityDay = null))
    }

    // -----------------------------------------------------------------------
    // Ratio de la ventana
    // -----------------------------------------------------------------------

    @Test
    fun `a spotless week makes Habi radiant`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a ratio just over eighty percent is still radiant`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a middling week keeps Habi normal`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3)

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `exactly eighty percent is normal, not radiant`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, listOf(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4)),
                pauses = listOf(pauseOn(RealHabits.makeBed, TODAY - 2, TODAY - 1)),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `exactly fifty percent is normal, not wilted`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, listOf(TODAY - 7, TODAY - 6, TODAY - 5)),
                pauses = listOf(pauseOn(RealHabits.makeBed, TODAY - 1, TODAY - 1)),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a bad week wilts Habi`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5)

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a week with nothing done at all wilts Habi`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a window without a single decided period is normal`() {
        val state = domainState(habits = listOf(RealHabits.makeBed.createdOn(TODAY)))

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY))
    }

    @Test
    fun `a fully paused window is normal — pauses count for neither side`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                pauses = listOf(pauseOn(RealHabits.makeBed, firstWindowDay, lastWindowDay)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `unsealed abstinences stay pending and never drag the mood down`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.noSmoking),
                entries = entriesOn(RealHabits.makeBed, firstWindowDay..lastWindowDay),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Límites de la ventana
    // -----------------------------------------------------------------------

    @Test
    fun `the window ends yesterday — what happens today does not count yet`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY)

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY))
    }

    @Test
    fun `the window starts seven days back — older days do not count`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, (TODAY - 7)..(TODAY - 2)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Hábitos semanales
    // -----------------------------------------------------------------------

    @Test
    fun `a weekly habit counts once, on the window that holds its closing day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a missed week weighs on the mood once its sunday is inside the window`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }
}
