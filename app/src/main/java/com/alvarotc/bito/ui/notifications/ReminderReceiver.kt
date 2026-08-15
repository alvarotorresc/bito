package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Fires on every scheduled reminder alarm (tech doc §6.3). Never trusts the alarm itself: it
 * always revalidates against live state through [ReminderUseCase] before ever posting a
 * notification, and reprograms the next occurrence for whatever slot it resolved to — orphaned
 * alarms (a deleted hour, an archived habit) self-heal without notifying.
 */
class ReminderReceiver : BroadcastReceiver() {
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
        val kindName = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY) ?: return
        NotificationChannels.ensure(context)
        val container = (context.applicationContext as BitoApp).container
        val useCase = ReminderUseCase(container.domainState, container.habits, container.settings)
        when (val outcome = useCase.evaluate(kindName, key)) {
            is ReminderUseCase.Outcome.Stale -> Unit
            is ReminderUseCase.Outcome.Remind -> {
                Notifier.showReminder(context, outcome.payload)
                reschedule(context, outcome.slot)
            }
            is ReminderUseCase.Outcome.RemindHabit -> {
                Notifier.showSingleHabit(context, outcome.slot.key, outcome.name, outcome.target)
                reschedule(context, outcome.slot)
            }
            is ReminderUseCase.Outcome.Review -> {
                Notifier.showReview(context)
                reschedule(context, outcome.slot)
            }
            is ReminderUseCase.Outcome.Silent -> reschedule(context, outcome.slot)
        }
    }

    private fun reschedule(
        context: Context,
        slot: Slot,
    ) = ReminderScheduler.scheduleSlot(context, slot, System.currentTimeMillis(), ZoneId.systemDefault())
}
