package com.alvarotc.bito.ui.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.MainActivity
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.theme.Hoja

/**
 * Posts Bito's local notifications from already-decided content — [ReminderUseCase] is the only
 * caller that decides *whether* to notify; this object only ever renders what it's handed.
 * Silently skips posting when the user has disabled notifications (API 33+ reality): scheduling
 * the next alarm never depends on whether this actually reached the tray.
 */
object Notifier {
    const val REMINDER_ID = 1
    const val REVIEW_ID = 2

    /** The GLOBAL reminder: one or more habits still open, up to three quick-log actions. */
    fun showReminder(
        context: Context,
        payload: ReminderPayload,
    ) {
        val title =
            if (payload.pendingNames.size == 1) {
                context.getString(R.string.notif_reminder_title_one)
            } else {
                context.getString(R.string.notif_reminder_title_many, payload.pendingNames.size)
            }
        val style = NotificationCompat.InboxStyle()
        payload.pendingNames.forEach(style::addLine)
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(title)
                .setStyle(style)
        payload.targets.forEach { target ->
            builder.addAction(
                0,
                context.getString(R.string.notif_target_done, target.name),
                quickActionIntent(context, target.habitId, target.amount),
            )
        }
        notify(context, REMINDER_ID, builder)
    }

    /** The HABIT reminder: a single habit's own slot, still open and unfailed. */
    fun showSingleHabit(
        context: Context,
        target: QuickTarget,
    ) {
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(context.getString(R.string.notif_reminder_single_title, target.name))
                .setContentText(context.getString(R.string.notif_reminder_single_body))
                .addAction(
                    0,
                    context.getString(R.string.notif_action_done),
                    quickActionIntent(context, target.habitId, target.amount),
                )
        if (target.amount > 1) {
            builder.addAction(
                0,
                context.getString(R.string.notif_action_add, target.amount),
                quickActionIntent(context, target.habitId, target.amount),
            )
        }
        notify(context, REMINDER_ID, builder)
    }

    /** The REVIEW nudge: something is still unsealed or open, on its own channel. */
    fun showReview(context: Context) {
        val builder =
            baseBuilder(context, NotificationChannels.REVIEW)
                .setContentTitle(context.getString(R.string.notif_review_title))
                .setContentText(context.getString(R.string.notif_review_body))
        notify(context, REVIEW_ID, builder)
    }

    /** Clears a stale reminder notification (e.g. its habit got logged some other way). */
    fun cancelReminder(context: Context) = NotificationManagerCompat.from(context).cancel(REMINDER_ID)

    private fun baseBuilder(
        context: Context,
        channelId: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_habi)
            .setColor(Hoja.toArgb())
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun quickActionIntent(
        context: Context,
        habitId: String,
        amount: Int,
    ): PendingIntent {
        val intent =
            Intent(context, QuickActionReceiver::class.java)
                .putExtra("habitId", habitId)
                .putExtra("amount", amount)
        return PendingIntent.getBroadcast(
            context,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Posts [builder] under [id], skipping silently when notifications are disabled — including
     * on API 33+ where [NotificationManagerCompat.areNotificationsEnabled] already reflects a
     * missing POST_NOTIFICATIONS grant, so this never reaches the OS without it.
     */
    @SuppressLint("MissingPermission")
    private fun notify(
        context: Context,
        id: Int,
        builder: NotificationCompat.Builder,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(id, builder.build())
    }
}
