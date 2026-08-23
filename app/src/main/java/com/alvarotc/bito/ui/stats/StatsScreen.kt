package com.alvarotc.bito.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.ActiveStreak
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HabitRecord
import com.alvarotc.bito.domain.PerfectDaysSummary
import com.alvarotc.bito.domain.WeekRow
import com.alvarotc.bito.domain.WeekSummary
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** The dot cell size and gap the week strip's rows use — the header row mirrors both exactly so its 7 labels land above the right columns. */
private val WeekDotSize = 16.dp
private val WeekDotGap = 6.dp
private val WeekDotsWidth = WeekDotSize * 7 + WeekDotGap * 6
private val WeekTallyGap = 12.dp

// Fixed (not a min) so the header's trailing reserve always matches a row's tally column exactly —
// a widthIn(min=) only reserves slack, so it stops right-aligning consistently once a tally's
// digits (e.g. "12/24") exceed the reserved width. Long values overflow past this rather than
// shifting every other row's dot cluster out of alignment with the header.
private val WeekTallyWidth = 40.dp

/** The Stats tab: commentator, perfect days, the week strip, active streaks and the two teasers. */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenRecords: () -> Unit,
    onOpenNumbers: () -> Unit,
    onOpenBadges: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = Papel) { padding ->
        if (state.loading) {
            // First frame after the nav-scoped VM is recreated: calm paper, never zeroed heroes
            // and empty streak walls (QA 2026-08-23).
            Box(Modifier.padding(padding).fillMaxSize().testTag("stats-loading"))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.nav_stats), style = MaterialTheme.typography.headlineLarge, color = Tinta)
            CommentatorBubble(state.mood, state.personality, state.equipped)
            PerfectDaysCard(state.perfectDays)
            WeekCard(state.week)
            StreaksSection(state.activeStreaks)
            Teasers(state.bestRecord, state.totalEntries, onOpenRecords, onOpenNumbers)
            AchievementsSection(state, onOpenBadges)
        }
    }
}

/** The commentator's mini-avatar wears whatever is really equipped — never a store preview (Stats has none). */
@Composable
private fun CommentatorBubble(
    mood: Mood,
    personality: Personality,
    equipped: EquippedSet,
) {
    SpeechBubble(
        speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(personality))),
        text = stringResource(HabiVoice.bubbleRes(mood, personality)),
        modifier = Modifier.fillMaxWidth(),
        avatar = { HabiAvatar(HabiSpec(mood, personality, equipped), Modifier.size(40.dp), animated = false) },
    )
}

/**
 * THE single solid-accent card on the screen: perfect days THIS YEAR (rule 1 of the 3a mockup) —
 * an outlined check-circle at the left, the hero number + "this year" inline to its right, the
 * "Perfect days" label below both.
 */
@Composable
private fun PerfectDaysCard(summary: PerfectDaysSummary) {
    BitoCard(
        container = Hoja,
        border = Hoja,
        modifier = Modifier.fillMaxWidth().testTag("accent"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).border(2.dp, Tarjeta, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BitoIcons.Check, contentDescription = null, tint = Tarjeta, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${summary.thisYear}",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                        color = Tarjeta,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.stats_perfect_days_year_label),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                        color = Tarjeta.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    stringResource(R.string.stats_perfect_days_label),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = Tarjeta,
                )
            }
        }
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BitoIcons.ArrowUp, contentDescription = null, tint = Hoja, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
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
                WeekColumnHeader()
                week.rows.forEach { row -> WeekRowLine(row) }
            }
        }
    }
}

/** L M X J V S D (or locale-equivalent), aligned above [WeekRowLine]'s dot cluster only — not the habit-name or tally columns. */
@Composable
private fun WeekColumnHeader() {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(WeekDotsWidth), horizontalArrangement = Arrangement.spacedBy(WeekDotGap)) {
            for (ordinal in 1..7) {
                Box(Modifier.size(WeekDotSize), contentAlignment = Alignment.Center) {
                    Text(
                        DayOfWeek.of(ordinal).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = TintaSuave,
                    )
                }
            }
        }
        Spacer(Modifier.width(WeekTallyGap))
        Spacer(Modifier.width(WeekTallyWidth))
    }
}

@Composable
private fun WeekRowLine(row: WeekRow) {
    // [D]: the done/target tally (below) already speaks the raw total, but not WHICH weekdays
    // were done — one aggregated description on the dot cluster, never per dot, same [D]-rule
    // DotHeatmap follows for its own per-cell glyphs.
    val daysDescription = weekRowDaysDescription(row.dots)
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .width(WeekDotsWidth)
                .semantics { contentDescription = daysDescription }
                .testTag("week-dots-${row.habitId}"),
            horizontalArrangement = Arrangement.spacedBy(WeekDotGap),
        ) {
            row.dots.forEach { dot -> WeekDayDot(dot) }
        }
        Spacer(Modifier.width(WeekTallyGap))
        Row(Modifier.width(WeekTallyWidth), horizontalArrangement = Arrangement.End) {
            Text(
                "${row.done}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = Tinta,
            )
            Text(
                "/${row.target}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = TintaSuave,
            )
        }
    }
}

/**
 * Small, tap-free twin of [com.alvarotc.bito.ui.components.DotHeatmap]'s per-day glyph mapping —
 * DotProgress-canon 16dp solid dots, same fill/ring per state as the fixed heatmap (FULFILLED/
 * ACTIVITY solid Hoja, FAILED/EMPTY a PeligroTinte fill with a Peligro X — QA 2026-08-23, PAUSED solid TintaSuave, PENDING a 2dp
 * TintaSuave ring, OFF a 2dp Borde ring for future/unreached days). Grid always reads complete
 * and aligned with header.
 */
@Composable
private fun WeekDayDot(dot: DayDot) {
    val size = WeekDotSize
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> Box(Modifier.size(size).clip(CircleShape).background(Hoja))
        DayDot.FAILED, DayDot.EMPTY ->
            Box(Modifier.size(size).clip(CircleShape).background(PeligroTinte), contentAlignment = Alignment.Center) {
                Icon(BitoIcons.X, contentDescription = null, tint = Peligro, modifier = Modifier.size(10.dp))
            }
        DayDot.FROZEN ->
            Box(Modifier.size(size).clip(CircleShape).background(HojaTinte), contentAlignment = Alignment.Center) {
                Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(10.dp))
            }
        DayDot.PAUSED -> Box(Modifier.size(size).clip(CircleShape).background(TintaSuave))
        DayDot.PENDING -> Box(Modifier.size(size).clip(CircleShape).border(2.dp, TintaSuave, CircleShape))
        DayDot.OFF -> Box(Modifier.size(size).clip(CircleShape).border(2.dp, Borde, CircleShape))
    }
}

/**
 * "Mon done, Tue done, Wed missed, …" for one [WeekRowLine]'s dot cluster — the [D]-rule
 * aggregated description, built from the same day-state words [com.alvarotc.bito.ui.components.DotHeatmap]
 * reuses (`day_done`/`day_not_done`/`heatmap_day_frozen`/`paused_section_title`/`heatmap_day_pending`)
 * so TalkBack says the same word for the same state everywhere in the app. `row.dots` is Monday-
 * first (matches [WeekColumnHeader]'s `DayOfWeek.of(1..7)`); OFF carries no judgeable state and is
 * skipped, same as the heatmap's own per-cell convention.
 *
 * `internal` (not `private`): lets a test build this directly off a hand-picked [DayDot] list —
 * same reasoning as [com.alvarotc.bito.ui.habi.PersonalityPills] — instead of reconstructing one
 * through the full [StatsViewModel]/Room pipeline just to pin down 7 specific day states.
 */
@Composable
internal fun weekRowDaysDescription(dots: List<DayDot>): String {
    val locale = Locale.getDefault()
    return dots.mapIndexedNotNull { index, dot ->
        val stateLabel = weekDayStateLabel(dot) ?: return@mapIndexedNotNull null
        val dayName = DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, locale)
        stringResource(R.string.stats_week_day_state, dayName, stateLabel)
    }.joinToString(", ")
}

/** The spoken state word for one [DayDot] in the week strip — null for OFF (no judgeable state). */
@Composable
private fun weekDayStateLabel(dot: DayDot): String? =
    when (dot) {
        DayDot.FULFILLED, DayDot.ACTIVITY -> stringResource(R.string.day_done)
        DayDot.FAILED, DayDot.EMPTY -> stringResource(R.string.day_not_done)
        DayDot.FROZEN -> stringResource(R.string.heatmap_day_frozen)
        // Reuses TodayScreen's "Paused" section-header string, same as DotHeatmap's own per-cell
        // mapping — one word, one meaning, no near-duplicate string to keep in sync.
        DayDot.PAUSED -> stringResource(R.string.paused_section_title)
        DayDot.PENDING -> stringResource(R.string.heatmap_day_pending)
        DayDot.OFF -> null
    }

/** Bare section title (no card) above a [LazyRow] wall of mini streak cards — rule 3 of the 3a mockup. */
@Composable
private fun StreaksSection(streaks: List<ActiveStreak>) {
    Column {
        Text(
            stringResource(R.string.stats_streaks_title),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
            color = TintaSuave,
        )
        Spacer(Modifier.height(12.dp))
        if (streaks.isEmpty()) {
            Text(stringResource(R.string.stats_streaks_empty), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(streaks, key = { it.habitId }) { streak -> StreakWallCard(streak) }
            }
        }
    }
}

/**
 * One mini card of the streak wall: flame + count is the dato grande, the habit name rides
 * centered below it. Display-only — no clickable modifier, brasa here is emotional heat, never
 * interaction (mirrors [com.alvarotc.bito.ui.components.StreakChip]).
 */
@Composable
private fun StreakWallCard(streak: ActiveStreak) {
    // [E]: Icon(Flame, null) + Text(length) + Text(name) — the flame glyph is where the word
    // "streak" actually lives; a plain `mergeDescendants = true` would only concatenate "3" and
    // the habit name (e.g. "3 Meditar"), silently dropping the one word a sighted reader gets for
    // free from the icon. `mergeDescendants = true` (still needed — the Surface has no `onClick`
    // to trigger it for free, unlike `AchievementsSection`'s `BitoCard`) collapses the 2 children
    // into ONE spoken stop, and the explicit `contentDescription` on that same node overrides what
    // the collapse alone would say — same shape as `DayRing` in Components.kt.
    val streakDescription =
        stringResource(R.string.stats_streak_card_cd, streak.name, streak.length, stringResource(periodUnitRes(streak.period)))
    Surface(
        modifier =
            Modifier
                .width(140.dp)
                .testTag("streak-${streak.habitId}")
                .semantics(mergeDescendants = true) { contentDescription = streakDescription },
        shape = RoundedCornerShape(20.dp),
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${streak.length}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                    color = Tinta,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                streak.name,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = TintaSuave,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Teasers(
    bestRecord: HabitRecord?,
    totalEntries: Int,
    onOpenRecords: () -> Unit,
    onOpenNumbers: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RecordsTeaserCard(
            record = bestRecord,
            onClick = onOpenRecords,
            modifier = Modifier.weight(1f).testTag("teaser-records"),
        )
        NumbersTeaserCard(
            totalEntries = totalEntries,
            onClick = onOpenNumbers,
            modifier = Modifier.weight(1f).testTag("teaser-numbers"),
        )
    }
}

/** Trophy top-left, chevron top-right, then the best-streak hero — empty state keeps the hero slot with "—". */
@Composable
private fun RecordsTeaserCard(
    record: HabitRecord?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BitoCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth()) {
            Icon(BitoIcons.Trophy, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            Icon(BitoIcons.ChevronRight, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                record?.best?.toString() ?: stringResource(R.string.no_data_placeholder),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
                color = Tinta,
            )
            if (record != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(periodUnitRes(record.period)),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.stats_teaser_records_caption),
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
        )
    }
}

/** Chevron top-right, then the total-entries hero formatted with the locale thousands separator. */
@Composable
private fun NumbersTeaserCard(
    totalEntries: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BitoCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Icon(BitoIcons.ChevronRight, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        val locale = LocalConfiguration.current.locales[0]
        Text(
            NumberFormat.getIntegerInstance(locale).format(totalEntries),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
            color = Tinta,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.stats_teaser_numbers_caption),
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
        )
    }
}

/**
 * The Logros section (GUIA 3a v2): title + "N of M" count + chevron, a [FlowRow] wall of every
 * catalog badge — unlocked ones first (catalog order), then locked — and a "see all" footer. A
 * normal [BitoCard], never the screen's solid accent: [PerfectDaysCard] alone keeps that role.
 * Renders the full grid even at zero unlocked — in a data screen empty is poverty, not reward,
 * the same rule [StreaksSection] and [RecordsScreen]'s empty state follow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsSection(
    state: StatsUiState,
    onOpenBadges: () -> Unit,
) {
    // [F]: BitoCard(onClick) auto-merges its whole subtree, so without this override tapping into
    // the card would concatenate the header text with EVERY BadgeChip's name in the FlowRow below
    // into one very long spoken string. An explicit contentDescription here takes over what
    // TalkBack announces (same data as the header Text just below), same pattern HabitCard uses
    // to keep "whole card opens X" rows short.
    val achievementsDescription = stringResource(R.string.achievements_card_cd, state.badgesUnlocked, state.badgesTotal)
    BitoCard(
        onClick = onOpenBadges,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("achievements")
                .semantics { contentDescription = achievementsDescription },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stats_badges_title),
                style = MaterialTheme.typography.titleMedium,
                color = Tinta,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.stats_badges_count, state.badgesUnlocked, state.badgesTotal),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
            Spacer(Modifier.width(8.dp))
            Icon(BitoIcons.ChevronRight, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        val (unlocked, locked) = state.badges.partition { it.unlockedAtMillis != null }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (unlocked + locked).forEach { badge -> BadgeChip(badge) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.stats_badges_all),
            style = MaterialTheme.typography.labelMedium,
            color = Hoja,
        )
    }
}

private fun periodUnitRes(period: Period): Int =
    when (period) {
        Period.DAY -> R.string.unit_days
        Period.WEEK -> R.string.unit_weeks
        Period.MONTH -> R.string.unit_months
    }
