package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.ActiveStreak
import com.alvarotc.bito.domain.HabitRecord
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.PerfectDaysSummary
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.WeekSummary
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf

/** One catalog badge as the UI renders it: [unlockedAtMillis] is null while it is still locked. */
data class BadgeUi(val def: BadgeDef, val unlockedAtMillis: Long?)

/**
 * Snapshot the Stats screen renders: commentator mood, perfect days, week strip, active streaks,
 * the two teasers' data ([bestRecord] and [totalEntries] — the Récords/Tus números cards), and
 * the Logros section's data ([badges], catalog order — [badgesUnlocked] of [badgesTotal]).
 * [equipped] is what the commentator's mini-avatar wears (M5's "Stats commentator" deferral,
 * closed by T13) — the same [EquippedSet] shape the Habi screen's own avatar uses.
 */
data class StatsUiState(
    val mood: Mood = Mood.NORMAL,
    val personality: Personality = Personality.NEUTRA,
    val equipped: EquippedSet = EquippedSet(),
    val perfectDays: PerfectDaysSummary = PerfectDaysSummary(total = 0, thisMonth = 0, thisYear = 0),
    val week: WeekSummary = WeekSummary(rows = emptyList(), thisWeekPercent = null, deltaVsLastWeek = null),
    val activeStreaks: List<ActiveStreak> = emptyList(),
    val bestRecord: HabitRecord? = null,
    val totalEntries: Int = 0,
    val loading: Boolean = true,
    val badges: List<BadgeUi> = emptyList(),
    val badgesUnlocked: Int = 0,
    val badgesTotal: Int = BadgeCatalog.size,
)

/**
 * Derives the Stats screen state from [state] as seen on [today]. Pure — no
 * side effects, no storage, no clock reads — so it is trivially testable and
 * safe to call on every state change. [owned] defaults empty so existing positional callers
 * (tests predating the commentator avatar) keep compiling unchanged, and [badges] trails it for
 * the same reason (tests predating the Logros section).
 */
fun buildStatsUiState(
    state: DomainState,
    personality: Personality,
    today: LogicalDay,
    owned: List<CustomizationItemEntity> = emptyList(),
    badges: List<BadgeEntity> = emptyList(),
): StatsUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val equippedIds = owned.filter { it.equipped }.map { it.itemId }
    val unlockedAtById = badges.associate { it.badgeId to it.unlockedAtMillis }
    val badgeUis = BadgeCatalog.all.map { def -> BadgeUi(def, unlockedAtById[def.id]) }
    return StatsUiState(
        mood = MoodEngine.moodOf(state, today, lastActivityDay),
        personality = personality,
        equipped = equippedSetOf(equippedIds),
        perfectDays = StatsEngine.perfectDays(state, today),
        week = StatsEngine.weekSummary(state, today),
        activeStreaks = StatsEngine.activeStreaks(state, today),
        bestRecord = StatsEngine.records(state, today).firstOrNull(),
        totalEntries = StatsEngine.totals(state, today).entriesCount,
        loading = false,
        badges = badgeUis,
        badgesUnlocked = badgeUis.count { it.unlockedAtMillis != null },
        badgesTotal = BadgeCatalog.size,
    )
}
