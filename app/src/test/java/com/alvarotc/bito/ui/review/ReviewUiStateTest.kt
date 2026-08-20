package com.alvarotc.bito.ui.review

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.createdOn
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.ledgerEntry
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.PointsReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewUiStateTest {
    @Test
    fun `rows exclude acknowledged ids and done cards`() {
        val done = RealHabits.makeBed
        val acknowledged = RealHabits.meditate
        val open = RealHabits.floss
        val state =
            domainState(
                habits = listOf(done, acknowledged, open),
                entries = entriesOn(done, listOf(TODAY)),
            )

        val ui = buildReviewUiState(state, emptyMap(), TODAY, acknowledged = setOf(acknowledged.id))

        assertEquals(listOf(open.id), ui.rows.map { it.id })
    }

    @Test
    fun `a clean abstinence is a row until today is sealed`() {
        val habit = RealHabits.noSmoking
        val unsealed = domainState(habits = listOf(habit))

        val uiUnsealed = buildReviewUiState(unsealed, emptyMap(), TODAY)
        assertEquals(listOf(habit.id), uiUnsealed.rows.map { it.id })

        val sealed = domainState(habits = listOf(habit), sealedDays = listOf(TODAY))
        val uiSealed = buildReviewUiState(sealed, emptyMap(), TODAY)
        assertTrue(uiSealed.rows.isEmpty())
    }

    @Test
    fun `pointsToday sums todays positive ledger deltas`() {
        val ledger =
            listOf(
                ledgerEntry(delta = 5, reason = PointsReason.HABIT_DONE, day = TODAY),
                ledgerEntry(delta = -3, reason = PointsReason.BUY_ITEM, day = TODAY),
                ledgerEntry(delta = 10, reason = PointsReason.PERFECT_DAY, day = TODAY - 1),
            )
        val state = domainState(ledger = ledger)

        val ui = buildReviewUiState(state, emptyMap(), TODAY)

        assertEquals(5, ui.pointsToday)
    }

    @Test
    fun `perfectToday follows PerfectDays`() {
        val habit = RealHabits.makeBed
        val fulfilled = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY)))
        assertTrue(buildReviewUiState(fulfilled, emptyMap(), TODAY).perfectToday)

        val unfulfilled = domainState(habits = listOf(habit))
        assertFalse(buildReviewUiState(unfulfilled, emptyMap(), TODAY).perfectToday)
    }

    @Test
    fun `newBadges keeps catalog order and only unseen ones`() {
        val badges = listOf(BadgeEntity("first-habit", 10L), BadgeEntity("streak-7", 30L))
        val state = domainState()

        val ui = buildReviewUiState(state, emptyMap(), TODAY, badges = badges, badgesSeenUntilMillis = 10L)

        assertEquals(listOf("streak-7"), ui.newBadges.map(BadgeDef::id))

        val bothUnseen = listOf(BadgeEntity("first-habit", 30L), BadgeEntity("streak-7", 30L))
        val uiBoth = buildReviewUiState(state, emptyMap(), TODAY, badges = bothUnseen, badgesSeenUntilMillis = 10L)

        // Catalog order (streak-7 before first-habit), independent of the input list's order.
        assertEquals(listOf("streak-7", "first-habit"), uiBoth.newBadges.map(BadgeDef::id))
    }

    @Test
    fun `remaining counts rows plus pending past days`() {
        val habit = RealHabits.noSmoking.createdOn(TODAY - 2)
        val state = domainState(habits = listOf(habit))

        val ui = buildReviewUiState(state, emptyMap(), TODAY)

        assertEquals(listOf(TODAY - 2, TODAY - 1), ui.pendingSealDays)
        assertEquals(listOf(habit.id), ui.rows.map { it.id })
        assertEquals(3, ui.remaining)
    }
}
