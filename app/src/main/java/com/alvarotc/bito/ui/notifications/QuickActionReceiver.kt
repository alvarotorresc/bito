package com.alvarotc.bito.ui.notifications

import android.app.NotificationManager
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
 * straight through [QuickActionUseCase], clears the notification the action came from, and only
 * ever refreshes or kills the GLOBAL tray — never conjures it. Spec §6.2's anti-spam rule ("a
 * notification only fires when something is pending") gates when a *scheduled* reminder may
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
                handle(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        intent: Intent,
    ) {
        val habitId = intent.getStringExtra("habitId") ?: return
        val amount = intent.getIntExtra("amount", 1)
        val notificationId = intent.getIntExtra("notificationId", Notifier.REMINDER_ID)
        NotificationChannels.ensure(context)
        val container = (context.applicationContext as BitoApp).container
        val useCase =
            QuickActionUseCase(container.journal, container.reconciler, container.domainState, container.habits, container.settings)
        val payload = useCase.log(habitId, amount)
        if (notificationId != Notifier.REMINDER_ID) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        if (payload == null) {
            Notifier.cancelReminder(context)
        } else if (notificationId == Notifier.REMINDER_ID || globalReminderIsActive(context)) {
            Notifier.showReminder(context, payload)
        }
    }

    /** Whether the GLOBAL tray is currently showing — never conjured, only refreshed or killed. */
    private fun globalReminderIsActive(context: Context): Boolean =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .any { it.id == Notifier.REMINDER_ID }
}
