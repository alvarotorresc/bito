package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.TodayUiState

/** What the single-habit widget renders for its chosen habit. */
sealed interface SingleHabitModel {
    /**
     * No usable habit behind the stored id — never configured, archived or deleted.
     * The widget shows a friendly "choose another habit" that reopens the config.
     */
    data object Missing : SingleHabitModel

    /** The habit exists but is paused: nothing to log today, tapping opens the app. */
    data class Paused(val name: String) : SingleHabitModel

    /** The habit is requirable today; [tapLogs] mirrors the list widget's rule (CHECK/COUNTER, not yet done). */
    data class Active(
        val habitId: String,
        val name: String,
        val kind: CardKind,
        val progress: Int,
        val target: Int,
        val step: Int,
        val doneToday: Boolean,
        val failed: Boolean,
        val tapLogs: Boolean,
    ) : SingleHabitModel
}

/**
 * Resolves [habitId] against today's state. `state.cards` only holds requirable habits
 * ([com.alvarotc.bito.domain.Compliance.isRequirableOn] = alive and not paused), so a miss there
 * plus a hit in `pausedHabits` is exactly "paused", and a miss in both is "archived or gone" —
 * no extra lookups needed beyond what [TodayUiState] already carries.
 */
fun buildSingleHabitModel(
    state: TodayUiState,
    habitId: String?,
): SingleHabitModel {
    if (habitId == null) return SingleHabitModel.Missing
    val card = state.cards.firstOrNull { it.id == habitId }
    if (card == null) {
        val paused = state.pausedHabits.firstOrNull { it.id == habitId }
        return if (paused != null) SingleHabitModel.Paused(paused.name) else SingleHabitModel.Missing
    }
    val logsFromWidget = card.kind == CardKind.CHECK || card.kind == CardKind.COUNTER
    return SingleHabitModel.Active(
        habitId = card.id,
        name = card.name,
        kind = card.kind,
        progress = card.progress,
        target = card.target,
        step = if (card.kind == CardKind.CHECK) 1 else card.step,
        doneToday = card.doneToday,
        failed = card.failed,
        tapLogs = logsFromWidget && !card.doneToday,
    )
}
