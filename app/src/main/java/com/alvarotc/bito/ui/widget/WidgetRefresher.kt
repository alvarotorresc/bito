package com.alvarotc.bito.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.alvarotc.bito.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Pushes a widget refresh whenever the data it renders changes. Any write to
 * Room or DataStore emits through [AppContainer.domainState], [AppContainer.habits]
 * or [AppContainer.settings], and this collector turns that into
 * [TodayWidget.updateAll] — including the receivers other lanes own, since
 * they wake the process and `BitoApp.onCreate` starts this collector, so
 * their writes refresh the widget without coupling to it. Rescheduling the
 * day-rotation alarm on every emission means a cutoff change moves the
 * rotation with no extra wiring.
 */
object WidgetRefresher {
    fun start(
        context: Context,
        container: AppContainer,
        scope: CoroutineScope,
    ) {
        scope.launch {
            combine(
                container.domainState.observe(),
                container.habits.observeHabits(),
                container.settings.settings,
            ) { _, _, prefs -> prefs }
                .conflate()
                .collect { prefs ->
                    TodayWidget().updateAll(context)
                    WidgetDayAlarm.schedule(context, prefs.dayCutoffMinutes)
                    delay(250)
                }
        }
    }
}
