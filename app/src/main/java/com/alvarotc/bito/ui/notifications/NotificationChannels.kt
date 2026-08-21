package com.alvarotc.bito.ui.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.R

/** The three notification channels Bito ever creates: reminders, the daily review nudge, and celebrations. */
object NotificationChannels {
    const val REMINDERS = "reminders"
    const val REVIEW = "review"
    const val CELEBRATIONS = "celebrations"

    /** Creates all three channels if missing. Idempotent — safe to call on every app start and before every notification. */
    fun ensure(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_reminders))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(REVIEW, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_review))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CELEBRATIONS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_celebrations))
                .build(),
        )
    }
}
