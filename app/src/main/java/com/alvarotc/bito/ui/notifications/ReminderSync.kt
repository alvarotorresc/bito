package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Keeps scheduled alarms in sync with live settings and habits (tech doc §6.3). A change from
 * Settings or the habit form lands here through [AppContainer.settings]/[AppContainer.habits]
 * without that code ever knowing about alarms — this collector reprograms only when the derived
 * [ReminderScheduler.slotsOf] actually changed.
 */
object ReminderSync {
    fun start(
        context: Context,
        container: AppContainer,
        scope: CoroutineScope,
    ) {
        scope.launch {
            // Presence by default: a clean install has no reminder hours, so without this the
            // GLOBAL reminder never fires until someone visits Ajustes. Seeded before the
            // collector starts so its very first slots emission already schedules them; the
            // seeder itself is one-shot and restore-safe (see SettingsRepository). A failed
            // seed (DataStore IO) must not kill the sync loop below — same guard as the
            // collect body; cancellation keeps propagating.
            runCatching { container.settings.seedDefaultReminders() }
                .onFailure { if (it is CancellationException) throw it }
            combine(container.settings.settings, container.habits.observeHabits()) { prefs, entities ->
                ReminderScheduler.slotsOf(prefs, entities)
            }
                .distinctUntilChanged()
                .collect { slots ->
                    // A TOCTOU exact-alarm revocation surfaces here as a SecurityException; letting it
                    // escape would cancel this collector and stop syncing reminders for the rest of the
                    // process's life, so one bad emission is swallowed instead of killing the loop.
                    // scheduleAll isn't suspend, so it can't itself throw CancellationException, but
                    // the guard is added for the same reasoning as BackupSync/WidgetRefresher in case
                    // that ever changes.
                    runCatching {
                        ReminderScheduler.scheduleAll(context, slots, System.currentTimeMillis(), ZoneId.systemDefault())
                    }.onFailure { if (it is CancellationException) throw it }
                }
        }
    }
}
