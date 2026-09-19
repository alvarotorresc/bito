package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.LogicalDay
import java.time.Instant
import java.time.ZoneId

/**
 * The logical day and the minute of day, both derived from the SAME instant — [TodayWidget] and
 * [SingleHabitWidget] used to read `System.currentTimeMillis()` twice (once for [today], once for
 * [minutesOfDay]), copy-pasted identically in both `provideGlance`s. Two clock reads a few
 * microseconds apart can straddle midnight: [today] rolls to the new day while [minutesOfDay]
 * still reads a few minutes before it, which is exactly the kind of drift `HabiDay.phaseOf` cannot
 * tell apart from a stale pose.
 */
internal data class WidgetClock(val today: LogicalDay, val minutesOfDay: Int)

internal fun widgetClock(
    nowMillis: Long,
    dayCutoffMinutes: Int,
    zone: ZoneId,
): WidgetClock {
    val today = LogicalDays.logicalDayOf(nowMillis, dayCutoffMinutes, zone)
    val local = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()
    return WidgetClock(today, local.hour * 60 + local.minute)
}
