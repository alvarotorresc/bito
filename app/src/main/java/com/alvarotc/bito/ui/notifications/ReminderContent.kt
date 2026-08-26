package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.ui.review.reviewRowsOf
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.TodayUiState

/** Maximum number of quick-log actions a single notification can offer (OS action-button cap). */
private const val MAX_QUICK_TARGETS = 3

/**
 * A pending habit that can be logged straight from the notification's action buttons.
 * [isCheck] decides the action's label: a CHECK habit is "done" in one tap (no honest
 * "+N" to show — its target is always 1), everything else must say the amount it logs.
 */
data class QuickTarget(val habitId: String, val name: String, val amount: Int, val isCheck: Boolean)

/**
 * The pure content of a reminder notification: what's still open, what can be one-tapped, and
 * where the day stands ([doneCount] of [totalCount] — the "llevas 2 de 5" the afternoon flavor
 * cites). A failed card counts toward neither, and leaves the total too: the fraction only
 * speaks of what can still be won today (anti-sargento — no rubbing in the lost one).
 */
data class ReminderPayload(
    val pendingNames: List<String>,
    val targets: List<QuickTarget>,
    val doneCount: Int,
    val totalCount: Int,
)

/**
 * Builds the reminder notification's content from [state], or `null` when there is nothing to
 * say — anti-spam: what's already fulfilled never generates noise, and a failed habit (an
 * exceeded limit, a relapsed ZERO habit) is excluded too: reminders never nag about something
 * that can no longer be fixed today, and never offer a quick action that would worsen the log
 * (anti-sargento). `targets` offers at most [MAX_QUICK_TARGETS] quick-loggable (CHECK/COUNTER)
 * pending habits, one tap = [QuickTarget.amount].
 */
fun buildReminderPayload(state: TodayUiState): ReminderPayload? {
    val pending = state.cards.filter { !it.doneToday && !it.failed }
    if (pending.isEmpty()) return null
    val targets =
        pending
            .filter { it.kind == CardKind.CHECK || it.kind == CardKind.COUNTER }
            .take(MAX_QUICK_TARGETS)
            .map {
                QuickTarget(
                    it.id,
                    it.name,
                    if (it.kind == CardKind.COUNTER) it.step else 1,
                    isCheck = it.kind == CardKind.CHECK,
                )
            }
    return ReminderPayload(
        pendingNames = pending.map { it.name },
        targets = targets,
        doneCount = state.cards.count { it.doneToday && !it.failed },
        totalCount = state.cards.count { !it.failed },
    )
}

/** Review owed: a past day unsealed, or today unsealed with something to act on. Sealing today closes the review for the day. */
fun reviewIsPending(state: TodayUiState): Boolean =
    state.pendingSealDays.isNotEmpty() || (!state.todaySealed && reviewRowsOf(state.cards, state.todaySealed).isNotEmpty())

/**
 * How many rows today's review still has to decide — the count the review nudge can honestly
 * cite. Zero with [reviewIsPending] still true means the debt is past days left unsealed, and
 * the nudge says so instead of inventing a number.
 */
fun reviewPendingCount(state: TodayUiState): Int = if (state.todaySealed) 0 else reviewRowsOf(state.cards, state.todaySealed).size
