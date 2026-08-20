package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [buildBadgesUiState] is pure — no clock, no storage — so every case sets up its own [BadgeEntity] list. */
class BadgesUiStateTest {
    @Test
    fun `groups follow family order and count unlocked`() {
        val badges =
            listOf(
                BadgeEntity("streak-7", unlockedAtMillis = 1_000L),
                BadgeEntity("perfect-day-1", unlockedAtMillis = 2_000L),
                BadgeEntity("first-habit", unlockedAtMillis = 3_000L),
            )

        val result = buildBadgesUiState(badges)

        assertEquals(3, result.unlocked)
        assertEquals(BadgeCatalog.size, result.total)
        assertFalse(result.loading)
        assertEquals(listOf(BadgeFamily.STREAK, BadgeFamily.CONSTANCY, BadgeFamily.MOMENT), result.groups.map { it.family })

        val streakGroup = result.groups.first { it.family == BadgeFamily.STREAK }
        assertEquals(BadgeCatalog.all.count { it.family == BadgeFamily.STREAK }, streakGroup.badges.size)
        val unlockedStreak = streakGroup.badges.first { it.def.id == "streak-7" }
        assertEquals(1_000L, unlockedStreak.unlockedAtMillis)
        val lockedStreak = streakGroup.badges.first { it.def.id == "streak-30" }
        assertEquals(null, lockedStreak.unlockedAtMillis)

        val constancyGroup = result.groups.first { it.family == BadgeFamily.CONSTANCY }
        assertEquals(BadgeCatalog.all.count { it.family == BadgeFamily.CONSTANCY }, constancyGroup.badges.size)

        val momentGroup = result.groups.first { it.family == BadgeFamily.MOMENT }
        assertEquals(BadgeCatalog.all.count { it.family == BadgeFamily.MOMENT }, momentGroup.badges.size)
    }

    @Test
    fun `an empty unlock list leaves every badge locked`() {
        val result = buildBadgesUiState(emptyList())

        assertEquals(0, result.unlocked)
        assertEquals(BadgeCatalog.size, result.total)
        assertTrue(result.groups.flatMap { it.badges }.all { it.unlockedAtMillis == null })
    }
}
