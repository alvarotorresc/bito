package com.alvarotc.bito.sesion.fuera

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier

/**
 * El aviso fijo de la sesión en la bandeja: lo que dice según el tramo (spec pieza 5 §3.3.9, §11.3). La
 * cuenta atrás la pinta Android; en pausa (H17) o con el descanso esperando, la cifra queda quieta en el texto.
 */
object PresenciaSesion {
    /** Publica el aviso del tramo en curso. Una sesión terminada no se pinta aquí: es [mostrarFin]. */
    fun mostrar(
        context: Context,
        r: RegistroSesion,
        ahora: Ahora,
        titulo: String,
        voz: Personality,
    ) {
        val (cuerpo, finPared) = texto(context, r, ahora) ?: return
        NotificationChannels.ensure(context)
        Notifier.mostrarSesion(context, titulo, cuerpo, finPared, voz)
    }

    /** El mismo aviso sin publicarlo: es el del servicio en primer plano. */
    fun construir(
        context: Context,
        r: RegistroSesion,
        ahora: Ahora,
        titulo: String,
        voz: Personality,
    ): Notification? {
        val (cuerpo, finPared) = texto(context, r, ahora) ?: return null
        NotificationChannels.ensure(context)
        return Notifier.notificacionSesion(context, titulo, cuerpo, finPared, voz)
    }

    /** El cuerpo y el fin (pared) de la cuenta atrás del aviso; null si la sesión ya terminó. */
    private fun texto(
        context: Context,
        r: RegistroSesion,
        ahora: Ahora,
    ): Pair<String, Long?>? {
        val instante = RelojSesion.en(r, ahora)
        val tramo = instante.actual ?: return null
        return when {
            instante.pausado -> quieto(context, R.string.ses_notif_en_pausa, instante.restaMillis) to null
            instante.esperandoDescanso -> quieto(context, R.string.ses_notif_descanso, instante.restaMillis) to null
            else -> {
                val cuerpo = context.getString(if (tramo is Tramo.Descanso) R.string.ses_notif_descanso else R.string.ses_notif_en_marcha)
                cuerpo to instante.finTramoEnSesion?.let { RelojSesion.paredDe(r, it, ahora) }
            }
        }
    }

    fun mostrarFin(
        context: Context,
        titulo: String,
        voz: Personality,
    ) {
        NotificationChannels.ensure(context)
        Notifier.mostrarSesionFin(context, titulo, voz)
    }

    fun cancelar(context: Context) = Notifier.cancelarSesion(context)

    /** Hay en la bandeja un aviso fijo de la sesión (no el viejo del foco, que va por otro canal). */
    fun hayAvisoFijo(context: Context): Boolean =
        NotificationManagerCompat.from(context).activeNotifications.any {
            it.id == Notifier.FOCUS_ID && it.notification.channelId == NotificationChannels.SESION
        }

    private fun quieto(
        context: Context,
        texto: Int,
        restaMillis: Long,
    ): String = context.getString(R.string.sesf_pausa_resta, context.getString(texto), FormatoSesion.cuenta(restaMillis))
}
