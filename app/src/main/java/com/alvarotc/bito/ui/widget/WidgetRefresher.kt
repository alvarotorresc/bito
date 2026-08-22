package com.alvarotc.bito.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ui.notifications.TrayRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Pushes a widget refresh whenever the data it renders changes. Any write to
 * Room or DataStore emits through [AppContainer.domainState], [AppContainer.habits],
 * [AppContainer.settings] or [AppContainer.rewards] (T15's owned/equipped items — needed
 * separately because `equip`/`unequip` only write `customizationItemDao`, which
 * [com.alvarotc.bito.data.repo.DomainStateRepository.observe] doesn't cover; a points-earning
 * purchase already refreshes today via its ledger row, but a pure re-equip would not without this),
 * and this collector turns that into [TodayWidget.updateAll] — including the receivers other lanes
 * own, since they wake the process and `BitoApp.onCreate` starts this collector, so their writes
 * refresh the widget without coupling to it. Rescheduling the day-rotation alarm on every emission
 * means a cutoff change moves the rotation with no extra wiring. The same in-app writes that move
 * the widget also refresh (or cancel) the GLOBAL reminder tray via [TrayRefresher], so an in-app
 * log or edit keeps the tray honest the same way a quick-action tap does.
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
                container.rewards.observeOwnedItems(),
            ) { _, _, prefs, _ -> prefs }
                .conflate()
                .collect { prefs ->
                    // A bad emission here (e.g. a widget host update throwing) must not cancel this
                    // collector — that would stop widget refreshes for the rest of the process's life,
                    // so one failure is swallowed instead of killing the loop. Cancellation is not
                    // "a failure" though — swallowing it here would leave the collector running past
                    // its own scope being cancelled.
                    runCatching {
                        TodayWidget().updateAll(context)
                        WidgetDayAlarm.schedule(context, prefs.dayCutoffMinutes)
                        TrayRefresher.refresh(context, container)
                    }.onFailure { if (it is CancellationException) throw it }
                    delay(250)
                }
        }
    }
}
