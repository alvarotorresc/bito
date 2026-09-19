package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Mantiene la bandeja honesta respecto a la sesion de foco: en cuanto no hay sesion, no hay
 * notificacion. Un forzar detencion, un borrado de la tarea o un fin gestionado desde otra
 * pantalla pasan todos por aqui. Solo CANCELA — postear es cosa de quien arranca la sesion —,
 * asi que es idempotente y no pelea con nadie.
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
    }
}
