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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.TintaSuave
import java.time.LocalDate

private const val COLUMNS = 7

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    val ringModifier = Modifier.size(18.dp).clip(CircleShape)
    val core: @Composable () -> Unit = {
        when (day.dot) {
            DayDot.FULFILLED, DayDot.ACTIVITY -> Box(ringModifier.background(Hoja))
            DayDot.FAILED -> Box(ringModifier.border(1.5.dp, TintaSuave, CircleShape))
            DayDot.FROZEN ->
                Box(ringModifier.background(HojaTinte), contentAlignment = Alignment.Center) {
                    Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(10.dp))
                }
            DayDot.PAUSED -> Box(Modifier.size(10.dp).clip(CircleShape).background(Borde))
            DayDot.PENDING -> Box(ringModifier.border(1.5.dp, TintaSuave.copy(alpha = 0.4f), CircleShape))
            DayDot.EMPTY -> Box(ringModifier.border(1.5.dp, Borde, CircleShape))
            DayDot.OFF -> Box(Modifier.size(4.dp).clip(CircleShape).background(Borde.copy(alpha = 0.3f)))
        }
    }
    if (day.isToday) {
        Box(Modifier.size(22.dp).border(1.dp, Brasa, CircleShape), contentAlignment = Alignment.Center) { core() }
    } else {
        core()
    }
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
