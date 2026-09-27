package com.alvarotc.bito.ui.notifications

import android.content.Context
import android.os.SystemClock
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.settings.FocusClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
 *
 * [recoverOnStart] corre en cada arranque de proceso, en paralelo con los dos colectores — no solo
 * cuando el usuario abre la app: tambien cuando el proceso nace por un recordatorio, un widget, la
 * medianoche o una accion rapida. Un forzar detencion del sistema mata la alarma y la notificacion
 * en el acto, pero la sesion sigue en el store — `FocusViewModel.init` la rearma, pero solo si la
 * pantalla de foco llega a construirse. Esto cubre el resto, en silencio y solo para una sesion viva.
 */
object FocusSync {
    /**
     * [onCycle] no es parte del comportamiento: es el gancho de sincronizacion para tests, que se
     * dispara al final de cada evaluacion del tercer colector (haya limpiado o no). En produccion
     * se queda en su valor por defecto, un no-op.
     */
    fun start(
        context: Context,
        container: AppContainer,
        scope: CoroutineScope,
        onCycle: () -> Unit = {},
    ) {
        scope.launch { recoverOnStart(context, container) }
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
                    onCycle()
                }
        }
    }

    /**
     * Solo una sesion VIVA se rearma (alarma + bandeja, las dos silenciosas e idempotentes con
     * `FocusViewModel.init`). Una sesion vencida no produce nada: el aviso de "se acabo el tiempo"
     * ya lo dio [FocusReceiver] cuando la alarma disparo, y como esto corre en cada arranque de
     * proceso, repetirlo aqui lo haria sonar con cada recordatorio, widget o medianoche que
     * despierte el proceso — y pelearia con [BootReceiver], que limpia sin avisar una sesion
     * vencida al reiniciar. La pantalla de foco ya pinta el 00:00 con sus botones cuando el usuario
     * entra. Una tarea borrada mientras tanto la deja para el segundo colector de arriba, que ya
     * sabe limpiarla; aqui no hay nada seguro que postear sin su titulo.
     */
    internal suspend fun recoverOnStart(
        context: Context,
        container: AppContainer,
    ) {
        runCatching {
            val session = container.focus.session.first() ?: return@runCatching
            val task = container.tasks.task(session.taskId) ?: return@runCatching
            val remaining = FocusClock.remainingMillis(session, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            if (remaining <= 0) return@runCatching
            NotificationChannels.ensure(context)
            Notifier.showFocus(context, task.title, session.endsAtMillis)
            FocusAlarm.schedule(context, session.endsAtMillis)
        }.onFailure { if (it is CancellationException) throw it }
    }
}
