package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe las acciones de los avisos de Habi (Vale, Hoy no, Más tarde). Esqueleto: no hace nada hasta que el carril
 * de avisos rellene el cuerpo.
 */
class AvisoHabiReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit

    companion object {
        const val ACTION_VALE = "com.alvarotc.bito.aviso.vale"
        const val ACTION_HOY_NO = "com.alvarotc.bito.aviso.hoy_no"
        const val ACTION_MAS_TARDE = "com.alvarotc.bito.aviso.mas_tarde"
        const val EXTRA_PROPUESTA_ID = "propuesta_id"
        const val EXTRA_AVISO_ID = "aviso_id"
        const val EXTRA_HORA = "hora"
    }
}
