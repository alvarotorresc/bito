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
 * refreshes the GLOBAL tray honestly from the recalculated state (dead when nothing's left
 * pending, so what's already fulfilled never keeps nagging).
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
        } else {
            Notifier.showReminder(context, payload)
        }
    }
}
