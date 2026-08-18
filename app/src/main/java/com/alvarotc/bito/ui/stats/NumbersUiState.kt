package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.Totals
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay

/** Every counter [Totals] tracks, zeroed until the first real emission. */
private val EMPTY_TOTALS =
    Totals(
        entriesCount = 0,
        perfectDays = 0,
        pointsEarned = 0,
        balance = 0,
        freezersUsed = 0,
        freezersOwned = 0,
        activeHabits = 0,
        pausedHabits = 0,
        archivedHabits = 0,
        daysSinceFirstHabit = null,
    )

/** Snapshot the Numbers screen renders: the whole-app counters, straight from [StatsEngine.totals]. */
data class NumbersUiState(
    val totals: Totals = EMPTY_TOTALS,
    val loading: Boolean = true,
)

/** Derives the Numbers screen state from [state] as seen on [today]. Pure, same shape as [buildStatsUiState]. */
fun buildNumbersUiState(
    state: DomainState,
    today: LogicalDay,
): NumbersUiState = NumbersUiState(totals = StatsEngine.totals(state, today), loading = false)
