package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.createdOn
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class HabiUiStateTest {
    private fun ownedItem(
        id: String,
        category: CustomizationCategory,
        equipped: Boolean = false,
    ) = CustomizationItemEntity(itemId = id, category = category, acquiredAtMillis = 0L, equipped = equipped)

    @Test
    fun `the mood follows the mood engine, same as stats`() {
        val habit = RealHabits.makeBed
        val radiantState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, (TODAY - 7)..(TODAY - 1)),
            )

        val radiant = buildHabiUiState(radiantState, owned = emptyList(), personality = Personality.NEUTRA, today = TODAY)

        assertEquals(Mood.RADIANT, radiant.spec.mood)

        val dramaticState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(TODAY - 5)),
            )

        val dramatic = buildHabiUiState(dramaticState, owned = emptyList(), personality = Personality.NEUTRA, today = TODAY)

        assertEquals(Mood.DRAMATIC, dramatic.spec.mood)
    }

    @Test
    fun `the spec carries the requested personality through untouched`() {
        val state = domainState()

        val result = buildHabiUiState(state, owned = emptyList(), personality = Personality.SARGENTO, today = TODAY)

        assertEquals(Personality.SARGENTO, result.spec.personality)
    }

    @Test
    fun `the equipped set derives only from rows flagged equipped, not merely owned`() {
        val state = domainState()
        val owned =
            listOf(
                ownedItem("body-terracota", CustomizationCategory.BODY_COLOR, equipped = true),
                // Owned but NOT equipped — must not leak into the worn set.
                ownedItem("body-carbon", CustomizationCategory.BODY_COLOR, equipped = false),
                ownedItem("neck-collar-flores", CustomizationCategory.NECK, equipped = true),
            )

        val result = buildHabiUiState(state, owned = owned, personality = Personality.NEUTRA, today = TODAY)

        assertEquals("body-terracota", result.spec.equipped.bodyColor)
        assertEquals("neck-collar-flores", result.spec.equipped.neck)
        // Eyes untouched -> still the axis default.
        assertEquals("eyes-tinta", result.spec.equipped.eyeColor)
    }

    @Test
    fun `the garden derives from history, unlocked badges, the clock and the high mark`() {
        val habit = RealHabits.makeBed.createdOn(TODAY - 3)
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY - 1, TODAY)))

        val result =
            buildHabiUiState(
                state,
                owned = emptyList(),
                personality = Personality.NEUTRA,
                today = TODAY,
                now = LocalDateTime.of(2026, 8, 14, 22, 0),
                unlockedBadges = setOf("streak-7", "first-habit"),
                waterHighMark = 30.0,
            )

        assertEquals(30.0, result.garden.water, 1e-9)
        assertEquals(2, result.garden.plots.size)
        assertEquals(setOf(GardenSpecial.LANTERN), result.garden.specials)
        assertEquals(LightPhase.NOCHE, result.garden.light.phase)
        assertEquals(0, result.garden.wilt)
    }
}
