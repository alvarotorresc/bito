package com.alvarotc.bito.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.domain.ClockTimes
import java.time.ZoneId

/**
 * Schedules the alarm that rotates the widget's logical day at the settings
 * cutoff. Pure math lives in [nextRotationAt] (delegates to [ClockTimes],
 * same DST-correct wall-clock resolution the reminders use).
 */
object WidgetDayAlarm {
    fun nextRotationAt(
        cutoffMinutes: Int,
        nowMillis: Long,
        zone: ZoneId,
    ): Long = ClockTimes.nextOccurrence(cutoffMinutes % (24 * 60), nowMillis, zone)

    fun schedule(
        context: Context,
        cutoffMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val at = nextRotationAt(cutoffMinutes, nowMillis, zone)
        val pi =
            PendingIntent.getBroadcast(
                context,
                RC,
                Intent(context, WidgetMidnightReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        // Day rotation tolerates minutes of drift: an inexact alarm avoids the
        // exact-alarm permission pressure a precise one would add.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private const val RC = 41001
}
