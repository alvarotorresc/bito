package com.alvarotc.bito.ui.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.R

/** The notification channels Bito creates: reminders, Habi's notices, celebrations and the session's ongoing notice. */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val HABI = "habi"
    const val CELEBRATIONS = "celebrations"

    /** El aviso fijo de la sesión: importancia baja, sin sonido ni vibración (spec pieza 5 §11.4). */
    const val SESION = "sesion"

    /** El canal del repaso diario, que los avisos de Habi sustituyen (spec §6.4). Se borra, no hay usuarios que conservar. */
    private const val REVIEW_ANTIGUO = "review"

    /** Creates all the channels if missing and drops the old review one. Idempotent, safe to call on every app start and before every notification. */
    fun ensure(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.deleteNotificationChannel(REVIEW_ANTIGUO)
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_reminders))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(HABI, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.avisohabi_canal))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CELEBRATIONS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_celebrations))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(SESION, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.ses_canal))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
    }
}
