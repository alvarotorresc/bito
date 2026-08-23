package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.StoreItemState
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.ledgerEntry
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

        val radiant = buildHabiUiState(radiantState, owned = emptyList(), balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertEquals(Mood.RADIANT, radiant.spec.mood)

        val dramaticState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(TODAY - 5)),
            )

        val dramatic = buildHabiUiState(dramaticState, owned = emptyList(), balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertEquals(Mood.DRAMATIC, dramatic.spec.mood)
    }

    @Test
    fun `the spec carries the requested personality through untouched`() {
        val state = domainState()

        val result = buildHabiUiState(state, owned = emptyList(), balance = 0, personality = Personality.SARGENTO, today = TODAY)

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
                ownedItem("upper-lazo", CustomizationCategory.UPPER, equipped = true),
            )

        val result = buildHabiUiState(state, owned = owned, balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertEquals("body-terracota", result.spec.equipped.bodyColor)
        assertEquals("upper-lazo", result.spec.equipped.upper)
        // Eyes untouched -> still the axis default.
        assertEquals("eyes-tinta", result.spec.equipped.eyeColor)
    }

    @Test
    fun `the store groups the catalog by axis in canon order, defaults first`() {
        val state = domainState()

        val result = buildHabiUiState(state, owned = emptyList(), balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertEquals(
            listOf(
                CustomizationCategory.BODY_COLOR,
                CustomizationCategory.PATTERN,
                CustomizationCategory.EYE_COLOR,
                CustomizationCategory.UPPER,
                CustomizationCategory.LOWER,
            ),
            result.store.keys.toList(),
        )

        // The two axes that have a default entry surface it first.
        assertEquals("body-salvia", result.store.getValue(CustomizationCategory.BODY_COLOR).first().item.id)
        assertEquals("eyes-tinta", result.store.getValue(CustomizationCategory.EYE_COLOR).first().item.id)

        // Every catalog item lands in exactly one axis bucket.
        val flattened = result.store.values.flatten().map { it.item.id }
        assertEquals(flattened.toSet().size, flattened.size)
    }

    @Test
    fun `store entries resolve the full ladder against a seeded balance`() {
        val state = domainState()

        val result = buildHabiUiState(state, owned = emptyList(), balance = 50, personality = Personality.NEUTRA, today = TODAY)

        fun stateOf(id: String) = result.store.values.flatten().first { it.item.id == id }.state

        // Defaults with nothing else equipped/owned -> worn, i.e. Equipped.
        assertEquals(StoreItemState.Equipped, stateOf("body-salvia"))
        assertEquals(StoreItemState.Equipped, stateOf("eyes-tinta"))

        // Affordable, including the exact-balance boundary (>=).
        assertEquals(StoreItemState.Affordable, stateOf("body-vainilla")) // price 15
        assertEquals(StoreItemState.Affordable, stateOf("pattern-corazones")) // price 50 == balance

        // Priced but out of reach.
        val missing = assertIs<StoreItemState.MissingPoints>(stateOf("upper-gorro-lana")) // price 80
        assertEquals(30, missing.missing)

        // Achievement-only exclusives.
        val lockedUpper = assertIs<StoreItemState.Locked>(stateOf("upper-corona"))
        assertEquals(100, lockedUpper.requiredStreak)
        val lockedPattern = assertIs<StoreItemState.Locked>(stateOf("pattern-chispas"))
        assertEquals(7, lockedPattern.requiredStreak)
    }

    @Test
    fun `an owned, worn item reads Equipped and a badge id turns Owned once unequipped`() {
        val state = domainState()
        val owned = listOf(ownedItem("body-vainilla", CustomizationCategory.BODY_COLOR, equipped = true))

        val result = buildHabiUiState(state, owned = owned, balance = 0, personality = Personality.NEUTRA, today = TODAY)

        val entries = result.store.getValue(CustomizationCategory.BODY_COLOR)
        assertEquals(StoreItemState.Equipped, entries.first { it.item.id == "body-vainilla" }.state)
        // The default fell out of the worn set now that something else is equipped, but it is
        // still owned by definition (defaults are never bought) -> Owned, not Equipped.
        assertEquals(StoreItemState.Owned, entries.first { it.item.id == "body-salvia" }.state)
    }

    @Test
    fun `a live preview dresses the spec without touching what the store thinks is owned or equipped`() {
        val state = domainState()
        val owned = listOf(ownedItem("body-terracota", CustomizationCategory.BODY_COLOR, equipped = true))

        // 90 is upper-lazo's exact price — Affordable, not MissingPoints.
        val previewing =
            buildHabiUiState(
                state,
                owned = owned,
                balance = 90,
                personality = Personality.NEUTRA,
                today = TODAY,
                previewItemId = "upper-lazo",
            )

        // The avatar wears the preview on top of what's really equipped...
        assertEquals("body-terracota", previewing.spec.equipped.bodyColor)
        assertEquals("upper-lazo", previewing.spec.equipped.upper)
        assertEquals("upper-lazo", previewing.previewItemId)
        // ...but the store grid never lies: the previewed item is still just Affordable/Owned/etc,
        // derived from the real DB-backed equipped set, not "Equipped" because it's on screen.
        val previewedEntry = previewing.store.getValue(CustomizationCategory.UPPER).first { it.item.id == "upper-lazo" }
        assertEquals(StoreItemState.Affordable, previewedEntry.state)

        val notPreviewing =
            buildHabiUiState(state, owned = owned, balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertEquals(null, notPreviewing.spec.equipped.upper)
        assertEquals(null, notPreviewing.previewItemId)
    }

    @Test
    fun `freezersOwned is threaded straight through PointsEngine`() {
        val state =
            domainState(
                ledger =
                    listOf(
                        ledgerEntry(delta = -100, reason = PointsReason.BUY_FREEZER, day = TODAY),
                        ledgerEntry(delta = -100, reason = PointsReason.BUY_FREEZER, day = TODAY),
                    ),
            )

        val result = buildHabiUiState(state, owned = emptyList(), balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertEquals(2, result.freezersOwned)
    }

    @Test
    fun `the balance passed in surfaces untouched and loading flips off`() {
        val state = domainState()

        val result = buildHabiUiState(state, owned = emptyList(), balance = 128, personality = Personality.NEUTRA, today = TODAY)

        assertEquals(128, result.balance)
        assertFalse(result.loading)
    }

    @Test
    fun `freezerPrice passed in surfaces untouched, not the EconomyConfig default`() {
        val result =
            buildHabiUiState(
                domainState(),
                owned = emptyList(),
                balance = 0,
                personality = Personality.NEUTRA,
                today = TODAY,
                freezerPrice = 250,
            )

        assertEquals(250, result.freezerPrice)
    }

    @Test
    fun `an empty state still produces a full, non-empty store`() {
        val result = buildHabiUiState(domainState(), owned = emptyList(), balance = 0, personality = Personality.NEUTRA, today = TODAY)

        assertTrue(result.store.values.all { it.isNotEmpty() })
        assertEquals(0, result.freezersOwned)
    }

    @Test
    fun `the default state starts loading`() {
        // The screens' first-frame gates (QA 2026-08-23) rely on this default: if it ever flips,
        // both gates die silently while every other test keeps passing.
        assertTrue(HabiUiState().loading)
    }
}
