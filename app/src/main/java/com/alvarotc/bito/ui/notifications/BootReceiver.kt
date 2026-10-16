package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.sesion.fuera.SesionSync
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
        reprogramar(context, container.settings, container.habits, container.avisosHabi)

        // La sesión de la pieza 5: tras reiniciar se pierden las alarmas y las notificaciones; la viva se rearma y la
        // que acabó avisa.
        recuperarSesion(context)

        // El foco de antes, hasta que la sesión nueva lo sustituya del todo. Una sesion que ya paso se limpia SIN
        // notificar: un «se acabo el tiempo» de hace seis horas es exactamente el ruido que esta app rechaza.
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

    companion object {
        /** La sesión de foco, recalculada con el reloj de pared (el contador de arranques ya cambió). */
        internal suspend fun recuperarSesion(context: Context) = SesionSync.recuperar(context)

        /** Lo que se reprograma tras reiniciar: los recordatorios de siempre y los avisos de Habi. La costura que prueban los tests. */
        internal suspend fun reprogramar(
            context: Context,
            settings: SettingsRepository,
            habits: HabitsRepository,
            avisos: AvisosHabi,
        ) {
            val prefs = settings.settings.first()
            val entities = habits.observeHabits().first()
            ReminderScheduler.scheduleAll(
                context,
                ReminderScheduler.slotsOf(prefs, entities),
                System.currentTimeMillis(),
                ZoneId.systemDefault(),
            )
            // Los avisos de Habi de hoy, con los aceptados pendientes (spec §6.4); los de otros días pierden su alarma.
            avisos.replanificar()
        }
    }
}
