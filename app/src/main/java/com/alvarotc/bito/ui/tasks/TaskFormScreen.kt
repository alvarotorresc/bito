@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.formatDayMedium
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import java.time.LocalDate

/** A [LogicalDay] is a UTC epoch day; the picker speaks UTC millis. One day is exactly this many. */
private const val MILLIS_PER_DAY = 86_400_000L

private fun LogicalDay.toUtcMillis(): Long = toLong() * MILLIS_PER_DAY

private fun Long.toLogicalDay(): LogicalDay = LocalDate.ofEpochDay(this / MILLIS_PER_DAY).toEpochDay().toInt()

/**
 * Creates a task, or edits one when [initial] carries a [TaskFormState.editingId] — full-screen
 * now (D12 revoked: a task's own form deserves the same weight as a habit's), calcada
 * estructuralmente de [com.alvarotc.bito.ui.habitform.HabitFormScreen]: same [Scaffold], same
 * header rhythm, same [BitoCard]-wrapped fields, same save button shape, and — when [onDelete] is
 * given, i.e. only ever on the editing route — the same red delete button at the end with the
 * same confirm-before-delete sheet. The model and its validation are untouched —
 * [TaskFormState]/[TaskFormState.canSave] are exactly what the old sheet used; only the continent
 * changed.
 */
@Composable
fun TaskFormScreen(
    initial: TaskFormState,
    today: LogicalDay,
    onSave: (TaskFormState) -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var state by remember { mutableStateOf(initial) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("task-form-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskFormHeader(state.isEditing, onBack)
            TaskTitleField(state.title) { state = state.copy(title = it) }
            TaskFirstStepField(state.firstStep) { state = state.copy(firstStep = it) }
            TaskDueSection(
                state = state,
                today = today,
                onSelect = { index ->
                    when (index) {
                        0 -> state = state.copy(dueKind = DueKind.NONE, dueDay = null)
                        1 -> state = state.copy(dueKind = DueKind.WEEK, dueDay = null)
                        else -> showDatePicker = true
                    }
                },
            )
            PillButton(
                text = stringResource(R.string.save),
                onClick = { onSave(state) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().testTag("task-form-save"),
            )
            if (onDelete != null) {
                GhostPillButton(
                    text = stringResource(R.string.task_delete),
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth().testTag("delete"),
                    color = Peligro,
                    borderColor = PeligroTinte,
                )
            }
        }
    }

    if (confirmingDelete) {
        DeleteTaskConfirmSheet(
            onConfirm = {
                confirmingDelete = false
                onDelete?.invoke()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    if (showDatePicker) {
        val todayMillis = today.toUtcMillis()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = state.datePickerSeed(today).toUtcMillis(),
                selectableDates =
                    object : SelectableDates {
                        // No past days: today itself is still a valid deadline.
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        state = state.copy(dueKind = DueKind.DATE, dueDay = millis.toLogicalDay())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.task_form_date_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Mirrors [com.alvarotc.bito.ui.habitform.HabitFormScreen]'s own FormHeader: back chevron, then
 * the title — task_form_title/task_form_edit_title, the same keys the old sheet already used, so
 * no new string entered the resources for this screen's own header. */
@Composable
private fun TaskFormHeader(
    isEditing: Boolean,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), tint = Tinta)
        }
        Text(
            stringResource(if (isEditing) R.string.task_form_edit_title else R.string.task_form_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
        )
    }
}

/** Transparent M3 [TextField] colors so a wrapping [BitoCard] reads as the only surface — the
 * exact copy [com.alvarotc.bito.ui.habitform.HabitFormScreen] keeps under the same name in its
 * own file; two screen-local copies of one tiny color table, not a shared one, same as neither
 * screen shares its other private helpers (FormHeader, etc.) with the other. */
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
private fun TaskTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth().testTag("task-title-field"),
            placeholder = { Text(stringResource(R.string.task_form_name_hint), color = TintaSuave) },
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Tinta),
            colors = borderlessFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

@Composable
private fun TaskFirstStepField(
    firstStep: String,
    onFirstStepChange: (String) -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = firstStep,
            onValueChange = onFirstStepChange,
            modifier = Modifier.fillMaxWidth().testTag("task-first-step-field"),
            placeholder = { Text(stringResource(R.string.task_form_step_hint), color = TintaSuave) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Tinta),
            colors = borderlessFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** The three due-date degrees, same [SegmentedPills] the sheet already drove — only now inside a
 * [BitoCard], matching the rhythm every other section of the habit form keeps. */
@Composable
private fun TaskDueSection(
    state: TaskFormState,
    today: LogicalDay,
    onSelect: (Int) -> Unit,
) {
    val chosenDate = state.dueDay
    val dateLabel =
        if (state.dueKind == DueKind.DATE && chosenDate != null) {
            formatDayMedium(chosenDate)
        } else {
            stringResource(R.string.task_form_due_date)
        }
    val selectedIndex =
        when (state.dueKind) {
            DueKind.NONE -> 0
            DueKind.WEEK -> 1
            DueKind.DATE -> 2
        }
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        SegmentedPills(
            options = listOf(stringResource(R.string.task_form_due_none), stringResource(R.string.task_form_due_week), dateLabel),
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            fillWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Calcado de [com.alvarotc.bito.ui.habitform.HabitFormScreen]'s own DeleteConfirmSheet, con su
 * propio copy: sin deshacer, la confirmacion ES el deshacer. Compartida — no privada de este
 * fichero — porque [TasksScreen]'s own row menu also opens this exact sheet for its own delete
 * flow; moved here instead of kept in each file so neither duplicates it.
 */
@Composable
internal fun DeleteTaskConfirmSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("task-delete-confirm")) {
            Text(stringResource(R.string.task_delete_confirm_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.task_delete_confirm_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            PillButton(
                stringResource(R.string.delete_confirm_yes),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().testTag("task-delete-confirm-yes"),
                containerColor = Peligro,
            )
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
