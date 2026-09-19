package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * La alarma exacta que avisa del fin de una sesion de foco. requestCode FIJO: con
 * FLAG_UPDATE_CURRENT, cada «+5» SUSTITUYE la alarma anterior en vez de acumularlas.
 * Degradado elegante cuando el permiso de alarma exacta falta, igual que ReminderScheduler:85-90
 * — un aviso de fin a las 25:03 sigue siendo un aviso de fin, y el reloj no depende de el.
 */
object FocusAlarm {
    private const val RC = 51_001

    fun schedule(
        context: Context,
        endsAtMillis: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // FLAG_UPDATE_CURRENT nunca devuelve null — solo FLAG_NO_CREATE puede (ver cancel()).
        val pendingIntent = intentFor(context, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        // Una revocacion del permiso de alarma exacta entre canScheduleExactAlarms() y el set de
        // arriba (TOCTOU) surge aqui como SecurityException — no debe tirar abajo a quien llama.
        runCatching {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // FLAG_NO_CREATE: cancelar no debe crear el PendingIntent que va a cancelar. Si no hay
        // alarma pendiente, intentFor devuelve null y no hay nada que hacer.
        intentFor(context, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    private fun intentFor(
        context: Context,
        flag: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            RC,
            Intent(context, FocusReceiver::class.java),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )
}
