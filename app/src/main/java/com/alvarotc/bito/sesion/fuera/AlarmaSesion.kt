package com.alvarotc.bito.sesion.fuera

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.RelojSesion

/**
 * La alarma de la siguiente frontera de la sesión (spec pieza 5 §11.3): una sola, siempre la próxima.
 * Se calcula en `elapsedRealtime` (`ELAPSED_REALTIME_WAKEUP`): un cambio de hora después de programarla no
 * la mueve. Código de petición propio, distinto del de `FocusAlarm` (51 001).
 */
object AlarmaSesion {
    private const val RC = 51_002

    /** El índice del tramo que acaba con esta alarma: el receptor lo compara con el de ahora. */
    const val EXTRA_TRAMO = "tramo"

    /** Programa la frontera del tramo en curso; sin frontera (pausa, descanso esperando, terminada) no deja alarma. */
    fun programar(
        context: Context,
        r: RegistroSesion,
        ahora: Ahora,
    ) {
        val instante = RelojSesion.en(r, ahora)
        val fin = instante.finTramoEnSesion ?: return cancelar(context)
        val disparo = ahora.elapsedMillis + (fin - instante.transcurridoMillis)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = intent(context, PendingIntent.FLAG_UPDATE_CURRENT, instante.tramo)!!
        val exactas = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        // Retirar el permiso entre la comprobación y el set llega aquí como SecurityException.
        runCatching {
            if (exactas) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, disparo, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, disparo, pendingIntent)
            }
        }
    }

    fun cancelar(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        intent(context, PendingIntent.FLAG_NO_CREATE, -1)?.let { alarmManager.cancel(it) }
    }

    private fun intent(
        context: Context,
        flag: Int,
        tramo: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            RC,
            Intent(context, SesionReceiver::class.java).putExtra(EXTRA_TRAMO, tramo),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )
}
