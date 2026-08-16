package com.alvarotc.bito.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.alvarotc.bito.BitoApp

/** Runs a widget tap: logs the habit and refreshes every instance with the new state. */
class LogHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val habitId = parameters[HABIT_ID] ?: return
        val amount = parameters[AMOUNT] ?: 1
        val container = (context.applicationContext as BitoApp).container
        WidgetLogger(container.journal, container.reconciler, container.settings).log(habitId, amount)
        TodayWidget().updateAll(context)
    }

    companion object {
        val HABIT_ID = ActionParameters.Key<String>("habitId")
        val AMOUNT = ActionParameters.Key<Int>("amount")
    }
}
