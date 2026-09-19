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
        val pendingIntent = intentFor(context)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
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
        alarmManager.cancel(intentFor(context))
    }

    private fun intentFor(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RC,
            Intent(context, FocusReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
