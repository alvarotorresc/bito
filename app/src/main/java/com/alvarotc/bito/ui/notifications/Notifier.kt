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
 * Posts or cancels Bito's local notifications from already-decided content — [ReminderUseCase],
 * [TrayRefresher], and [PerfectDayNotifier] are the callers that decide *whether* to notify;
 * this object renders what it's handed and manages stale notifications. Silently skips posting
 * when the user has disabled notifications (API 33+ reality): scheduling the next alarm never
 * depends on whether this actually reached the tray.
 */
object Notifier {
    const val REMINDER_ID = 1
    const val REVIEW_ID = 2
    const val CELEBRATION_ID = 3

    /** Extra keys carried by [quickActionIntent] and read back in [QuickActionReceiver]. */
    const val EXTRA_HABIT_ID = "habitId"
    const val EXTRA_AMOUNT = "amount"
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    /** Extra key carried by [contentIntent] and read back in `MainActivity.handleIntent` (T11). */
    const val EXTRA_OPEN_ROUTE = "openRoute"

    /** The GLOBAL reminder: one or more habits still open, up to three quick-log actions. */
    fun showReminder(
        context: Context,
        payload: ReminderPayload,
    ) {
        val count = payload.pendingNames.size
        val title = context.resources.getQuantityString(R.plurals.notif_reminder_title, count, count)
        val style = NotificationCompat.InboxStyle()
        payload.pendingNames.forEach(style::addLine)
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(title)
                .setStyle(style)
        payload.targets.forEach { target ->
            val label =
                if (target.isCheck) {
                    context.getString(R.string.notif_target_done, target.name)
                } else {
                    context.getString(R.string.notif_target_add, target.amount, target.name)
                }
            builder.addAction(0, label, quickActionIntent(context, target.habitId, target.amount, REMINDER_ID))
        }
        notify(context, REMINDER_ID, builder)
    }

    /**
     * The HABIT reminder: a single habit's own slot, still open and unfailed. Posted under its
     * own [habitId]-derived id so it can stack alongside other per-habit reminders and the
     * GLOBAL one instead of clobbering them. [target] is `null` for habit kinds with no honest
     * quick action (DURATION, ABSTINENCE) — the notification still fires, just without a
     * button; tapping it only opens the app.
     */
    fun showSingleHabit(
        context: Context,
        habitId: String,
        name: String,
        target: QuickTarget?,
    ) {
        val id = habitId.hashCode()
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(context.getString(R.string.notif_reminder_single_title, name))
                .setContentText(context.getString(R.string.notif_reminder_single_body))
        if (target != null) {
            val label =
                if (target.isCheck) {
                    context.getString(R.string.notif_action_done)
                } else {
                    context.getString(R.string.notif_action_add, target.amount)
                }
            builder.addAction(0, label, quickActionIntent(context, target.habitId, target.amount, id))
        }
        notify(context, id, builder)
    }

    /** The REVIEW nudge: something is still unsealed or open, on its own channel. Tapping it
     * deep-links straight into the review flow rather than opening Today bare (T11). */
    fun showReview(context: Context) {
        val builder =
            baseBuilder(context, NotificationChannels.REVIEW)
                .setContentTitle(context.getString(R.string.notif_review_title))
                .setContentText(context.getString(R.string.notif_review_body))
                .setContentIntent(contentIntent(context, "review", 2))
        notify(context, REVIEW_ID, builder)
    }

    /**
     * The CELEBRATIONS nudge: [PerfectDayNotifier] already decided this write is worth
     * announcing outside the app — this just renders [body] under its own channel and id.
     * Tapping it opens Today bare, the same default [contentIntent] every other route falls
     * back to, where the in-app perfect-day sheet takes over from there.
     */
    fun showPerfectDay(
        context: Context,
        body: String,
    ) {
        val builder =
            baseBuilder(context, NotificationChannels.CELEBRATIONS)
                .setContentTitle(context.getString(R.string.notif_perfect_day_title))
                .setContentText(body)
        notify(context, CELEBRATION_ID, builder)
    }

    /** Clears a stale reminder notification (e.g. its habit got logged some other way). */
    fun cancelReminder(context: Context) = NotificationManagerCompat.from(context).cancel(REMINDER_ID)

    /** Clears a stale review nudge (e.g. the day just got sealed in-app) — mejoras-qa M7 #3. */
    fun cancelReview(context: Context) = NotificationManagerCompat.from(context).cancel(REVIEW_ID)

    private fun baseBuilder(
        context: Context,
        channelId: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_habi)
            .setColor(Hoja.toArgb())
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            // TrayRefresher re-posts the GLOBAL reminder on every in-app write while it's showing
            // (recomputed content, same id) — without this, each of those updates would re-alert
            // (sound/vibration) exactly like a brand-new notification. Only the very first post
            // should alert; every refresh after that is silent.
            .setOnlyAlertOnce(true)

    /**
     * [route], when given, is carried as [EXTRA_OPEN_ROUTE] for `MainActivity.handleIntent` to
     * hand to [com.alvarotc.bito.ui.NavRequests]. [requestCode] must differ per route — not just
     * be a constant — because [Intent.filterEquals] ignores extras: every route would otherwise
     * collide on the same [PendingIntent] and `FLAG_UPDATE_CURRENT` would silently overwrite the
     * other notification's extra with whichever posted last.
     */
    private fun contentIntent(
        context: Context,
        route: String? = null,
        requestCode: Int = 0,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                route?.let { putExtra(EXTRA_OPEN_ROUTE, it) }
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * [notificationId] is the tray id this action's notification was posted under, so the
     * receiver can clear it. The request code must be unique per (habit, notification) — not
     * just per habit — because [Intent.filterEquals] ignores extras: the same habit's GLOBAL
     * and HABIT actions would otherwise collide on the same [PendingIntent] and
     * `FLAG_UPDATE_CURRENT` would silently rewrite [notificationId] to whichever posted last.
     */
    private fun quickActionIntent(
        context: Context,
        habitId: String,
        amount: Int,
        notificationId: Int,
    ): PendingIntent {
        val intent =
            Intent(context, QuickActionReceiver::class.java)
                .putExtra(EXTRA_HABIT_ID, habitId)
                .putExtra(EXTRA_AMOUNT, amount)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        val requestCode = 31 * habitId.hashCode() + notificationId
        return PendingIntent.getBroadcast(
            context,
            requestCode,
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
