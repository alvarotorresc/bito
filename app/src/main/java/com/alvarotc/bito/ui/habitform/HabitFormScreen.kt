@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.habitform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.NumberInputSheet
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.components.TimePickerSheet
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** One screen, no wizard: create and edit share every field, the preset just gates what's shown. */
@Composable
fun HabitFormScreen(
    viewModel: HabitFormViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val personality by viewModel.personality.collectAsStateWithLifecycle()
    val equipped by viewModel.equipped.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }

    // Same launcher SettingsScreen.kt uses for the first GLOBAL reminder: fire-and-forget, the
    // save below never waits on nor blocks on the result.
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormHeader(state.isEditing, onBack)
            SpeechBubble(
                stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(personality))),
                stringResource(HabiVoice.formPromptRes(personality)),
                modifier = Modifier.fillMaxWidth(),
                // The same mini-Habi slot the Stats commentator and FreezerInfoSheet fill: 40dp,
                // resting frame. This ViewModel is about the habit being built, not Habi's state,
                // so the face stays the neutral resting one — but it wears the user's real
                // equipped set: the Habi on every screen is THE user's Habi.
                avatar = {
                    HabiAvatar(
                        HabiSpec(Mood.NORMAL, personality, equipped),
                        Modifier.size(40.dp),
                        animated = false,
                    )
                },
            )
            NameField(state.name, viewModel::setName)
            PresetPills(state.preset, state.isEditing, viewModel::selectPreset)
            TargetSection(
                state = state,
                onAdjustTarget = viewModel::adjustTarget,
                onSetTarget = viewModel::setTarget,
                onSelectPeriod = viewModel::selectPeriod,
                onSelectQuitMode = viewModel::selectQuitMode,
                onSelectLimitMetric = viewModel::selectLimitMetric,
                onUnitChange = viewModel::setUnit,
            )
            MoreOptionsSection(state, viewModel::toggleBinary, viewModel::adjustStep, viewModel::setReminder)
            PillButton(
                text = stringResource(if (state.isEditing) R.string.save_habit else R.string.create_habit),
                onClick = {
                    // QUIT's own reminder can never fire (ReminderUseCase.kt HABIT branch: a QUIT
                    // card is always doneToday or failed) — asking for the permission here would be
                    // fishing for access with nothing to justify it (same rule SettingsScreen.kt
                    // documents for its own first-reminder prompt).
                    if (state.reminderMinutes != null &&
                        state.preset != HabitPreset.QUIT &&
                        Build.VERSION.SDK_INT >= 33 &&
                        !NotificationManagerCompat.from(context).areNotificationsEnabled()
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.save(onBack)
                },
                enabled = state.canSave && !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("save"),
            )
            if (state.isEditing) {
                GhostPillButton(
                    text = stringResource(R.string.delete_habit),
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth().testTag("delete"),
                    color = Peligro,
                    borderColor = PeligroTinte,
                )
            }
        }
    }

    if (confirmingDelete) {
        DeleteConfirmSheet(
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun FormHeader(
    isEditing: Boolean,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), tint = Tinta)
        }
        Text(
            stringResource(if (isEditing) R.string.form_title_edit else R.string.form_title_new),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
        )
    }
}

/** Transparent M3 [TextField] colors so a wrapping [BitoCard] reads as the only surface. */
@Composable
private fun borderlessFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = Hoja,
    )

@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().testTag("name-field"),
            placeholder = { Text(stringResource(R.string.name_hint), color = TintaSuave) },
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Tinta),
            colors = borderlessFieldColors(),
            // QA3: names read better sentence-cased ("Meditar" not "meditar"); the keyboard
            // itself opens on a capital letter instead of forcing a manual shift.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** Not private: [com.alvarotc.bito.ui.onboarding.OnboardingScreen]'s 7g preset pills reuse these
 * exact labels rather than duplicating the mapping — same strings, same five presets. */
internal fun HabitPreset.labelRes(): Int =
    when (this) {
        HabitPreset.DAILY_CHECK -> R.string.preset_daily
        HabitPreset.QUANTITY -> R.string.preset_quantity
        HabitPreset.DURATION -> R.string.preset_duration
        HabitPreset.WEEKLY_TIMES -> R.string.preset_weekly
        HabitPreset.QUIT -> R.string.preset_quit
    }

/** The line glyph each type pill leads with — canon pills.html + mockup 7g (check, dots, clock,
 * calendar, ban). Internal next to [labelRes] for the same reason: one preset→glyph mapping,
 * ready for onboarding's mirrored pills to reuse. */
internal fun HabitPreset.iconVector(): ImageVector =
    when (this) {
        HabitPreset.DAILY_CHECK -> BitoIcons.Check
        HabitPreset.QUANTITY -> BitoIcons.Ellipsis
        HabitPreset.DURATION -> BitoIcons.Clock
        HabitPreset.WEEKLY_TIMES -> BitoIcons.Calendar
        HabitPreset.QUIT -> BitoIcons.Ban
    }

/** Mockup 7g's reading order frozen into two full-width rows: the three short labels up top, the
 * two long ones below. Each row splits its width equally, so both edges stay flush (the old
 * FlowRow wrapped ragged) and every label keeps room at fontScale 1.15 in both languages. */
private val PRESET_ROWS =
    listOf(
        listOf(HabitPreset.DAILY_CHECK, HabitPreset.QUANTITY, HabitPreset.DURATION),
        listOf(HabitPreset.WEEKLY_TIMES, HabitPreset.QUIT),
    )

/** Rule E2: the metric a preset resolves to can't change once a habit exists — presets lock on edit. */
@Composable
private fun PresetPills(
    selected: HabitPreset,
    locked: Boolean,
    onSelect: (HabitPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESET_ROWS.forEach { rowPresets ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPresets.forEach { preset ->
                    PresetPill(
                        preset = preset,
                        isSelected = preset == selected,
                        locked = locked,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (locked) {
            Text(stringResource(R.string.preset_locked_hint), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
    }
}

/** One type pill: leading glyph + label, centered in whatever width its row cell grants. Selection
 * only recolors (HojaTinte fill, Tinta content — GUIA's active-icon rule), never resizes, so
 * picking a type can't reflow the block. */
@Composable
private fun PresetPill(
    preset: HabitPreset,
    isSelected: Boolean,
    locked: Boolean,
    onSelect: (HabitPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // [C]: no Role/selected semantics — every pill reads as "<label>, Button" with no sign
        // which one is active. `Surface(onClick = ...)` stays as-is rather than becoming a bare
        // `Modifier.selectable` (its interactive overload applies
        // `minimumInteractiveComponentSize()`, which the plain one doesn't — swapping would risk
        // shrinking this pill's touch target). Same additive shape as onboarding's
        // `HabitPresetPill` fix for this exact mirrored component.
        onClick = { onSelect(preset) },
        enabled = !locked,
        shape = CircleShape,
        color = if (isSelected) HojaTinte else Tarjeta,
        border = BorderStroke(1.dp, Borde),
        modifier =
            modifier
                .testTag("preset-${preset.name}")
                .semantics {
                    this.selected = isSelected
                    role = Role.Tab
                },
    ) {
        val tint = if (isSelected) Tinta else TintaSuave
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            // 6dp inset, not the classic 14: content centers in a weight-granted cell, so the
            // inset is only a floor — measured tight so "Cantidad" holds one line on a 360dp
            // screen at fontScale 1.15 (HabitFormPresetPillLayoutTest pins exactly this).
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            Icon(preset.iconVector(), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                stringResource(preset.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PERIOD_OPTIONS = listOf(Period.DAY, Period.WEEK, Period.MONTH)

/**
 * Hidden entirely for DAILY_CHECK. adjustTarget/selectPeriod already carry their own guards
 * (QUIT TOTAL pins at 0, WEEKLY_TIMES fixes WEEK), so this UI needs no extra state guards.
 *
 * Rule E2 extends past the preset: [HabitFormViewModel.save]'s edit branch only ever persists
 * name/target/unit/step/reminderMinutes, so period, quit-mode and limit-metric are shape, not
 * value — they lock alongside the preset pills when editing. Target, unit, step and reminder do
 * persist and stay live.
 */
@Composable
private fun TargetSection(
    state: HabitFormState,
    onAdjustTarget: (Int) -> Unit,
    onSetTarget: (Int) -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onSelectQuitMode: (QuitMode) -> Unit,
    onSelectLimitMetric: (Metric) -> Unit,
    onUnitChange: (String) -> Unit,
) {
    if (state.preset == HabitPreset.DAILY_CHECK) return
    val shapeLocked = state.isEditing

    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.preset == HabitPreset.QUIT) {
                SegmentedPills(
                    options = listOf(stringResource(R.string.quit_total), stringResource(R.string.quit_limit)),
                    selectedIndex = if (state.quitMode == QuitMode.TOTAL) 0 else 1,
                    onSelect = { onSelectQuitMode(if (it == 0) QuitMode.TOTAL else QuitMode.LIMIT) },
                    enabled = !shapeLocked,
                )
                if (state.quitMode == QuitMode.LIMIT) {
                    SegmentedPills(
                        options = listOf(stringResource(R.string.limit_amount), stringResource(R.string.limit_time)),
                        selectedIndex = if (state.limitMetric == Metric.COUNT) 0 else 1,
                        onSelect = { onSelectLimitMetric(if (it == 0) Metric.COUNT else Metric.DURATION) },
                        enabled = !shapeLocked,
                    )
                }
            }

            TargetStepper(state, onAdjustTarget, onSetTarget)

            val showPeriod =
                state.preset == HabitPreset.QUANTITY || state.preset == HabitPreset.DURATION ||
                    (state.preset == HabitPreset.QUIT && state.quitMode == QuitMode.LIMIT)
            if (showPeriod) {
                SegmentedPills(
                    options =
                        listOf(
                            stringResource(R.string.period_day),
                            stringResource(R.string.period_week),
                            stringResource(R.string.period_month),
                        ),
                    selectedIndex = PERIOD_OPTIONS.indexOf(state.period),
                    onSelect = { onSelectPeriod(PERIOD_OPTIONS[it]) },
                    enabled = !shapeLocked,
                )
            }

            if (state.preset == HabitPreset.QUANTITY) {
                // QA3 deliberately stops at the name field: units are written lowercase by
                // convention ("vasos", "min"), so no KeyboardCapitalization here — noted for the PR.
                TextField(
                    value = state.unit,
                    onValueChange = onUnitChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.unit_hint), color = TintaSuave) },
                    colors = borderlessFieldColors(),
                )
            }
        }
    }
}

/**
 * QA4/QA5: minute targets (Duración, or a QUIT limit measured in minutes) are painful to reach
 * one tap at a time, so they get an extra ±10 stride. The central value is always tappable —
 * across every preset this stepper renders for — and opens [NumberInputSheet] for direct entry.
 */
@Composable
private fun TargetStepper(
    state: HabitFormState,
    onAdjustTarget: (Int) -> Unit,
    onSetTarget: (Int) -> Unit,
) {
    val isQuitLimitTime = state.preset == HabitPreset.QUIT && state.quitMode == QuitMode.LIMIT && state.limitMetric == Metric.DURATION
    val isMinuteTarget = state.preset == HabitPreset.DURATION || isQuitLimitTime
    val unitLabel =
        when {
            isMinuteTarget -> stringResource(R.string.unit_min)
            state.preset == HabitPreset.QUANTITY -> state.unit.takeIf(String::isNotBlank)
            else -> null
        }
    var showNumberInput by remember { mutableStateOf(false) }

    // The minute row packs five controls (±10, ±1, value) into one line. A Material3 IconButton
    // enforces a 48dp touch target no matter what size modifier it's given, so reusing it for
    // ±10 would overflow a ~360dp-wide screen once card/screen padding is subtracted (see the
    // narrow-width regression test). StepChip opts out of that enforced minimum — it's a
    // smaller, purpose-built tap target shared by all four step controls — and the row's own
    // spacing tightens only while the extra pair is present; the three-control case is untouched.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isMinuteTarget) 4.dp else 12.dp),
    ) {
        if (isMinuteTarget) {
            StepChip(
                onClick = { onAdjustTarget(-10) },
                contentDescription = stringResource(R.string.target_minus10_cd),
                modifier = Modifier.testTag("target-minus10"),
            ) {
                Text(
                    "−10",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tinta,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
        StepChip(
            onClick = { onAdjustTarget(-1) },
            contentDescription = stringResource(R.string.target_minus1_cd),
            modifier = Modifier.testTag("target-minus"),
        ) {
            Icon(BitoIcons.Minus, contentDescription = null, tint = Tinta, modifier = Modifier.size(16.dp))
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { showNumberInput = true }.testTag("target-value"),
        ) {
            Text("${state.target}", style = MaterialTheme.typography.displayLarge, color = Tinta)
            if (unitLabel != null) {
                Text(unitLabel, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            }
        }
        StepChip(
            onClick = { onAdjustTarget(1) },
            contentDescription = stringResource(R.string.target_plus1_cd),
            modifier = Modifier.testTag("target-plus"),
        ) {
            Icon(BitoIcons.Plus, contentDescription = null, tint = Tinta, modifier = Modifier.size(16.dp))
        }
        if (isMinuteTarget) {
            StepChip(
                onClick = { onAdjustTarget(10) },
                contentDescription = stringResource(R.string.target_plus10_cd),
                modifier = Modifier.testTag("target-plus10"),
            ) {
                Text(
                    "+10",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tinta,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }

    if (showNumberInput) {
        NumberInputSheet(
            title = stringResource(R.string.target_input_title),
            initial = state.target,
            onConfirm = {
                onSetTarget(it)
                showNumberInput = false
            },
            onDismiss = { showNumberInput = false },
        )
    }
}

/**
 * The single visual language for all four [TargetStepper] step controls (±1, ±10) — see the
 * row-overflow comment above its call site for why this isn't a Material3 IconButton.
 *
 * [A]: [contentDescription] is an explicit override on the chip's own node, not left to the
 * merged-in child — the ±1 chips' `Icon(contentDescription = null)` would otherwise carry no
 * label, and the ±10 chips' plain `Text("−10"/"+10")` reads ambiguous out of context ("minus one
 * zero"). One real action string per chip fixes both the same way.
 */
@Composable
private fun StepChip(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(40.dp)
            .widthIn(min = 40.dp)
            .clip(CircleShape)
            .background(Tarjeta)
            .border(1.dp, Borde, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Minutes-of-day a fresh reminder opens on when none is set yet: 08:00. */
private const val DEFAULT_REMINDER_MINUTES = 8 * 60

@Composable
private fun MoreOptionsSection(
    state: HabitFormState,
    onToggleBinary: () -> Unit,
    onAdjustStep: (Int) -> Unit,
    onSetReminder: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "more-options-chevron")

    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.more_options),
                style = MaterialTheme.typography.titleMedium,
                color = Tinta,
                modifier = Modifier.weight(1f),
            )
            Icon(BitoIcons.ChevronDown, contentDescription = null, tint = Tinta, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                if (state.preset == HabitPreset.QUANTITY || state.preset == HabitPreset.DURATION) {
                    // logMode isn't persisted on edit (Task 10 save() carries
                    // name/target/unit/step/reminderMinutes only) — it's shape, like the preset,
                    // so it locks the same way.
                    BinaryModeRow(state.binaryMode, enabled = !state.isEditing, onToggle = onToggleBinary)
                }
                if (state.preset == HabitPreset.QUANTITY && !state.binaryMode) {
                    StepRow(state.step, onAdjustStep)
                }
                // QA1: every preset lands here now — Daily/Weekly/Quit had nothing of their own
                // before this row, so the panel used to open empty for three of the five presets.
                // Reminder is deliberately outside every preset gate above and stays editable in
                // edit mode: it's a schedule detail, not part of the locked (metric/direction) shape.
                // QUIT is the one exception: its own reminder can never fire (see the save-button
                // comment above), so the row is honest about that instead of offering a dead control.
                ReminderRow(
                    reminderMinutes = state.reminderMinutes,
                    disabled = state.preset == HabitPreset.QUIT,
                    onOpenPicker = { showTimePicker = true },
                    onClear = { onSetReminder(null) },
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerSheet(
            title = stringResource(R.string.form_reminder_label),
            initialMinutes = state.reminderMinutes ?: DEFAULT_REMINDER_MINUTES,
            onConfirm = {
                onSetReminder(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** "HH:MM", zero-padded, 24h — matches [TimePickerSheet]'s is24Hour clock. */
private fun formatReminderTime(minutes: Int): String {
    val hh = (minutes / 60).toString().padStart(2, '0')
    val mm = (minutes % 60).toString().padStart(2, '0')
    return "$hh:$mm"
}

/**
 * The open-picker tap target and the clear button are siblings, not nested: an icon button
 * inside a clickable row would give the row two overlapping tap targets fighting for the same
 * touch area, which is a confusing surface regardless of which one wins the gesture.
 *
 * [disabled] (QUIT presets — gap d) turns off the tap target and hides the clear button instead
 * of removing the row outright: the stored [reminderMinutes] survives a preset switch (nothing
 * clears it), so an honest hint explains why it's inert rather than pretending it doesn't exist.
 */
@Composable
private fun ReminderRow(
    reminderMinutes: Int?,
    disabled: Boolean,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(enabled = !disabled) { onOpenPicker() }.testTag("reminder-row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.form_reminder_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (disabled) TintaSuave else Tinta,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    reminderMinutes?.let(::formatReminderTime) ?: stringResource(R.string.form_reminder_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TintaSuave,
                )
            }
            if (!disabled && reminderMinutes != null) {
                IconButton(onClick = onClear, modifier = Modifier.testTag("reminder-clear")) {
                    Icon(BitoIcons.X, contentDescription = stringResource(R.string.form_reminder_clear), tint = TintaSuave)
                }
            }
        }
        if (disabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.form_reminder_quit_hint),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
        }
    }
}

/**
 * [E]: the [Switch] carries no text of its own, sitting in a plain, non-clickable [Row] next to —
 * not merged with — its label+hint [Column]. TalkBack used to announce the label on one swipe and
 * a bare "Switch, On" on the next, with the two never connected. A plain `mergeDescendants = true`
 * on the row is NOT enough (verified empirically in SettingsScreen's identical rows): [Switch] is
 * itself an independently screenreader-focusable node, so it stays a separate reachable stop under
 * a merely-merging ancestor — same reason the app's own convention never nests an `IconButton`
 * inside an already-clickable row. `toggleable` on the row instead MOVES the toggle action there
 * (`Switch(onCheckedChange = null)` makes the switch purely visual) — the standard Android
 * Settings-list a11y idiom, same fix as [ReminderRow]'s sibling-not-nested comment is the mirror
 * image of (there, two actions stay apart; here, one action and its label merge into one).
 */
@Composable
private fun BinaryModeRow(
    binaryMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .testTag("binary-mode-row")
            .toggleable(value = binaryMode, enabled = enabled, onValueChange = { onToggle() }, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.binary_mode), style = MaterialTheme.typography.bodyLarge, color = Tinta)
            Text(
                stringResource(R.string.binary_mode_hint),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
        }
        Switch(checked = binaryMode, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun StepRow(
    step: Int,
    onAdjustStep: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.step_label),
            style = MaterialTheme.typography.bodyLarge,
            color = Tinta,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onAdjustStep(-1) }) {
            Icon(BitoIcons.Minus, contentDescription = stringResource(R.string.step_minus_cd), tint = Tinta)
        }
        Text("$step", style = MaterialTheme.typography.titleMedium, color = Tinta)
        IconButton(onClick = { onAdjustStep(1) }) {
            Icon(BitoIcons.Plus, contentDescription = stringResource(R.string.step_plus_cd), tint = Tinta)
        }
    }
}

@Composable
private fun DeleteConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.delete_confirm_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.delete_confirm_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            PillButton(
                stringResource(R.string.delete_confirm_yes),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Peligro,
            )
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
