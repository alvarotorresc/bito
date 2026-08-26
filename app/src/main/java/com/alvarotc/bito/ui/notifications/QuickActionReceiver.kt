package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when the user taps a quick-log action button on a notification — no app launch. Logs
 * straight through [QuickActionUseCase], clears the notification the action came from, and
 * hands the GLOBAL tray off to [TrayRefresher] — never conjures it. Spec §6.2's anti-spam rule
 * ("a notification only fires when something is pending") gates when a *scheduled* reminder may
 * sound; a user tapping "Done" on their own personal reminder never asked for a fresh global
 * summary to appear out of nowhere. Refreshing one that's already showing keeps it honest;
 * creating one from nothing is noise.
 */
class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // A journal write or DataStore read failing mid-tap must not crash the process —
                // finish() below still has to run so the system doesn't ANR us.
                runCatching { handle(context, intent) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        intent: Intent,
    ) {
        val habitId = intent.getStringExtra(Notifier.EXTRA_HABIT_ID) ?: return
        val amount = intent.getIntExtra(Notifier.EXTRA_AMOUNT, 1)
        val notificationId = intent.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, Notifier.REMINDER_ID)
        val container = (context.applicationContext as BitoApp).container
        NotificationChannels.ensure(context)
        val useCase =
            QuickActionUseCase(container.journal, container.reconciler, container.domainState, container.habits, container.settings)
        run(
            context = context,
            notificationId = notificationId,
            log = { useCase.log(habitId, amount).perfectDayReached },
            notifyPerfectDay = { reached -> PerfectDayNotifier.maybeNotify(context, container, reached) },
            refreshTray = { treatAsActive -> TrayRefresher.refresh(context, container, treatAsActive = treatAsActive) },
        )
    }

    companion object {
        /**
         * The testable seam behind [onReceive], with the write and the two follow-ups injected.
         * A [notifyPerfectDay] failure (e.g. POST_NOTIFICATIONS revoked mid-flight) used to ride
         * the outer catch-all in [onReceive] and silently skip everything after it — the same
         * notifier gap the widget's `LogHabitAction` closed in the 2026-08-23 QA round. Guarded
         * the same way here: the tapped notification is still cleared and the tray still
         * refreshed, and cancellation keeps propagating (it is not "a failure").
         */
        internal suspend fun run(
            context: Context,
            notificationId: Int,
            log: suspend () -> Boolean,
            notifyPerfectDay: suspend (reachedNow: Boolean) -> Unit,
            refreshTray: suspend (treatAsActive: Boolean) -> Unit,
        ) {
            val reached = log()
            runCatching { notifyPerfectDay(reached) }
                .onFailure { if (it is CancellationException) throw it }
            if (notificationId != Notifier.REMINDER_ID) {
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
            // The action came from the GLOBAL tray's own button when notificationId == REMINDER_ID —
            // that always refreshes it, even if the system no longer lists it as active.
            refreshTray(notificationId == Notifier.REMINDER_ID)
        }
    }
}
