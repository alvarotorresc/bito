package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El nivel de ojo se DERIVA del historial (spec §7.2): nunca se lee de nada persistido, así que
 * un restore lo reconstruye solo. Y nunca baja: lo que se pintó, pintado queda (biblia §4).
 */
class EyeRitualTest {
    @Test
    fun `no habits means no eyes painted`() {
        val state = domainState(habits = emptyList())
        assertEquals(0, EyeRitual.derivedLevel(state, TODAY))
    }

    @Test
    fun `one habit paints the first eye`() {
        val habit = dailyCheck(id = "cama", createdOnDay = TODAY - 1)
        val state = domainState(habits = listOf(habit))
        assertEquals(1, EyeRitual.derivedLevel(state, TODAY))
    }

    @Test
    fun `a six day run still only earns the first eye`() {
        val state = runOf(days = 6)
        assertEquals(1, EyeRitual.derivedLevel(state, TODAY))
    }

    @Test
    fun `a seven day run earns the second eye`() {
        val state = runOf(days = 7)
        assertEquals(2, EyeRitual.derivedLevel(state, TODAY))
    }

    @Test
    fun `breaking the streak after seven never unpaints the second eye`() {
        val state = runOf(days = 7, thenMissedDays = 5)
        assertEquals(2, EyeRitual.derivedLevel(state, TODAY))
    }

    @Test
    fun `an archived habit still counts - the walk happened`() {
        val state = runOf(days = 7, status = HabitStatus.ARCHIVED)
        assertEquals(2, EyeRitual.derivedLevel(state, TODAY))
    }

    @Test
    fun `heal never lowers the stored level`() {
        assertEquals(2, EyeRitual.heal(stored = 2, derived = 0))
        assertEquals(2, EyeRitual.heal(stored = 0, derived = 2))
        assertEquals(1, EyeRitual.heal(stored = 1, derived = 1))
        assertEquals(1, EyeRitual.heal(stored = 0, derived = 1))
    }

    // -----------------------------------------------------------------------
    // Helper: run of consecutive days
    // -----------------------------------------------------------------------

    private fun runOf(
        days: Int,
        thenMissedDays: Int = 0,
        status: HabitStatus = HabitStatus.ACTIVE,
    ): DomainState {
        // When archiving, ensure at least 1 missed day so the last entry is before TODAY,
        // allowing the archive period to be encountered during evaluation
        val actualMissedDays =
            if (status == HabitStatus.ARCHIVED) maxOf(thenMissedDays, 1) else thenMissedDays

        val createdDay = TODAY - days - actualMissedDays + 1
        val lastEntryDay = TODAY - actualMissedDays

        var habit = dailyCheck(id = "test-habit", createdOnDay = createdDay)

        if (status == HabitStatus.ARCHIVED) {
            // Archive on TODAY so the ENDS_WALK is encountered when evaluating at TODAY,
            // properly testing the archive logic while preserving the days-long history
            habit = habit.archivedOn(TODAY)
        } else {
            habit = habit.copy(status = status)
        }

        // Entries from createdDay to lastEntryDay
        val entries = entriesOn(habit, createdDay..lastEntryDay)
        return domainState(habits = listOf(habit), entries = entries)
    }

    private fun dailyCheck(
        id: String,
        createdOnDay: Int,
    ) = RealHabits.makeBed.copy(id = id, createdOnDay = createdOnDay)
}
