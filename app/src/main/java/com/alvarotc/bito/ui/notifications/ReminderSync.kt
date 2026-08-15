package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
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
            combine(container.settings.settings, container.habits.observeHabits()) { prefs, entities ->
                ReminderScheduler.slotsOf(prefs, entities)
            }
                .distinctUntilChanged()
                .collect { slots ->
                    ReminderScheduler.scheduleAll(context, slots, System.currentTimeMillis(), ZoneId.systemDefault())
                }
        }
    }
}
