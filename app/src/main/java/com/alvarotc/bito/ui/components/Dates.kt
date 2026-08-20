package com.alvarotc.bito.ui.components

import com.alvarotc.bito.domain.model.LogicalDay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Shared date-display formatting: every screen that renders a [LogicalDay] or a [YearMonth] as
 * text goes through here instead of building its own `DateTimeFormatter.ofPattern(...)`. A
 * [LogicalDay] is already a resolved calendar day (the zone was applied upstream turning a real
 * instant into it), so none of these need a [java.time.ZoneId] — only [LocalDate.format].
 */

/** [day] as "14 Aug 2026" (device locale) — Detail's paused-since caption, Archived's since-date row. */
fun formatDayMedium(day: LogicalDay): String =
    LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

/** [millis] (an unlock instant, device zone) as "14 Aug 2026" — the Badges list's unlock-date caption. */
fun formatMillisMedium(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay().toInt()
    return formatDayMedium(day)
}

/** [day] formatted with a caller-supplied [pattern] (device locale) — Today's header date, whose pattern is itself localized. */
fun formatDayWithPattern(
    day: LogicalDay,
    pattern: String,
): String = LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

/**
 * Lowercase month label for [month] ("agosto" / "agosto 2025") — Detail's heatmap header. The
 * year only joins in when [month] isn't in [currentRealMonth]'s year.
 */
fun formatMonthLabel(
    month: YearMonth,
    currentRealMonth: YearMonth,
): String {
    val pattern = if (month.year != currentRealMonth.year) "MMMM yyyy" else "MMMM"
    return month.atDay(1)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        .lowercase(Locale.getDefault())
}
