package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        assertNull(result.bestRecord)
        assertEquals(0, result.totalEntries)
        assertFalse(result.loading)
    }

    @Test
    fun `the teasers surface the best record and the total entry count`() {
        val onARun = RealHabits.makeBed
        val other = RealHabits.meditate
        val state =
            domainState(
                habits = listOf(onARun, other),
                entries = entriesOn(onARun, listOf(TODAY - 2, TODAY - 1, TODAY)) + entriesOn(other, listOf(TODAY)),
            )

        val result = buildStatsUiState(state, Personality.NEUTRA, TODAY)

        assertEquals(onARun.id, result.bestRecord?.habitId)
        assertEquals(3, result.bestRecord?.best)
        assertEquals(4, result.totalEntries)
    }

    @Test
    fun `badges list the whole catalog with unlock times`() {
        val state = domainState()
        val badges =
            listOf(
                BadgeEntity("streak-7", unlockedAtMillis = 1_000L),
                BadgeEntity("first-habit", unlockedAtMillis = 2_000L),
            )

        val result = buildStatsUiState(state, Personality.NEUTRA, TODAY, badges = badges)

        assertEquals(BadgeCatalog.size, result.badges.size)
        assertEquals(2, result.badgesUnlocked)
        assertEquals(BadgeCatalog.size, result.badgesTotal)
        val streak7 = result.badges.first { it.def.id == "streak-7" }
        assertEquals(1_000L, streak7.unlockedAtMillis)
        val firstHabit = result.badges.first { it.def.id == "first-habit" }
        assertEquals(2_000L, firstHabit.unlockedAtMillis)
        val stillLocked = result.badges.filter { it.def.id != "streak-7" && it.def.id != "first-habit" }
        assertEquals(BadgeCatalog.size - 2, stillLocked.size)
        assertTrue(stillLocked.all { it.unlockedAtMillis == null })
    }
}
