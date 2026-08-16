package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatsUiStateTest {
    @Test
    fun `the commentator mood follows the mood engine`() {
        val habit = RealHabits.makeBed
        val radiantState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, (TODAY - 7)..(TODAY - 1)),
            )

        val radiant = buildStatsUiState(radiantState, Personality.NEUTRA, TODAY)

        assertEquals(Mood.RADIANT, radiant.mood)

        // Same engine, different branch: 5 silent days trigger DRAMATIC regardless of ratio,
        // proving the builder threads StatsEngine.lastActivityDay into MoodEngine.moodOf.
        val dramaticState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(TODAY - 5)),
            )

        val dramatic = buildStatsUiState(dramaticState, Personality.NEUTRA, TODAY)

        assertEquals(Mood.DRAMATIC, dramatic.mood)
    }

    @Test
    fun `stats survive an empty state`() {
        val state = domainState()

        val result = buildStatsUiState(state, Personality.NEUTRA, TODAY)

        assertEquals(Mood.NORMAL, result.mood)
        assertEquals(Personality.NEUTRA, result.personality)
        assertEquals(0, result.perfectDays.total)
        assertEquals(0, result.perfectDays.thisMonth)
        assertTrue(result.week.rows.isEmpty())
        assertTrue(result.activeStreaks.isEmpty())
        assertFalse(result.loading)
    }
}
