package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Mantiene la bandeja honesta respecto a la sesion de foco: en cuanto no hay sesion, no hay
 * notificacion. Un forzar detencion, un borrado de la tarea o un fin gestionado desde otra
 * pantalla pasan todos por aqui. El primer colector solo CANCELA — postear es cosa de quien
 * arranca la sesion —, asi que es idempotente y no pelea con nadie.
 *
 * El segundo colector es el UNICO dueño de la limpieza de una sesion viva cuya tarea ya no existe
 * (spec §8.5 "Recuperacion"): antes vivia en `FocusViewModel.init`, pero un ViewModel muere con la
 * pantalla y la sesion sigue en el store aunque nadie la mire — el limpiador tiene que vivir donde
 * vive la sesion, no donde vive la pantalla. Sin escribir DONE ni ATTEMPT: la tarea ya no esta, no
 * hubo un "termino" ni un "lo dejo".
 */
object FocusSync {
    fun start(
        context: Context,
        container: AppContainer,
        scope: CoroutineScope,
    ) {
        scope.launch {
            container.focus.session.collect { session ->
                if (session == null) {
                    runCatching { Notifier.cancelFocus(context) }
                        .onFailure { if (it is CancellationException) throw it }
                }
            }
        }
        scope.launch {
            combine(container.focus.session, container.domainState.observe()) { session, state -> session to state }
                // collect, no collectLatest: el propio focus.clear() de abajo reemite la sesion ya
                // nula, y collectLatest cancelaria este mismo bloque a mitad de limpieza — segun
                // quien ganara la carrera, antes de cancelar la bandeja y la alarma.
                .collect { (session, state) ->
                    if (session != null && state.tasks.none { it.id == session.taskId }) {
                        // Tres efectos independientes, cada uno con su propio guard: si uno falla
                        // (p. ej. una IOException del DataStore), los otros dos igual se intentan —
                        // acoplarlos bajo un unico runCatching dejaria la bandeja o la alarma sin
                        // cancelar cada vez que el primero fallara, mismo criterio que ReminderSync.
                        runCatching { container.focus.clear() }.onFailure { if (it is CancellationException) throw it }
                        runCatching { Notifier.cancelFocus(context) }.onFailure { if (it is CancellationException) throw it }
                        runCatching { FocusAlarm.cancel(context) }.onFailure { if (it is CancellationException) throw it }
                    }
                }
        }
    }
}
