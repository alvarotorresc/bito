package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Fires when the user grants or revokes SCHEDULE_EXACT_ALARM from the system Settings screen
 * (tech doc §6.3). Every alarm scheduled under the old permission state may now be wrong —
 * degraded when it could be exact, or vice versa — so this reprograms all of them from live
 * state, the same fix [BootReceiver] applies after a reboot wipes every alarm outright. The
 * broadcast itself only exists on SDK 31+; the guard below is repeated even though the manifest
 * intent-filter alone would make it unreachable pre-31, so the constant reference itself is
 * provably safe, not just reachability-safe (same reasoning as `SettingsScreen`'s own repeated
 * SDK 31+ guard).
 */
class ExactAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (Build.VERSION.SDK_INT < 31) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // A DataStore/Room IO failure reading settings/habits fresh off this callback must
                // not crash the process — finish() below still has to run so the system doesn't ANR us.
                runCatching {
                    val container = (context.applicationContext as BitoApp).container
                    reschedule(context, container.settings, container.habits)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** Recomputes every slot from live state and reschedules — the testable seam behind [onReceive]. */
        internal suspend fun reschedule(
            context: Context,
            settings: SettingsRepository,
            habits: HabitsRepository,
        ) {
            val prefs = settings.settings.first()
            val entities = habits.observeHabits().first()
            ReminderScheduler.scheduleAll(
                context,
                ReminderScheduler.slotsOf(prefs, entities),
                System.currentTimeMillis(),
                ZoneId.systemDefault(),
            )
        }
    }
}
