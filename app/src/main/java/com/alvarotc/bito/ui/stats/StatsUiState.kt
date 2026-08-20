package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.ActiveStreak
import com.alvarotc.bito.domain.HabitRecord
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.PerfectDaysSummary
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.WeekSummary
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf

/**
 * Snapshot the Stats screen renders: commentator mood, perfect days, week strip, active streaks,
 * and the two teasers' data ([bestRecord] and [totalEntries] — the Récords/Tus números cards).
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
)

/**
 * Derives the Stats screen state from [state] as seen on [today]. Pure — no
 * side effects, no storage, no clock reads — so it is trivially testable and
 * safe to call on every state change. [owned] defaults empty so existing positional callers
 * (tests predating the commentator avatar) keep compiling unchanged.
 */
fun buildStatsUiState(
    state: DomainState,
    personality: Personality,
    today: LogicalDay,
    owned: List<CustomizationItemEntity> = emptyList(),
): StatsUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val equippedIds = owned.filter { it.equipped }.map { it.itemId }
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
    )
}
