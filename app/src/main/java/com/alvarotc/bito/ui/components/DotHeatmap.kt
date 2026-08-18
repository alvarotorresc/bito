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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
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
private val OFF_DOT_SIZE = 10.dp
private val TODAY_RING_WIDTH = 2.5.dp
private val PENDING_RING_WIDTH = 2.dp
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
    // 6dp: only the row-to-row gap, no effect on any single cell's own measured width/height —
    // the ≥46dp touch floor is entirely a function of the Row's 3dp inter-cell gap below, which
    // stays untouched (see that Row's own comment).
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    Box(
        modifier
            .aspectRatio(1f)
            .heightIn(min = 44.dp)
            .testTag(if (day != null) "heatmap-day-${day.day}" else "heatmap-blank")
            .then(if (tappable) Modifier.clickable { onDayTap(day!!.day) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) DayDotGlyph(day)
    }
}

@Composable
private fun DayDotGlyph(day: HeatmapDay) {
    // OFF stays small and faint regardless of isToday (a day can't actually be both, but the
    // guide is explicit this is the one state allowed to look like a speck).
    if (day.dot == DayDot.OFF) {
        Box(Modifier.size(OFF_DOT_SIZE).clip(CircleShape).background(Borde.copy(alpha = 0.35f)))
        return
    }
    val base = Modifier.size(DOT_SIZE).clip(CircleShape)
    if (day.isToday) {
        val (fill, showSnowflake) = todayFillFor(day.dot)
        Box(base.background(fill).border(TODAY_RING_WIDTH, Brasa, CircleShape), contentAlignment = Alignment.Center) {
            if (showSnowflake) {
                Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(FROZEN_ICON_SIZE))
            }
        }
        return
    }
    when (day.dot) {
        DayDot.PENDING -> Box(base.border(PENDING_RING_WIDTH, TintaSuave, CircleShape))
        DayDot.FROZEN ->
            Box(base.background(HojaTinte), contentAlignment = Alignment.Center) {
                Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(FROZEN_ICON_SIZE))
            }
        DayDot.FULFILLED, DayDot.ACTIVITY -> Box(base.background(Hoja))
        DayDot.FAILED, DayDot.EMPTY -> Box(base.background(Borde))
        DayDot.PAUSED -> Box(base.background(TintaSuave))
        DayDot.OFF -> Unit // handled above
    }
}

/** Today's fill per its state; PENDING (nothing logged yet) reads as an empty card slot. */
private fun todayFillFor(dot: DayDot): Pair<Color, Boolean> =
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> Hoja to false
        DayDot.FAILED, DayDot.EMPTY -> Borde to false
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
