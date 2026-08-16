package com.alvarotc.bito.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fires at the logical-midnight cutoff: rolls the widget over and reschedules itself. */
class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // A DataStore IO failure at rotation time (e.g. a corrupted preferences file) must not
                // crash the process — finish() below still has to run so the system doesn't ANR us.
                runCatching {
                    TodayWidget().updateAll(context)
                    val container = (context.applicationContext as BitoApp).container
                    val cutoff = container.settings.settings.first().dayCutoffMinutes
                    WidgetDayAlarm.schedule(context, cutoff)
                }
            } finally {
                result.finish()
            }
        }
    }
}
