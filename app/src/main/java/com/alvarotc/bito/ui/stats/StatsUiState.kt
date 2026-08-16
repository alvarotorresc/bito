package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.domain.ActiveStreak
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.PerfectDaysSummary
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.WeekSummary
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality

/** Snapshot the Stats screen renders: commentator mood, perfect days, week strip, active streaks. */
data class StatsUiState(
    val mood: Mood = Mood.NORMAL,
    val personality: Personality = Personality.NEUTRA,
    val perfectDays: PerfectDaysSummary = PerfectDaysSummary(total = 0, thisMonth = 0),
    val week: WeekSummary = WeekSummary(rows = emptyList(), thisWeekPercent = null, deltaVsLastWeek = null),
    val activeStreaks: List<ActiveStreak> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Derives the Stats screen state from [state] as seen on [today]. Pure — no
 * side effects, no storage, no clock reads — so it is trivially testable and
 * safe to call on every state change.
 */
fun buildStatsUiState(
    state: DomainState,
    personality: Personality,
    today: LogicalDay,
): StatsUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    return StatsUiState(
        mood = MoodEngine.moodOf(state, today, lastActivityDay),
        personality = personality,
        perfectDays = StatsEngine.perfectDays(state, today),
        week = StatsEngine.weekSummary(state, today),
        activeStreaks = StatsEngine.activeStreaks(state, today),
        loading = false,
    )
}
