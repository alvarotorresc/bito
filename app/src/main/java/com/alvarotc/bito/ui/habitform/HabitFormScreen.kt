@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alvarotc.bito.ui.habitform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.SpeechBubble
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
    var confirmingDelete by remember { mutableStateOf(false) }

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
                stringResource(R.string.habi_speaker, stringResource(R.string.personality_neutra)),
                stringResource(R.string.habi_form_prompt),
            )
            NameField(state.name, viewModel::setName)
            PresetPills(state.preset, state.isEditing, viewModel::selectPreset)
            TargetSection(
                state = state,
                onAdjustTarget = viewModel::adjustTarget,
                onSelectPeriod = viewModel::selectPeriod,
                onSelectQuitMode = viewModel::selectQuitMode,
                onSelectLimitMetric = viewModel::selectLimitMetric,
                onUnitChange = viewModel::setUnit,
            )
            MoreOptionsSection(state, viewModel::toggleBinary, viewModel::adjustStep)
            PillButton(
                text = stringResource(if (state.isEditing) R.string.save_habit else R.string.create_habit),
                onClick = { viewModel.save(onBack) },
                enabled = state.canSave,
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
        )
    }
}

private fun HabitPreset.labelRes(): Int =
    when (this) {
        HabitPreset.DAILY_CHECK -> R.string.preset_daily
        HabitPreset.QUANTITY -> R.string.preset_quantity
        HabitPreset.DURATION -> R.string.preset_duration
        HabitPreset.WEEKLY_TIMES -> R.string.preset_weekly
        HabitPreset.QUIT -> R.string.preset_quit
    }

/** Rule E2: the metric a preset resolves to can't change once a habit exists — presets lock on edit. */
@Composable
private fun PresetPills(
    selected: HabitPreset,
    locked: Boolean,
    onSelect: (HabitPreset) -> Unit,
) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitPreset.entries.forEach { preset ->
                val isSelected = preset == selected
                Surface(
                    onClick = { onSelect(preset) },
                    enabled = !locked,
                    shape = CircleShape,
                    color = if (isSelected) HojaTinte else Tarjeta,
                    border = BorderStroke(1.dp, Borde),
                    modifier = Modifier.testTag("preset-${preset.name}"),
                ) {
                    Text(
                        stringResource(preset.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Tinta else TintaSuave,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (locked) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.preset_locked_hint), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
    }
}

private val PERIOD_OPTIONS = listOf(Period.DAY, Period.WEEK, Period.MONTH)

/**
 * Hidden entirely for DAILY_CHECK. adjustTarget/selectPeriod already carry their own guards
 * (QUIT TOTAL pins at 0, WEEKLY_TIMES fixes WEEK), so this UI needs no extra state guards.
 *
 * Rule E2 extends past the preset: [HabitFormViewModel.save]'s edit branch only ever persists
 * name/target/unit/step, so period, quit-mode and limit-metric are shape, not value — they lock
 * alongside the preset pills when editing. Target, unit and step do persist and stay live.
 */
@Composable
private fun TargetSection(
    state: HabitFormState,
    onAdjustTarget: (Int) -> Unit,
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

            TargetStepper(state, onAdjustTarget)

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

@Composable
private fun TargetStepper(
    state: HabitFormState,
    onAdjustTarget: (Int) -> Unit,
) {
    val isQuitLimitTime = state.preset == HabitPreset.QUIT && state.quitMode == QuitMode.LIMIT && state.limitMetric == Metric.DURATION
    val unitLabel =
        when {
            state.preset == HabitPreset.DURATION || isQuitLimitTime -> stringResource(R.string.unit_min)
            state.preset == HabitPreset.QUANTITY -> state.unit.takeIf(String::isNotBlank)
            else -> null
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = { onAdjustTarget(-1) }, modifier = Modifier.testTag("target-minus")) {
            Icon(BitoIcons.Minus, contentDescription = null, tint = Tinta)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${state.target}", style = MaterialTheme.typography.displayLarge, color = Tinta)
            if (unitLabel != null) {
                Text(unitLabel, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            }
        }
        IconButton(onClick = { onAdjustTarget(1) }, modifier = Modifier.testTag("target-plus")) {
            Icon(BitoIcons.Plus, contentDescription = null, tint = Tinta)
        }
    }
}

@Composable
private fun MoreOptionsSection(
    state: HabitFormState,
    onToggleBinary: () -> Unit,
    onAdjustStep: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
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
                    // logMode isn't persisted on edit (Task 10 save() carries name/target/unit/step
                    // only) — it's shape, like the preset, so it locks the same way.
                    BinaryModeRow(state.binaryMode, enabled = !state.isEditing, onToggle = onToggleBinary)
                }
                if (state.preset == HabitPreset.QUANTITY && !state.binaryMode) {
                    StepRow(state.step, onAdjustStep)
                }
            }
        }
    }
}

@Composable
private fun BinaryModeRow(
    binaryMode: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.binary_mode), style = MaterialTheme.typography.bodyLarge, color = Tinta)
            Text(
                stringResource(R.string.binary_mode_hint),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
        }
        Switch(checked = binaryMode, onCheckedChange = { onToggle() }, enabled = enabled)
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
            Icon(BitoIcons.Minus, contentDescription = null, tint = Tinta)
        }
        Text("$step", style = MaterialTheme.typography.titleMedium, color = Tinta)
        IconButton(onClick = { onAdjustStep(1) }) {
            Icon(BitoIcons.Plus, contentDescription = null, tint = Tinta)
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
