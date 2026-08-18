package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.BitoApp
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
        NotificationChannels.ensure(context)
        val container = (context.applicationContext as BitoApp).container
        val useCase =
            QuickActionUseCase(container.journal, container.reconciler, container.domainState, container.habits, container.settings)
        useCase.log(habitId, amount)
        if (notificationId != Notifier.REMINDER_ID) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        // The action came from the GLOBAL tray's own button when notificationId == REMINDER_ID —
        // that always refreshes it, even if the system no longer lists it as active.
        TrayRefresher.refresh(context, container, treatAsActive = notificationId == Notifier.REMINDER_ID)
    }
}
