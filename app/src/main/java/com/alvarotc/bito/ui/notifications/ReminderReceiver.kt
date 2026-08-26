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
                // DataStore IO reading live state or a TOCTOU SecurityException rescheduling the
                // next occurrence must not crash the process — finish() below still has to run so
                // the system doesn't ANR us.
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
        val kindName = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY) ?: return
        NotificationChannels.ensure(context)
        val container = (context.applicationContext as BitoApp).container
        val useCase = ReminderUseCase(container.domainState, container.habits, container.settings)
        when (val outcome = useCase.evaluate(kindName, key)) {
            is ReminderUseCase.Outcome.Stale -> Unit
            is ReminderUseCase.Outcome.Remind -> {
                // The slot's own configured hour picks the flavor — the alarm fires at it, so
                // "when this was scheduled for" and "now" agree up to alarm-delivery slop.
                Notifier.showReminder(
                    context,
                    outcome.payload,
                    outcome.personality,
                    outcome.userName,
                    outcome.slot.minutesOfDay,
                )
                reschedule(context, outcome.slot)
            }
            is ReminderUseCase.Outcome.RemindHabit -> {
                Notifier.showSingleHabit(context, outcome.slot.key, outcome.name, outcome.target)
                reschedule(context, outcome.slot)
            }
            is ReminderUseCase.Outcome.Review -> {
                Notifier.showReview(context, outcome.personality, outcome.userName, outcome.pendingCount)
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
