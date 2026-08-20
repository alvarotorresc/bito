package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.BadgeFamily
import com.alvarotc.bito.domain.model.LogicalDay
import java.time.ZoneId

/**
 * One catalog badge in the full Badges list. Unlike [BadgeUi] (Stats' Logros section, which only
 * needs unlocked/locked), the list screen shows an unlock date, so [unlockedDay] is a resolved
 * [LogicalDay] — the same day-cutoff model every other screen's dates go through — rather than a
 * raw millisecond timestamp.
 */
data class BadgeRowUi(val def: BadgeDef, val unlockedDay: LogicalDay?)

/** One family's slice of the Badges list: its label and every catalog badge in that family, catalog order. */
data class BadgeGroupUi(val family: BadgeFamily, val badges: List<BadgeRowUi>)

/** Snapshot the Badges (full list) screen renders: every catalog badge, grouped by family. */
data class BadgesUiState(
    val groups: List<BadgeGroupUi> = emptyList(),
    val unlocked: Int = 0,
    val total: Int = BadgeCatalog.size,
    val loading: Boolean = true,
)

/**
 * Derives the Badges screen state from the unlocked [badges]. Each unlock's raw
 * [BadgeEntity.unlockedAtMillis] is resolved to a [LogicalDay] via [LogicalDays.logicalDayOf]
 * with [dayCutoffMinutes]/[zone] — the same day-cutoff model [com.alvarotc.bito.ui.stats.RecordsUiState]
 * and every other date-bearing screen use, so an unlock just after midnight but before the
 * configured cutoff still lands on "yesterday". Groups follow [BadgeFamily]'s declaration order
 * (STREAK, CONSTANCY, MOMENT), same as the catalog table (docs/06-badges.md §2).
 */
fun buildBadgesUiState(
    badges: List<BadgeEntity>,
    dayCutoffMinutes: Int,
    zone: ZoneId,
): BadgesUiState {
    val unlockedDayById =
        badges.associate { it.badgeId to LogicalDays.logicalDayOf(it.unlockedAtMillis, dayCutoffMinutes, zone) }
    val groups =
        BadgeFamily.entries.map { family ->
            val familyBadges =
                BadgeCatalog.all
                    .filter { it.family == family }
                    .map { def -> BadgeRowUi(def, unlockedDayById[def.id]) }
            BadgeGroupUi(family, familyBadges)
        }
    return BadgesUiState(
        groups = groups,
        unlocked = groups.sumOf { group -> group.badges.count { it.unlockedDay != null } },
        total = BadgeCatalog.size,
        loading = false,
    )
}
