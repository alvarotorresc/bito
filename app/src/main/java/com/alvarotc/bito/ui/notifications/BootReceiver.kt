package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // A DataStore IO failure reading settings/habits fresh off boot must not crash the
                // process — finish() below still has to run so the system doesn't ANR us.
                runCatching { handle(context) }
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

        // Tras reiniciar, las alarmas y las notificaciones se pierden las dos. Una sesion de foco
        // cuyo fin sigue en el futuro se reprograma y se vuelve a postear; una que ya paso se
        // limpia SIN notificar: un «se acabo el tiempo» de hace seis horas es exactamente el
        // ruido que esta app rechaza.
        val session = container.focus.session.first()
        if (session != null) {
            if (session.endsAtMillis > System.currentTimeMillis()) {
                FocusAlarm.schedule(context, session.endsAtMillis)
                container.tasks.task(session.taskId)?.let { Notifier.showFocus(context, it.title, session.endsAtMillis) }
            } else {
                container.focus.clear()
                Notifier.cancelFocus(context)
            }
        }
    }
}
