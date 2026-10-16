package com.alvarotc.bito.sesion

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Lo mínimo del filtro de interrupciones de Android que usa [NoMolestar]. */
internal interface FiltroAndroid {
    fun permitido(): Boolean

    fun actual(): Int

    /** Puede lanzar [SecurityException] si el permiso se ha retirado. */
    fun poner(filtro: Int)
}

private class FiltroDelSistema(context: Context) : FiltroAndroid {
    private val manager = context.applicationContext.getSystemService(NotificationManager::class.java)

    override fun permitido() = manager.isNotificationPolicyAccessGranted

    override fun actual() = manager.currentInterruptionFilter

    override fun poner(filtro: Int) = manager.setInterruptionFilter(filtro)
}

/**
 * El filtro de interrupciones de Android durante la sesión (spec §10). No es el silencio de Bito
 * para sus avisos. Nunca lanza (ninguna RuntimeException del sistema): sin permiso o con el permiso retirado, no hace nada.
 */
class NoMolestar internal constructor(private val filtro: FiltroAndroid) {
    constructor(context: Context) : this(FiltroDelSistema(context))

    fun permitido(): Boolean =
        try {
            filtro.permitido()
        } catch (_: RuntimeException) {
            false
        }

    fun intentAjustes(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * Pone PRIORITY y devuelve el filtro previo, que solo puede ser «todo permitido»: un filtro más
     * estricto o desconocido no se toca (devuelve null), así que nunca se afloja lo que la persona puso.
     * También null si falla o no hay permiso.
     */
    fun poner(): Int? =
        try {
            if (!filtro.permitido()) {
                null
            } else {
                val previo = filtro.actual()
                if (previo != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    null
                } else {
                    filtro.poner(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    previo
                }
            }
        } catch (_: RuntimeException) {
            null
        }

    /** Solo si el actual sigue siendo PRIORITY: si se cambió a mano, no se toca. */
    fun restaurar(previo: Int?) {
        if (previo == null || previo == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        try {
            if (filtro.actual() == NotificationManager.INTERRUPTION_FILTER_PRIORITY) filtro.poner(previo)
        } catch (_: RuntimeException) {
            // Permiso retirado o sistema caído: no hay nada que restaurar.
        }
    }

    /**
     * El actual es PRIORITY y lo puso Bito: lo dice el [filtroPrevio] del registro de la sesión
     * (distinto de null), que sobrevive a que maten el proceso.
     */
    fun activo(filtroPrevio: Int? = null): Boolean =
        filtroPrevio != null &&
            try {
                filtro.actual() == NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } catch (_: RuntimeException) {
                false
            }
}
