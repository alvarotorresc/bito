package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeFamily
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [buildBadgesUiState] is pure — no clock, no storage — so every case sets up its own [BadgeEntity] list. */
class BadgesUiStateTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `groups follow family order and count unlocked`() {
        val badges =
            listOf(
                BadgeEntity("streak-7", unlockedAtMillis = 1_000L),
                BadgeEntity("perfect-day-1", unlockedAtMillis = 2_000L),
                BadgeEntity("first-habit", unlockedAtMillis = 3_000L),
            )

        val result = buildBadgesUiState(badges, dayCutoffMinutes = 0, zone = utc)

        assertEquals(3, result.unlocked)
        assertEquals(BadgeCatalog.size, result.total)
        assertFalse(result.loading)
        assertEquals(listOf(BadgeFamily.STREAK, BadgeFamily.CONSTANCY, BadgeFamily.MOMENT), result.groups.map { it.family })

        val streakGroup = result.groups.first { it.family == BadgeFamily.STREAK }
        assertEquals(BadgeCatalog.all.count { it.family == BadgeFamily.STREAK }, streakGroup.badges.size)
        val unlockedStreak = streakGroup.badges.first { it.def.id == "streak-7" }
        assertEquals(LogicalDays.logicalDayOf(1_000L, 0, utc), unlockedStreak.unlockedDay)
        val lockedStreak = streakGroup.badges.first { it.def.id == "streak-30" }
        assertNull(lockedStreak.unlockedDay)

        val constancyGroup = result.groups.first { it.family == BadgeFamily.CONSTANCY }
        assertEquals(BadgeCatalog.all.count { it.family == BadgeFamily.CONSTANCY }, constancyGroup.badges.size)

        val momentGroup = result.groups.first { it.family == BadgeFamily.MOMENT }
        assertEquals(BadgeCatalog.all.count { it.family == BadgeFamily.MOMENT }, momentGroup.badges.size)
    }

    @Test
    fun `an empty unlock list leaves every badge locked`() {
        val result = buildBadgesUiState(emptyList(), dayCutoffMinutes = 0, zone = utc)

        assertEquals(0, result.unlocked)
        assertEquals(BadgeCatalog.size, result.total)
        assertTrue(result.groups.flatMap { it.badges }.all { it.unlockedDay == null })
    }

    @Test
    fun `an unlock before the day cutoff counts toward the previous calendar day`() {
        // 2026-01-02T01:00 local with a 240min (4h) cutoff shifts back to 2026-01-01T21:00 —
        // the PREVIOUS calendar day. Same day-cutoff model every other date-bearing screen
        // (RecordsUiState, StatsUiState's week strip, etc.) already honors via LogicalDays.
        val unlockMillis = LocalDateTime.of(2026, 1, 2, 1, 0).atZone(utc).toInstant().toEpochMilli()
        val badges = listOf(BadgeEntity("streak-7", unlockedAtMillis = unlockMillis))

        val result = buildBadgesUiState(badges, dayCutoffMinutes = 240, zone = utc)

        val expectedDay = LocalDate.of(2026, 1, 1).toEpochDay().toInt()
        val streak7 = result.groups.flatMap { it.badges }.first { it.def.id == "streak-7" }
        assertEquals(expectedDay, streak7.unlockedDay)
    }
}
