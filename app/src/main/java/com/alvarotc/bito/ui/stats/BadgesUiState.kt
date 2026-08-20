package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeFamily

/** One family's slice of the Badges list: its label and every catalog badge in that family, catalog order. */
data class BadgeGroupUi(val family: BadgeFamily, val badges: List<BadgeUi>)

/** Snapshot the Badges (full list) screen renders: every catalog badge, grouped by family. */
data class BadgesUiState(
    val groups: List<BadgeGroupUi> = emptyList(),
    val unlocked: Int = 0,
    val total: Int = BadgeCatalog.size,
    val loading: Boolean = true,
)

/**
 * Derives the Badges screen state from the unlocked [badges]. Pure — no side effects, no clock
 * reads. Groups follow [BadgeFamily]'s declaration order (STREAK, CONSTANCY, MOMENT), same as
 * the catalog table (docs/06-badges.md §2).
 */
fun buildBadgesUiState(badges: List<BadgeEntity>): BadgesUiState {
    val unlockedAtById = badges.associate { it.badgeId to it.unlockedAtMillis }
    val groups =
        BadgeFamily.entries.map { family ->
            val familyBadges =
                BadgeCatalog.all
                    .filter { it.family == family }
                    .map { def -> BadgeUi(def, unlockedAtById[def.id]) }
            BadgeGroupUi(family, familyBadges)
        }
    return BadgesUiState(
        groups = groups,
        unlocked = groups.sumOf { group -> group.badges.count { it.unlockedAtMillis != null } },
        total = BadgeCatalog.size,
        loading = false,
    )
}
