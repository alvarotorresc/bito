package com.alvarotc.bito.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.TintaSuave
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val COLUMNS = 7

// Visual dot diameter — the touch cell around it stays >=46dp (see HeatmapCell); at the 48dp
// cells this grid actually renders (w411dp), a 26dp dot keeps the same ~54% dot-to-cell ratio as
// the canon mock (22px dot in a ~40.6px column), so the grid reads as dense as the mock without
// touching the cell's own tap-target math.
private val DOT_SIZE = 26.dp

private val TODAY_RING_WIDTH = 2.5.dp
private val PENDING_RING_WIDTH = 2.dp

// Fidelidad ajuste 2 (2a mockup): future/out-of-range days now read as a hollow ring, same DOT_SIZE
// as every other dot — the grid always looks complete, replacing the old small/faint OFF dot.
private val OFF_RING_WIDTH = 2.dp
private val FROZEN_ICON_SIZE = 14.dp

/**
 * A calendar-month grid of [days], 7 columns Monday-first, one dot per day. Leading blanks pad
 * the first row so day 1 lands under its real weekday; the grid does not pad a trailing row.
 * Each cell is a ≥44dp tap target ([days] are navigation, not a logging action); tapping an
 * [DayDot.OFF] day does nothing.
 */
@Composable
fun DotHeatmap(
    days: List<HeatmapDay>,
    onDayTap: (LogicalDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val leadingBlanks = (LocalDate.ofEpochDay(days.first().day.toLong()).dayOfWeek.value - 1).coerceIn(0, COLUMNS - 1)
    val cells: List<HeatmapDay?> = List(leadingBlanks) { null } + days
    // [D]-rule: ONE aggregated description on the grid container, never per cell — this is what a
    // screen-reader user hears landing on the whole grid ("how did this month go"), separate from
    // each cell's own per-day description below (needed there for the tap target itself, not as a
    // second data-viz summary).
    val monthDescription = monthSummaryDescription(days)
    // 4dp: only the row-to-row gap, no effect on any single cell's own measured width/height —
    // the ≥46dp touch floor is entirely a function of the Row's 3dp inter-cell gap below, which
    // stays untouched (see that Row's own comment). Tightened from 6dp for the 2a mockup's denser
    // grid — cells still can't shrink below the touch floor, so ~22dp (dot-to-cell padding) is the
    // real floor on the visual gap regardless of this value; this is just the closest the two rows
    // of dots can be pulled without touching the cells themselves.
    Column(
        // mergeDescendants = true: this Column has children (WeekdayHeaderRow's Text nodes, the
        // day cells below) and is neither a leaf nor otherwise a merge boundary, so a plain
        // `.semantics { contentDescription = ... }` here would not make it a real, separately
        // focusable stop for a screen reader — its own contentDescription would just be at the
        // mercy of whatever ancestor merge boundary happens to sit above it. mergeDescendants =
        // true makes this Column its own boundary: the explicit contentDescription overrides the
        // merged header text (Compose prefers an explicit contentDescription over merged Text),
        // while each HeatmapCell below stays its own separate stop (clickable cells are already
        // their own merge boundaries, so this outer merge does not reach past them).
        modifier.semantics(mergeDescendants = true) { contentDescription = monthDescription },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WeekdayHeaderRow()
        cells.chunked(COLUMNS).forEach { week ->
            // 3dp (not 4dp): part of the touch-floor fix — see HeatmapSection's comment in
            // DetailScreen.kt for the full horizontal-budget accounting.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day -> HeatmapCell(day, onDayTap, Modifier.weight(1f)) }
                // Pad a short last row so every column keeps its width, matching the full rows.
                repeat(COLUMNS - week.size) { Box(Modifier.weight(1f).heightIn(min = 44.dp)) }
            }
        }
    }
}

/** L M X J V S D (or locale-equivalent), one per column, aligned with the day grid below it. */
@Composable
private fun WeekdayHeaderRow() {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (ordinal in 1..COLUMNS) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    DayOfWeek.of(ordinal).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = TintaSuave,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HeatmapDay?,
    onDayTap: (LogicalDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    // OFF marks a day outside the habit's life or in the future — never a valid tap target.
    val tappable = day != null && day.dot != DayDot.OFF
    // Goes beyond the letter of the [D] aggregate rule above: each cell is also the only way to
    // open the retroactive day-edit sheet, so — same as any other unlabeled clickable — it needs
    // its own identity, not just a color a screen-reader user can't see. OFF cells (day != null
    // but dot == OFF) stay undescribed, matching their non-tappable, decorative treatment.
    val cellDescription = day?.let { dayCellDescription(it) }
    Box(
        modifier
            .aspectRatio(1f)
            .heightIn(min = 44.dp)
            .testTag(if (day != null) "heatmap-day-${day.day}" else "heatmap-blank")
            .then(if (tappable) Modifier.clickable { onDayTap(day!!.day) } else Modifier)
            .then(if (cellDescription != null) Modifier.semantics { contentDescription = cellDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) DayDotGlyph(day)
    }
}

/** "<month> <day>, <state>" for one cell — null for OFF (outside the habit's life, or future). */
@Composable
private fun dayCellDescription(day: HeatmapDay): String? {
    val stateLabel = dayStateLabel(day.dot) ?: return null
    val dateLabel = formatDayWithPattern(day.day, stringResource(R.string.heatmap_day_pattern))
    return stringResource(R.string.heatmap_day_cd, dateLabel, stateLabel)
}

/** The spoken state word for one [DayDot] — null for OFF, which carries no judgeable state. */
@Composable
private fun dayStateLabel(dot: DayDot): String? =
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> stringResource(R.string.day_done)
        DayDot.FAILED, DayDot.EMPTY -> stringResource(R.string.day_not_done)
        DayDot.FROZEN -> stringResource(R.string.heatmap_day_frozen)
        // Reuses the section-header string (TodayScreen's "Paused" list title) rather than
        // minting a near-duplicate — same word, same meaning, one fewer string to keep in sync.
        DayDot.PAUSED -> stringResource(R.string.paused_section_title)
        DayDot.PENDING -> stringResource(R.string.heatmap_day_pending)
        DayDot.OFF -> null
    }

/**
 * One aggregated description for the whole visible month (the [D]-rule container summary, never
 * per cell): "<month>: N done, N failed, N frozen, N paused of N days". OFF (outside the habit's
 * life / future) and PENDING (today, not yet judged) days are excluded from every count and from
 * the total — a screen-reader user wants "how did this month go so far", not the raw calendar
 * cell count.
 */
@Composable
private fun monthSummaryDescription(days: List<HeatmapDay>): String {
    val monthLabel = formatDayWithPattern(days.first().day, stringResource(R.string.heatmap_month_pattern))
    val counts = days.groupingBy { it.dot }.eachCount()
    val done = (counts[DayDot.FULFILLED] ?: 0) + (counts[DayDot.ACTIVITY] ?: 0)
    val failed = (counts[DayDot.FAILED] ?: 0) + (counts[DayDot.EMPTY] ?: 0)
    val frozen = counts[DayDot.FROZEN] ?: 0
    val paused = counts[DayDot.PAUSED] ?: 0
    val total = done + failed + frozen + paused
    return stringResource(R.string.heatmap_month_summary, monthLabel, done, failed, frozen, paused, total)
}

@Composable
private fun DayDotGlyph(day: HeatmapDay) {
    val dayNumber = LocalDate.ofEpochDay(day.day.toLong()).dayOfMonth.toString()
    // OFF renders as a hollow ring regardless of isToday (a day can't actually be both) — same
    // DOT_SIZE as every judged day, so the grid always reads as a complete calendar. Every cell
    // except FROZEN carries its day-of-month number (QA 2026-08-23); FROZEN keeps the snowflake —
    // in a 26dp dot the state mark wins over the digits.
    if (day.dot == DayDot.OFF) {
        Box(
            Modifier.size(DOT_SIZE).clip(CircleShape).border(OFF_RING_WIDTH, Borde, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DayNumber(dayNumber, TintaSuave)
        }
        return
    }
    val base = Modifier.size(DOT_SIZE).clip(CircleShape)
    if (day.isToday) {
        val (fill, showSnowflake) = todayFillFor(day.dot)
        Box(base.background(fill).border(TODAY_RING_WIDTH, Brasa, CircleShape), contentAlignment = Alignment.Center) {
            if (showSnowflake) {
                Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(FROZEN_ICON_SIZE))
            } else {
                DayNumber(dayNumber, numberColorFor(day.dot))
            }
        }
        return
    }
    when (day.dot) {
        DayDot.PENDING ->
            Box(base.border(PENDING_RING_WIDTH, TintaSuave, CircleShape), contentAlignment = Alignment.Center) {
                DayNumber(dayNumber, TintaSuave)
            }
        DayDot.FROZEN ->
            Box(base.background(HojaTinte), contentAlignment = Alignment.Center) {
                Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(FROZEN_ICON_SIZE))
            }
        DayDot.FULFILLED, DayDot.ACTIVITY ->
            Box(base.background(Hoja), contentAlignment = Alignment.Center) { DayNumber(dayNumber, Tarjeta) }
        DayDot.FAILED, DayDot.EMPTY ->
            Box(base.background(PeligroTinte), contentAlignment = Alignment.Center) { DayNumber(dayNumber, Peligro) }
        DayDot.PAUSED ->
            Box(base.background(TintaSuave), contentAlignment = Alignment.Center) { DayNumber(dayNumber, Tarjeta) }
        DayDot.OFF -> Unit // handled above
    }
}

/** The day-of-month digits inside one heatmap dot — color picked per state by the caller. */
@Composable
private fun DayNumber(
    text: String,
    color: Color,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
    )
}

/** Number color over each judged fill — mirrors [DayDotGlyph]'s branches for today's ringed dot. */
private fun numberColorFor(dot: DayDot): Color =
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY, DayDot.PAUSED -> Tarjeta
        DayDot.FAILED, DayDot.EMPTY -> Peligro
        DayDot.PENDING -> TintaSuave
        DayDot.FROZEN, DayDot.OFF -> TintaSuave // unreachable: FROZEN shows the snowflake, OFF returned earlier
    }

/** Today's fill per its state; PENDING (nothing logged yet) reads as an empty card slot. */
private fun todayFillFor(dot: DayDot): Pair<Color, Boolean> =
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> Hoja to false
        DayDot.FAILED, DayDot.EMPTY -> PeligroTinte to false
        DayDot.FROZEN -> HojaTinte to true
        DayDot.PAUSED -> TintaSuave to false
        DayDot.PENDING -> Tarjeta to false
        DayDot.OFF -> Borde to false // unreachable: OFF returns before this branch
    }

@Preview(showBackground = true)
@Composable
private fun DotHeatmapPreview() {
    BitoTheme {
        val sample =
            listOf(
                HeatmapDay(1, DayDot.FULFILLED, false),
                HeatmapDay(2, DayDot.FAILED, false),
                HeatmapDay(3, DayDot.FROZEN, false),
                HeatmapDay(4, DayDot.PAUSED, false),
                HeatmapDay(5, DayDot.PENDING, false),
                HeatmapDay(6, DayDot.EMPTY, true),
                HeatmapDay(7, DayDot.OFF, false),
            )
        DotHeatmap(days = sample, onDayTap = {}, modifier = Modifier.fillMaxWidth())
    }
}
