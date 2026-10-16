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
 * para sus avisos. Nunca lanza: sin permiso o con el permiso retirado, no hace nada.
 */
class NoMolestar internal constructor(private val filtro: FiltroAndroid) {
    constructor(context: Context) : this(FiltroDelSistema(context))

    @Volatile
    private var puesto = false

    fun permitido(): Boolean = filtro.permitido()

    fun intentAjustes(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** Pone PRIORITY y devuelve el filtro previo; null si falla o no hay permiso. */
    fun poner(): Int? =
        try {
            if (!filtro.permitido()) {
                null
            } else {
                val previo = filtro.actual()
                filtro.poner(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                puesto = true
                previo
            }
        } catch (_: SecurityException) {
            null
        }

    /** Solo si el actual sigue siendo PRIORITY: si se cambió a mano, no se toca. */
    fun restaurar(previo: Int?) {
        puesto = false
        if (previo == null) return
        try {
            if (filtro.actual() == NotificationManager.INTERRUPTION_FILTER_PRIORITY) filtro.poner(previo)
        } catch (_: SecurityException) {
            // Permiso retirado durante la sesión: no hay nada que restaurar.
        }
    }

    /** El actual es PRIORITY y lo puso Bito. */
    fun activo(): Boolean =
        puesto &&
            try {
                filtro.actual() == NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } catch (_: SecurityException) {
                false
            }
}
