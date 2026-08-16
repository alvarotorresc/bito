package com.alvarotc.bito.ui.widget

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

data class WidgetModel(val done: Int, val total: Int, val items: List<WidgetItem>)

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
    return WidgetModel(done = visible.count { it.doneToday }, total = visible.size, items = items)
}
