package com.alvarotc.bito.ui.celebration

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.ledgerEntry
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CelebrationsUiStateTest {
    @Test
    fun `perfectDayPending needs the grant and an older marker`() {
        val granted = domainState(ledger = listOf(ledgerEntry(3, PointsReason.PERFECT_DAY, "day:$TODAY")))

        val olderMarker =
            buildCelebrationsUiState(granted, Settings(perfectDayCelebratedDay = -1), emptyList(), emptyList(), TODAY)
        assertTrue(olderMarker.perfectDayPending)
        // pointsToday sums the day's positive ledger deltas — the one PERFECT_DAY grant above.
        assertEquals(3, olderMarker.pointsToday)

        val sameDayMarker =
            buildCelebrationsUiState(granted, Settings(perfectDayCelebratedDay = TODAY), emptyList(), emptyList(), TODAY)
        assertFalse(sameDayMarker.perfectDayPending)

        val noGrant =
            buildCelebrationsUiState(domainState(), Settings(perfectDayCelebratedDay = -1), emptyList(), emptyList(), TODAY)
        assertFalse(noGrant.perfectDayPending)
    }

    @Test
    fun `newBadges are the unseen ones in catalog order`() {
        val badges =
            listOf(
                BadgeEntity("perfect-day-1", unlockedAtMillis = 200L),
                BadgeEntity("streak-7", unlockedAtMillis = 300L),
                // Unlocked before the marker: already seen, excluded.
                BadgeEntity("first-habit", unlockedAtMillis = 50L),
            )

        val result =
            buildCelebrationsUiState(domainState(), Settings(badgesSeenUntilMillis = 100L), badges, emptyList(), TODAY)

        // streak-7 precedes perfect-day-1 in BadgeCatalog.all — catalog order, not unlock order.
        assertEquals(listOf("streak-7", "perfect-day-1"), result.newBadges.map { it.id })
    }

    @Test
    fun `spec reflects mood, personality and the equipped set`() {
        val equipped = CustomizationItemEntity("body-vainilla", CustomizationCategory.BODY_COLOR, acquiredAtMillis = 0L, equipped = true)

        val result =
            buildCelebrationsUiState(domainState(), Settings(personality = Personality.CHEERLEADER), emptyList(), listOf(equipped), TODAY)

        // Empty state -> no decided habit-periods in the window -> NORMAL (MoodEngine.moodOf's own default).
        assertEquals(Mood.NORMAL, result.spec.mood)
        assertEquals(Personality.CHEERLEADER, result.spec.personality)
        assertEquals(Personality.CHEERLEADER, result.personality)
        assertEquals("body-vainilla", result.spec.equipped.bodyColor)
    }
}
