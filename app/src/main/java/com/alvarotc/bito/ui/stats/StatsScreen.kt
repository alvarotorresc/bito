package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.ActiveStreak
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.PerfectDaysSummary
import com.alvarotc.bito.domain.WeekRow
import com.alvarotc.bito.domain.WeekSummary
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** The Stats tab: commentator, perfect days, the week strip, active streaks and the two teasers. */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenRecords: () -> Unit,
    onOpenNumbers: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.nav_stats), style = MaterialTheme.typography.headlineLarge, color = Tinta)
            CommentatorBubble(state.mood, state.personality)
            PerfectDaysCard(state.perfectDays)
            WeekCard(state.week)
            StreaksCard(state.activeStreaks)
            Teasers(onOpenRecords, onOpenNumbers)
        }
    }
}

@Composable
private fun CommentatorBubble(
    mood: Mood,
    personality: Personality,
) {
    SpeechBubble(
        speaker = stringResource(R.string.habi_speaker, stringResource(personalityLabelRes(personality))),
        text = stringResource(moodTextRes(mood)),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun personalityLabelRes(personality: Personality): Int =
    when (personality) {
        Personality.SARGENTO -> R.string.personality_sargento
        Personality.CHEERLEADER -> R.string.personality_cheerleader
        Personality.NEUTRA -> R.string.personality_neutra
    }

private fun moodTextRes(mood: Mood): Int =
    when (mood) {
        Mood.RADIANT -> R.string.stats_habi_radiant
        Mood.NORMAL -> R.string.stats_habi_normal
        Mood.WILTED -> R.string.stats_habi_wilted
        Mood.DRAMATIC -> R.string.stats_habi_dramatic
    }

/** THE single solid-accent card on the screen: total perfect days, "N this month". */
@Composable
private fun PerfectDaysCard(summary: PerfectDaysSummary) {
    BitoCard(
        container = Hoja,
        border = Hoja,
        modifier = Modifier.fillMaxWidth().testTag("accent"),
    ) {
        Text(
            stringResource(R.string.stats_perfect_days_label),
            style = MaterialTheme.typography.labelMedium,
            color = Tarjeta.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${summary.total}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
            color = Tarjeta,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.stats_perfect_days_this_month, summary.thisMonth),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = Tarjeta.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun WeekCard(week: WeekSummary) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stats_week_title),
                style = MaterialTheme.typography.titleMedium,
                color = Tinta,
                modifier = Modifier.weight(1f),
            )
            val delta = week.deltaVsLastWeek
            // The design names one chip — the positive-delta one ("+12% vs last week"); a slipping
            // week simply gets no chip, same as a week with no data yet (delta == null).
            if (delta != null && delta >= 0) {
                Row(
                    Modifier.clip(CircleShape).background(HojaTinte).padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.stats_week_delta, delta),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Hoja,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (week.rows.isEmpty()) {
            Text(stringResource(R.string.stats_week_empty), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                week.rows.forEach { row -> WeekRowLine(row) }
            }
        }
    }
}

@Composable
private fun WeekRowLine(row: WeekRow) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.dots.forEach { dot -> WeekDayDot(dot) }
        }
    }
}

/**
 * Small, tap-free twin of [com.alvarotc.bito.ui.components.DotHeatmap]'s per-day glyph mapping —
 * DotProgress-canon 16dp solid dots, same fill/ring per state as the fixed heatmap (FULFILLED/
 * ACTIVITY solid Hoja, FAILED/EMPTY solid Borde, PAUSED solid TintaSuave, PENDING a 2dp
 * TintaSuave ring). OFF stays its own small/faint speck at 35% (not the heatmap's 40% — this
 * strip is a smaller, denser component).
 */
@Composable
private fun WeekDayDot(dot: DayDot) {
    val size = 16.dp
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> Box(Modifier.size(size).clip(CircleShape).background(Hoja))
        DayDot.FAILED, DayDot.EMPTY -> Box(Modifier.size(size).clip(CircleShape).background(Borde))
        DayDot.FROZEN ->
            Box(Modifier.size(size).clip(CircleShape).background(HojaTinte), contentAlignment = Alignment.Center) {
                Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(10.dp))
            }
        DayDot.PAUSED -> Box(Modifier.size(size).clip(CircleShape).background(TintaSuave))
        DayDot.PENDING -> Box(Modifier.size(size).clip(CircleShape).border(2.dp, TintaSuave, CircleShape))
        DayDot.OFF -> Box(Modifier.size(6.dp).clip(CircleShape).background(Borde.copy(alpha = 0.35f)))
    }
}

@Composable
private fun StreaksCard(streaks: List<ActiveStreak>) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.stats_streaks_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(12.dp))
        if (streaks.isEmpty()) {
            Text(stringResource(R.string.stats_streaks_empty), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(streaks, key = { it.habitId }) { streak -> StreakWallChip(streak) }
            }
        }
    }
}

/**
 * Muro de llamas: one pill per streak, canon chip colors (BrasaTinte bg, Brasa flame) but with
 * the count as the dato grande and the habit name riding along small beside it. Display-only —
 * no clickable modifier, brasa here is emotional heat, never interaction (mirrors
 * [com.alvarotc.bito.ui.components.StreakChip]).
 */
@Composable
private fun StreakWallChip(streak: ActiveStreak) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(BrasaTinte)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("streak-${streak.habitId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "${streak.length}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp),
            color = Brasa,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            streak.name,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
            color = Tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    }
}

@Composable
private fun Teasers(
    onOpenRecords: () -> Unit,
    onOpenNumbers: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TeaserCard(
            label = stringResource(R.string.stats_teaser_records),
            onClick = onOpenRecords,
            modifier = Modifier.weight(1f).testTag("teaser-records"),
        )
        TeaserCard(
            label = stringResource(R.string.stats_teaser_numbers),
            onClick = onOpenNumbers,
            modifier = Modifier.weight(1f).testTag("teaser-numbers"),
        )
    }
}

@Composable
private fun TeaserCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BitoCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // No maxLines/ellipsis here: unlike the week rows (a fixed habit name against a strict
            // dot budget), this label is one of two known app strings — heightIn(min = 72.dp) above
            // already absorbs a two-line wrap at this width, so both teasers land at the same 72dp
            // whether their label takes one line or two, with nothing truncated.
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = Tinta,
                modifier = Modifier.weight(1f),
            )
            Icon(BitoIcons.ChevronRight, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(20.dp))
        }
    }
}
