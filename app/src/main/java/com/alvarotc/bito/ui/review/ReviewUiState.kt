package com.alvarotc.bito.ui.review

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.PerfectDays
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.buildTodayUiState

/**
 * Snapshot the nightly review screen renders: what is left to answer for
 * [today], plus the day's earned points/streaks/perfect status and any
 * badge unlocked since the user last saw the badge shelf.
 */
data class ReviewUiState(
    val today: LogicalDay = 0,
    val rows: List<HabitCardUi> = emptyList(),
    val pendingSealDays: List<LogicalDay> = emptyList(),
    val todaySealed: Boolean = false,
    val ringDone: Int = 0,
    val ringTotal: Int = 0,
    val pointsToday: Int = 0,
    val streaksAdvanced: Int = 0,
    val perfectToday: Boolean = false,
    val newBadges: List<BadgeDef> = emptyList(),
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val userName: String = "",
    val loading: Boolean = true,
) {
    /** "quedan N por sellar": rows still open plus past days left unsealed. */
    val remaining: Int get() = rows.size + pendingSealDays.size
}

/**
 * Derives the nightly review state from [state] as seen on [today]. Pure —
 * no side effects, no storage, no clock reads. Reuses [buildTodayUiState]
 * for the cards/ring/spec/pending-seal facts, then narrows them down to
 * what the review actually asks about via [reviewRowsOf], minus any card
 * the user already dismissed this session ([acknowledged]).
 */
fun buildReviewUiState(
    state: DomainState,
    sortOrder: Map<String, Int>,
    today: LogicalDay,
    acknowledged: Set<String> = emptySet(),
    personality: Personality = Personality.NEUTRA,
    owned: List<CustomizationItemEntity> = emptyList(),
    userName: String = "",
    badges: List<BadgeEntity> = emptyList(),
    badgesSeenUntilMillis: Long = 0L,
): ReviewUiState {
    val todayUi = buildTodayUiState(state, sortOrder, today, personality, owned, userName)
    return ReviewUiState(
        today = today,
        rows = reviewRowsOf(todayUi.cards, todayUi.todaySealed).filterNot { it.id in acknowledged },
        pendingSealDays = todayUi.pendingSealDays,
        todaySealed = todayUi.todaySealed,
        ringDone = todayUi.ringDone,
        ringTotal = todayUi.ringTotal,
        pointsToday = PointsEngine.pointsEarnedOn(state.pointsLedger, today),
        streaksAdvanced = StatsEngine.streaksAdvancedToday(state, today),
        perfectToday = PerfectDays.isPerfectDay(state, today, today),
        newBadges =
            BadgeCatalog.all.filter { def ->
                badges.any { it.badgeId == def.id && it.unlockedAtMillis > badgesSeenUntilMillis }
            },
        spec = todayUi.spec,
        userName = todayUi.userName,
        loading = false,
    )
}
