package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.TodayUiState

data class WidgetItem(
    val habitId: String,
    val name: String,
    val kind: CardKind,
    val progress: Int,
    val target: Int,
    val step: Int,
    val tapLogs: Boolean,
)

/** [spec] mirrors Habi's live mood/personality/equipped look (T15) — a mini avatar in the widget header. */
data class WidgetModel(val done: Int, val total: Int, val items: List<WidgetItem>, val spec: HabiSpec)

/**
 * [state]'s own [TodayUiState.spec] already carries mood x personality x equipped (T14's
 * `buildTodayUiState`, fed by the same domain state/settings/owned items the widget reads) — no
 * need to re-derive it here, just thread it through unchanged.
 */
fun buildWidgetModel(
    state: TodayUiState,
    selectedIds: Set<String>?,
): WidgetModel {
    val visible = if (selectedIds.isNullOrEmpty()) state.cards else state.cards.filter { it.id in selectedIds }
    val items =
        visible.filterNot { it.doneToday }.map {
            WidgetItem(
                habitId = it.id,
                name = it.name,
                kind = it.kind,
                progress = it.progress,
                target = it.target,
                step = if (it.kind == CardKind.CHECK) 1 else it.step,
                tapLogs = it.kind == CardKind.CHECK || it.kind == CardKind.COUNTER,
            )
        }
    return WidgetModel(done = visible.count { it.doneToday }, total = visible.size, items = items, spec = state.spec)
}
