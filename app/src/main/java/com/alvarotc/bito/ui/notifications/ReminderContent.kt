package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.TodayUiState

/** Maximum number of quick-log actions a single notification can offer (OS action-button cap). */
private const val MAX_QUICK_TARGETS = 3

/** A pending habit that can be logged straight from the notification's action buttons. */
data class QuickTarget(val habitId: String, val name: String, val amount: Int)

/** The pure content of a reminder notification: what's still open, and what can be one-tapped. */
data class ReminderPayload(val pendingNames: List<String>, val targets: List<QuickTarget>)

/**
 * Builds the reminder notification's content from [state], or `null` when there is nothing to
 * say — anti-spam: what's already fulfilled never generates noise. `targets` offers at most
 * [MAX_QUICK_TARGETS] quick-loggable (CHECK/COUNTER) pending habits, one tap = [QuickTarget.amount].
 */
fun buildReminderPayload(state: TodayUiState): ReminderPayload? {
    val pending = state.cards.filter { !it.doneToday }
    if (pending.isEmpty()) return null
    val targets =
        pending
            .filter { it.kind == CardKind.CHECK || it.kind == CardKind.COUNTER }
            .take(MAX_QUICK_TARGETS)
            .map { QuickTarget(it.id, it.name, if (it.kind == CardKind.COUNTER) it.step else 1) }
    return ReminderPayload(pendingNames = pending.map { it.name }, targets = targets)
}

/** Whether today's review is still owed: something open today, or a past day left unsealed. */
fun reviewIsPending(state: TodayUiState): Boolean = state.cards.any { !it.doneToday } || state.pendingSealDays.isNotEmpty()
