package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Fires once on `BOOT_COMPLETED`: every alarm the OS held is gone, so this reprograms all of them
 * from live state (tech doc §6.3, "reprogramación en boot").
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context) {
        val container = (context.applicationContext as BitoApp).container
        val prefs = container.settings.settings.first()
        val entities = container.habits.observeHabits().first()
        ReminderScheduler.scheduleAll(
            context,
            ReminderScheduler.slotsOf(prefs, entities),
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
        )
    }
}
