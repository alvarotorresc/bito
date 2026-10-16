package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recibe las acciones de los avisos de Habi (Vale, Hoy no o Luego, Más tarde y Mañana) y se las pasa a
 * [AvisosHabiAndroid.accion]. Una acción desconocida se descarta antes de tocar nada.
 */
class AvisoHabiReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in ACCIONES) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Un fallo de Room o de DataStore no debe tumbar el proceso; finish() tiene que llegar.
                runCatching {
                    val container = (context.applicationContext as BitoApp).container
                    NotificationChannels.ensure(context)
                    AvisosHabiAndroid(context.applicationContext, contenedor = { container }).accion(intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_VALE = "com.alvarotc.bito.aviso.vale"
        const val ACTION_HOY_NO = "com.alvarotc.bito.aviso.hoy_no"
        const val ACTION_MAS_TARDE = "com.alvarotc.bito.aviso.mas_tarde"
        const val EXTRA_PROPUESTA_ID = "propuesta_id"
        const val EXTRA_AVISO_ID = "aviso_id"
        const val EXTRA_HORA = "hora"

        /** «Mañana» en el aviso de la noche: lo quita. */
        const val ACTION_MANANA = "com.alvarotc.bito.aviso.manana"

        /** La respuesta que lleva [ACTION_HOY_NO]: `HOY_NO` o `LUEGO`. */
        const val EXTRA_RESPUESTA = "respuesta"

        private val ACCIONES = setOf(ACTION_VALE, ACTION_HOY_NO, ACTION_MAS_TARDE, ACTION_MANANA)
    }
}
