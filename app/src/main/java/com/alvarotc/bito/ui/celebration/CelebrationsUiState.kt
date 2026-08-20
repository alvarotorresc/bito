package com.alvarotc.bito.ui.celebration

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.ui.habi.HabiSpec

/**
 * Snapshot the global celebration sheets render: whether today's perfect-day sheet is pending,
 * how many points it earned, and which catalog badges unlocked since the badges sheet was last
 * dismissed. [spec] is what Habi looks like for the sheet's mini-avatar, the same shape the Stats
 * commentator uses.
 */
data class CelebrationsUiState(
    val perfectDayPending: Boolean = false,
    val pointsToday: Int = 0,
    val newBadges: List<BadgeDef> = emptyList(),
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val personality: Personality = Personality.NEUTRA,
    val userName: String = "",
)

/**
 * Derives the celebrations state from [state] as seen on [today]. Pure — no side effects, no
 * storage, no clock reads. [newBadges] is ordered by [BadgeCatalog.all] (catalog order), not by
 * unlock time.
 */
fun buildCelebrationsUiState(
    state: DomainState,
    prefs: Settings,
    badges: List<BadgeEntity>,
    owned: List<CustomizationItemEntity>,
    today: LogicalDay,
): CelebrationsUiState {
    val equippedIds = owned.filter { it.equipped }.map { it.itemId }
    val unseenBadgeIds =
        badges.filter { it.unlockedAtMillis > prefs.badgesSeenUntilMillis }.mapTo(mutableSetOf()) { it.badgeId }
    return CelebrationsUiState(
        perfectDayPending =
            PointsEngine.perfectDayGranted(state.pointsLedger, today) && prefs.perfectDayCelebratedDay < today,
        pointsToday = PointsEngine.pointsEarnedOn(state.pointsLedger, today),
        newBadges = BadgeCatalog.all.filter { it.id in unseenBadgeIds },
        spec =
            HabiSpec(
                mood = MoodEngine.moodOf(state, today, StatsEngine.lastActivityDay(state)),
                personality = prefs.personality,
                equipped = equippedSetOf(equippedIds),
            ),
        personality = prefs.personality,
        userName = prefs.userName,
    )
}
