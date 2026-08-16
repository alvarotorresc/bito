package com.alvarotc.bito.ui.stats

import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period

/** One row of the Records list: a habit's current vs. best streak, plus whether it is archived. */
data class RecordRow(
    val habitId: String,
    val name: String,
    val current: Int,
    val best: Int,
    val period: Period,
    val archived: Boolean,
)

/** Snapshot the Records screen renders: every habit with a streak in its history, best run first. */
data class RecordsUiState(
    val records: List<RecordRow> = emptyList(),
    val loading: Boolean = true,
)

/** Derives the Records screen state from [state] as seen on [today]. Pure, same shape as [buildStatsUiState]. */
fun buildRecordsUiState(
    state: DomainState,
    today: LogicalDay,
): RecordsUiState {
    val archivedIds = state.habits.filter { it.status == HabitStatus.ARCHIVED }.mapTo(mutableSetOf()) { it.id }
    val rows =
        StatsEngine.records(state, today).map { record ->
            RecordRow(
                habitId = record.habitId,
                name = record.name,
                current = record.current,
                best = record.best,
                period = record.period,
                archived = record.habitId in archivedIds,
            )
        }
    return RecordsUiState(records = rows, loading = false)
}
