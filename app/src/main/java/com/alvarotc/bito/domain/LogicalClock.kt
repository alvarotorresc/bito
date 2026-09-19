package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.LogicalDay
import java.time.Instant
import java.time.ZoneId

/**
 * The logical day and the minute of day, both derived from the SAME instant. The widgets
 * ([com.alvarotc.bito.ui.widget.TodayWidget], [com.alvarotc.bito.ui.widget.SingleHabitWidget], T16)
 * and the notification specs ([com.alvarotc.bito.ui.notifications.ReminderUseCase],
 * [com.alvarotc.bito.ui.notifications.PerfectDayNotifier], T18) all used to read
 * `System.currentTimeMillis()` twice (once for [today], once for [minutesOfDay]), copy-pasted
 * across call sites. Two clock reads a few microseconds apart can straddle midnight: [today]
 * rolls to the new day while [minutesOfDay] still reads a few minutes before it, which is
 * exactly the kind of drift `HabiDay.phaseOf` cannot tell apart from a stale pose. Pure JVM — no
 * Android imports — so both `ui/widget` and `ui/notifications` can depend on it without either
 * depending on the other.
 */
data class LogicalClock(val today: LogicalDay, val minutesOfDay: Int)

fun logicalClockAt(
    nowMillis: Long,
    dayCutoffMinutes: Int,
    zone: ZoneId,
): LogicalClock {
    val today = LogicalDays.logicalDayOf(nowMillis, dayCutoffMinutes, zone)
    val local = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()
    return LogicalClock(today, local.hour * 60 + local.minute)
}
