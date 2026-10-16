package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.sesion.fuera.PuertaFuera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Fires on every scheduled reminder alarm (tech doc §6.3). Never trusts the alarm itself: it
 * always revalidates against live state through [ReminderUseCase] before ever posting a
 * notification, and reprograms the next occurrence for whatever slot it resolved to — orphaned
 * alarms (a deleted hour, an archived habit) self-heal without notifying.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // DataStore IO reading live state or a TOCTOU SecurityException rescheduling the
                // next occurrence must not crash the process — finish() below still has to run so
                // the system doesn't ANR us.
                runCatching { handle(context, intent) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        intent: Intent,
    ) {
        val kindName = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_KEY) ?: return
        NotificationChannels.ensure(context)
        val container = (context.applicationContext as BitoApp).container
        procesar(context, container, kindName, key, retener = true)
    }

    internal companion object {
        /** Un aviso que esperaba una sesión y ya puede salir: se evalúa otra vez contra el estado de ahora. */
        suspend fun reevaluar(
            context: Context,
            container: AppContainer,
            kindName: String,
            key: String,
        ) {
            NotificationChannels.ensure(context)
            procesar(context, container, kindName, key, retener = false)
        }

        /**
         * Con [retener] y una sesión viva, un aviso que iba a publicarse queda apuntado para cuando acabe la parte con
         * tiempo (decisión 8); su alarma sigue encadenada. Lo que no se publicaba (viejo, en silencio) se resuelve igual.
         */
        suspend fun procesar(
            context: Context,
            container: AppContainer,
            kindName: String,
            key: String,
            retener: Boolean,
        ) {
            val avisos = container.avisosHabiAndroid
            val useCase =
                ReminderUseCase(container.domainState, container.habits, container.settings) { kind, key -> avisos.evaluar(kind, key) }
            val outcome = useCase.evaluate(kindName, key)
            val publica = outcome !is ReminderUseCase.Outcome.Stale && outcome !is ReminderUseCase.Outcome.Silent
            if (retener && publica && PuertaFuera.entornoDe(context).retenidos.retenerSiHaySesion(kindName, key)) {
                ranuraDe(outcome)?.let { reschedule(context, it) }
                return
            }
            publicar(context, container, outcome)
        }

        private fun ranuraDe(outcome: ReminderUseCase.Outcome): Slot? =
            when (outcome) {
                is ReminderUseCase.Outcome.Remind -> outcome.slot
                is ReminderUseCase.Outcome.RemindHabit -> outcome.slot
                is ReminderUseCase.Outcome.Manana -> outcome.slot
                is ReminderUseCase.Outcome.Noche -> outcome.slot
                is ReminderUseCase.Outcome.Tasks -> outcome.slot
                is ReminderUseCase.Outcome.Breathing -> outcome.slot
                else -> null
            }

        private suspend fun publicar(
            context: Context,
            container: AppContainer,
            outcome: ReminderUseCase.Outcome,
        ) {
            when (outcome) {
                is ReminderUseCase.Outcome.Stale -> Unit
                is ReminderUseCase.Outcome.Remind -> {
                    // The slot's own configured hour picks the flavor — the alarm fires at it, so
                    // "when this was scheduled for" and "now" agree up to alarm-delivery slop.
                    Notifier.showReminder(
                        context,
                        outcome.payload,
                        outcome.personality,
                        outcome.userName,
                        outcome.slot.minutesOfDay,
                    )
                    reschedule(context, outcome.slot)
                }
                is ReminderUseCase.Outcome.RemindHabit -> {
                    Notifier.showSingleHabit(context, outcome.slot.key, outcome.name, outcome.target)
                    reschedule(context, outcome.slot)
                }
                is ReminderUseCase.Outcome.Silent -> reschedule(context, outcome.slot)
                is ReminderUseCase.Outcome.Manana -> {
                    Notifier.showManana(context, outcome.propuesta, outcome.titulo, outcome.personality, outcome.mood)
                    reschedule(context, outcome.slot)
                }
                is ReminderUseCase.Outcome.Noche -> {
                    Notifier.showNoche(context, outcome.titulo, outcome.cuerpo, outcome.personality)
                    reschedule(context, outcome.slot)
                }
                // Un aceptado suena una vez: no se reprograma, queda LANZADO. «Más tarde» lo vuelve a poner pendiente.
                is ReminderUseCase.Outcome.Aceptado -> {
                    Notifier.showRecordatorioAceptado(
                        context,
                        outcome.avisoId,
                        outcome.indice,
                        outcome.nombre,
                        outcome.cuerpo,
                        outcome.target,
                        outcome.masTarde,
                        outcome.personality,
                    )
                    container.avisosAceptados.cambiarEstado(outcome.avisoId, EstadoAviso.LANZADO)
                }
                is ReminderUseCase.Outcome.Tasks -> {
                    Notifier.showTasks(context, outcome.titles, outcome.pendingCount, outcome.personality, outcome.userName)
                    reschedule(context, outcome.slot)
                }
                is ReminderUseCase.Outcome.Breathing -> {
                    Notifier.showBreathing(context, outcome.personality, outcome.userName)
                    reschedule(context, outcome.slot)
                }
            }
        }

        private fun reschedule(
            context: Context,
            slot: Slot,
        ) = ReminderScheduler.scheduleSlot(context, slot, System.currentTimeMillis(), ZoneId.systemDefault())
    }
}
