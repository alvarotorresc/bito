package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.DotHeatmap
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.RelapseSheet
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [SegmentedPills] order for the compliance windows, matching [DetailUiState.windows]' [7, 30, 365] order. */
private val WINDOW_LABEL_RES = listOf(R.string.window_7, R.string.window_30, R.string.window_year)

/** The M5 habit detail screen: monument, heatmap, retroactive editing, freezers, pause/archive. */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // `uiState` seeds `null` before its first real emission AND when the habit genuinely does not
    // exist — the two are indistinguishable by type. `hasLoaded` tells them apart for the only
    // case that matters in practice: a habit deleted (from Settings/import) while its detail is
    // open bounces back once it was seen to exist; a cold-start null never fires onBack. Plain
    // `remember`, not `rememberSaveable`: a recreated VM after process death re-seeds `uiState`
    // at `null` too, so a saveable `true` here would false-positive onBack on the very first
    // frame. Config changes (the only other case `rememberSaveable` would help) are already
    // covered by the VM's cached StateFlow re-emitting its last value.
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onBack()
        }
    }
    val current = state ?: return

    var daySheetFor by remember { mutableStateOf<LogicalDay?>(null) }
    var showFreezerSheet by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var showArchiveSheet by remember { mutableStateOf(false) }
    var relapseSheetOpen by remember { mutableStateOf(false) }
    var windowIndex by rememberSaveable { mutableStateOf(0) }

    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DetailHeader(current.name, onBack, { onEdit(current.habitId) })
            // Archived: data stays visible (monument, heatmap, % pills) but every write surface
            // closes — no freezer purchase, no day-tap sheet, no relapse (already gated below).
            val archived = current.status == HabitStatus.ARCHIVED
            Monument(state = current)
            MonumentActionsRow(
                showRelapsePill = current.kind == CardKind.ABSTINENCE && !archived,
                showFreezerPill = current.period == Period.DAY && !archived,
                freezersOwned = current.freezersOwned,
                onRelapseClick = { relapseSheetOpen = true },
                onFreezerClick = { showFreezerSheet = true },
            )
            HeatmapSection(
                current = current,
                onPrevMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onDayTap = { day -> if (!archived) daySheetFor = day },
            )
            ComplianceSection(current.windows, windowIndex, onSelect = { windowIndex = it })
            FooterActions(
                status = current.status,
                pausedSinceDay = current.pausedSinceDay,
                onPause = { showPauseSheet = true },
                onResume = viewModel::resume,
                onArchive = { showArchiveSheet = true },
                onUnarchive = viewModel::unarchive,
            )
        }
    }

    daySheetFor?.let { day ->
        val heatmapDay = current.heatmap.find { it.day == day }
        // Equivalent to FreezerEngine.eligibilityOf(...) == ELIGIBLE: a FAILED dot already means
        // period == DAY, compliance FAILED, not already protected; `!isToday` matches the engine's
        // day >= today -> FUTURE_DAY (freezers only ever protect a day already in the past).
        val freezerOffered = heatmapDay?.dot == DayDot.FAILED && heatmapDay.isToday.not() && current.freezersOwned > 0
        DaySheet(
            day = day,
            kind = current.kind,
            currentValue = current.dayValues[day] ?: 0,
            freezerOffered = freezerOffered,
            freezersOwned = current.freezersOwned,
            actions =
                DaySheetActions(
                    onMarkDone = { viewModel.markDayDone(day) },
                    onMarkNotDone = { viewModel.clearDay(day) },
                    onSetValue = { viewModel.setDayValue(day, it) },
                    onRelapse = { viewModel.logRelapseOn(day) },
                    onStayedClean = { viewModel.markCleanAndSeal(day) },
                    onUseFreezer = { viewModel.applyFreezer(day) },
                ),
            onDismiss = { daySheetFor = null },
        )
    }
    if (showFreezerSheet) {
        FreezerSheet(
            owned = current.freezersOwned,
            price = current.freezerPrice,
            balance = current.balance,
            onBuy = viewModel::buyFreezer,
            onDismiss = { showFreezerSheet = false },
        )
    }
    if (showPauseSheet) {
        PauseSheet(onPause = viewModel::pause, onDismiss = { showPauseSheet = false })
    }
    if (showArchiveSheet) {
        ArchiveSheet(onConfirm = viewModel::archive, onDismiss = { showArchiveSheet = false })
    }
    if (relapseSheetOpen) {
        RelapseSheet(
            name = current.name,
            onConfirm = {
                viewModel.logRelapseOn(current.today)
                relapseSheetOpen = false
            },
            onDismiss = { relapseSheetOpen = false },
        )
    }
}

@Composable
private fun DetailHeader(
    name: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlainIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            name,
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        PlainIconButton(
            BitoIcons.Pencil,
            contentDescription = stringResource(R.string.edit_habit_hint, name),
            onClick = onEdit,
        )
    }
}

/**
 * A bare, borderless icon tap target — no pill/outline container, per the 2a mockup's header and
 * calendar-nav chevrons (contrast [com.alvarotc.bito.ui.components.GhostIconButton], which always
 * draws a bordered pill and stays canon for every other call site). The 44dp box is touch comfort
 * only, invisible — [iconSize] is what actually renders.
 */
@Composable
private fun PlainIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Tinta,
    iconSize: Dp = 24.dp,
) = Box(
    modifier
        .size(44.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(iconSize))
}

/**
 * The racha-monument, per the 2a mockup: a [BitoCard] wrapping the biggest number in the app,
 * everything centered — number + flame, unit line, record chip. The freezer/relapse pills that
 * used to live on this card's own row now sit below it as [MonumentActionsRow], full-width pills
 * of their own.
 */
@Composable
private fun Monument(state: DetailUiState) {
    // ZERO/abstinence habits always carry period == DAY (see HabitFormModel's QuitMode.TOTAL
    // shape) — "días limpios" is keyed off the habit's kind, not its period, so a plain DAY-period
    // CHECK/COUNTER habit still reads the ordinary "días" unit.
    val unitRes =
        if (state.kind == CardKind.ABSTINENCE) {
            R.string.unit_days_clean
        } else {
            when (state.period) {
                Period.DAY -> R.string.unit_days
                Period.WEEK -> R.string.unit_weeks
                Period.MONTH -> R.string.unit_months
            }
        }
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The flame sits to the number's right, raised toward its top rather than centered on
            // its full height — Alignment.Top (not CenterVertically) is what reads as "elevada"
            // next to a number many times the flame's own height.
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${state.currentStreak}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 78.sp, fontWeight = FontWeight.Bold),
                    color = Tinta,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    BitoIcons.Flame,
                    contentDescription = null,
                    tint = Brasa,
                    modifier = Modifier.padding(top = 6.dp).size(36.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(unitRes),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = TintaSuave,
            )
            Spacer(Modifier.height(12.dp))
            RecordChip(state.bestStreak)
        }
    }
}

/**
 * Compact per the mockup's chip size (6dp vertical / 12dp horizontal) — the architect's override
 * keeps this chip's brasa-tinte/brasa colors against the mockup's plain papel treatment.
 */
@Composable
private fun RecordChip(bestStreak: Int) {
    Row(
        Modifier.clip(CircleShape).background(BrasaTinte).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.record_chip, bestStreak),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = Brasa,
        )
    }
}

/**
 * The pill row under the monument card: "He recaído" (ZERO habits) and the freezer pill (DAY
 * period), each Row-weight-equal so a lone survivor takes the full row on its own. Neither gate
 * changes from the old standalone call sites — only their position and shape do.
 */
@Composable
private fun MonumentActionsRow(
    showRelapsePill: Boolean,
    showFreezerPill: Boolean,
    freezersOwned: Int,
    onRelapseClick: () -> Unit,
    onFreezerClick: () -> Unit,
) {
    if (!showRelapsePill && !showFreezerPill) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showRelapsePill) {
            RelapsePill(onClick = onRelapseClick, modifier = Modifier.weight(1f).testTag("relapse-pill"))
        }
        if (showFreezerPill) {
            FreezerPill(freezersOwned, onClick = onFreezerClick, modifier = Modifier.weight(1f).testTag("freezer-chip"))
        }
    }
}

/** "He recaído": a discreet, border-only pill — no fill, no destructive color (canon per GUIA). */
@Composable
private fun RelapsePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .border(1.dp, Borde, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.relapse_action),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = TintaSuave,
        )
    }
}

/**
 * The freezer pill: architect override keeps the old chip's hoja-tinte fill / hoja+tinta content
 * against the mockup's plain outline, resized into the mockup's 56dp pill. The count renders bold
 * and larger than the trailing word — [buildAnnotatedString] splits the localized "%1$d word"
 * string on its first space rather than hardcoding word order, since [R.string.freezer_chip_label]
 * already puts the digits first in both shipped locales.
 */
@Composable
private fun FreezerPill(
    owned: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(HojaTinte)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        val label = stringResource(R.string.freezer_chip_label, owned)
        val splitAt = label.indexOf(' ').let { if (it == -1) label.length else it }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)) {
                    append(label.substring(0, splitAt))
                }
                if (splitAt < label.length) {
                    withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)) {
                        append(label.substring(splitAt))
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = Tinta,
        )
    }
}

@Composable
private fun HeatmapSection(
    current: DetailUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayTap: (LogicalDay) -> Unit,
) {
    val currentRealMonth = remember(current.today) { YearMonth.from(LocalDate.ofEpochDay(current.today.toLong())) }
    val nextDisabled = current.month >= currentRealMonth
    // The month header keeps the card's usual 20dp inset; only the day grid gets a tighter one of
    // its own (8dp, plus a 3dp inter-cell gap instead of 4dp) — at BitoCard's default 20dp on all
    // sides, 7 columns landed at ~41-44dp, under the guide's ≥44dp touch floor at the narrowest
    // supported width. Trimming just the grid's budget (not the header's) clears it with headroom
    // at both w393dp and w411dp without touching the screen's 20dp margins.
    // Lowercase per the mockup ("agosto"); the year only joins in when it isn't the current one
    // ("agosto 2025"), same as a plain "MMMM" vs "MMMM yyyy" pattern switch.
    val monthLabel =
        remember(current.month, currentRealMonth) {
            val pattern = if (current.month.year != currentRealMonth.year) "MMMM yyyy" else "MMMM"
            current.month.atDay(1)
                .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
                .lowercase(Locale.getDefault())
        }
    BitoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.padding(vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    monthLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    color = TintaSuave,
                    modifier = Modifier.weight(1f),
                )
                PlainIconButton(
                    BitoIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.previous_month),
                    onClick = onPrevMonth,
                    color = TintaSuave,
                    iconSize = 20.dp,
                )
                PlainIconButton(
                    BitoIcons.ChevronRight,
                    contentDescription = stringResource(R.string.next_month),
                    onClick = { if (!nextDisabled) onNextMonth() },
                    color = if (nextDisabled) TintaSuave.copy(alpha = 0.4f) else TintaSuave,
                    iconSize = 20.dp,
                )
            }
            Spacer(Modifier.height(8.dp))
            DotHeatmap(
                days = current.heatmap,
                onDayTap = onDayTap,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("heatmap"),
            )
        }
    }
}

@Composable
private fun ComplianceSection(
    windows: List<WindowStats>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val labels = WINDOW_LABEL_RES.map { stringResource(it) }
    val selected = windows.getOrNull(selectedIndex)
    val percent = selected?.percent
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        SegmentedPills(
            options = labels,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            fillWidth = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            percent?.let { "$it%" } ?: stringResource(R.string.no_data_placeholder),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold),
            color = if (percent != null && percent >= 80) Hoja else Tinta,
        )
        if (selected != null && percent != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.compliance_caption,
                    stringResource(R.string.compliance_fraction, selected.fulfilled, selected.judged),
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color = TintaSuave,
            )
        }
    }
}

@Composable
private fun FooterActions(
    status: HabitStatus,
    pausedSinceDay: LogicalDay?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
) {
    // Archived habits offer only "Reactivar" — every other registration/lifecycle action is
    // gated off the screen above (freezer chip, day-tap sheet, relapse pill).
    if (status == HabitStatus.ARCHIVED) {
        PillButton(
            stringResource(R.string.unarchive_habit),
            onClick = onUnarchive,
            modifier = Modifier.fillMaxWidth().testTag("reactivate"),
        )
        return
    }
    Column {
        if (status == HabitStatus.PAUSED && pausedSinceDay != null) {
            val since =
                remember(pausedSinceDay) {
                    LocalDate.ofEpochDay(pausedSinceDay.toLong()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                }
            Text(stringResource(R.string.paused_since, since), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (status == HabitStatus.PAUSED) {
                LifecycleActionButton(
                    text = stringResource(R.string.resume_habit),
                    icon = BitoIcons.Play,
                    onClick = onResume,
                    modifier = Modifier.weight(1f).testTag("resume"),
                )
            } else {
                LifecycleActionButton(
                    text = stringResource(R.string.pause_habit),
                    icon = BitoIcons.Pause,
                    onClick = onPause,
                    modifier = Modifier.weight(1f).testTag("pause"),
                )
            }
            LifecycleActionButton(
                text = stringResource(R.string.archive_habit),
                icon = BitoIcons.Archive,
                onClick = onArchive,
                modifier = Modifier.weight(1f).testTag("archive"),
            )
        }
    }
}

/**
 * Pausar/Archivar canon: two equal-weight outlined pills, 52dp tall, icon + 15sp label — no
 * longer [com.alvarotc.bito.ui.components.GhostPillButton]'s smaller ghost treatment, which stays
 * as-is for its other call sites (sheets, forms, settings) not touched by this fix.
 */
@Composable
private fun LifecycleActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = 52.dp),
    shape = CircleShape,
    border = BorderStroke(1.dp, Borde),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = TintaSuave),
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
}
