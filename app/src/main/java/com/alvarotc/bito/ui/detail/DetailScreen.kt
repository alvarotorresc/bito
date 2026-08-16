package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
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
    // open bounces back once it was seen to exist; a cold-start null never fires onBack.
    var hasLoaded by rememberSaveable { mutableStateOf(false) }
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
            Monument(current)
            if (current.period == Period.DAY) {
                FreezerChip(current.freezersOwned, onClick = { showFreezerSheet = true })
            }
            HeatmapSection(
                current = current,
                onPrevMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onDayTap = { daySheetFor = it },
            )
            ComplianceSection(current.windows, windowIndex, onSelect = { windowIndex = it })
            if (current.kind == CardKind.ABSTINENCE && current.status != HabitStatus.ARCHIVED) {
                GhostPillButton(
                    stringResource(R.string.relapse_action),
                    onClick = { relapseSheetOpen = true },
                    color = Peligro,
                    borderColor = PeligroTinte,
                    modifier = Modifier.testTag("relapse-pill"),
                )
            }
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
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            name,
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        GhostIconButton(
            BitoIcons.Pencil,
            contentDescription = stringResource(R.string.edit_habit_hint, name),
            onClick = onEdit,
        )
    }
}

/** The typographic monument: no solid accent card — the racha number itself is the protagonist. */
@Composable
private fun Monument(state: DetailUiState) {
    val unitRes =
        when (state.period) {
            Period.DAY -> R.string.unit_days
            Period.WEEK -> R.string.unit_weeks
            Period.MONTH -> R.string.unit_months
        }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.height(40.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${state.currentStreak}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold),
                color = Tinta,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(unitRes), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.clip(CircleShape).background(BrasaTinte).padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.record_chip, state.bestStreak),
                style = MaterialTheme.typography.labelMedium,
                color = Brasa,
            )
        }
    }
}

@Composable
private fun FreezerChip(
    owned: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(HojaTinte)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("freezer-chip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.height(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.freezer_chip_label, owned), style = MaterialTheme.typography.labelMedium, color = Tinta)
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
    BitoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.padding(vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostIconButton(
                    BitoIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.previous_month),
                    onClick = onPrevMonth,
                )
                Text(
                    current.month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    color = Tinta,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                GhostIconButton(
                    BitoIcons.ChevronRight,
                    contentDescription = stringResource(R.string.next_month),
                    onClick = { if (!nextDisabled) onNextMonth() },
                    color = if (nextDisabled) TintaSuave.copy(alpha = 0.4f) else TintaSuave,
                )
            }
            Spacer(Modifier.height(12.dp))
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
        SegmentedPills(options = labels, selectedIndex = selectedIndex, onSelect = onSelect)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                percent?.let { "$it%" } ?: stringResource(R.string.no_data_placeholder),
                style = MaterialTheme.typography.displayLarge,
                color = if (percent != null && percent >= 80) Hoja else Tinta,
            )
            if (selected != null && percent != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.compliance_fraction, selected.fulfilled, selected.judged),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave,
                )
            }
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
        PillButton(stringResource(R.string.unarchive_habit), onClick = onUnarchive, modifier = Modifier.testTag("reactivate"))
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == HabitStatus.PAUSED) {
                GhostPillButton(
                    stringResource(R.string.resume_habit),
                    onClick = onResume,
                    modifier = Modifier.testTag("resume"),
                    icon = BitoIcons.Play,
                )
            } else {
                GhostPillButton(stringResource(R.string.pause_habit), onClick = onPause, modifier = Modifier.testTag("pause"))
            }
            GhostPillButton(
                stringResource(R.string.archive_habit),
                onClick = onArchive,
                modifier = Modifier.testTag("archive"),
            )
        }
    }
}
