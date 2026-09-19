@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.formatDayMedium
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import java.time.LocalDate

/** A [LogicalDay] is a UTC epoch day; the picker speaks UTC millis. One day is exactly this many. */
private const val MILLIS_PER_DAY = 86_400_000L

private fun LogicalDay.toUtcMillis(): Long = toLong() * MILLIS_PER_DAY

private fun Long.toLogicalDay(): LogicalDay = LocalDate.ofEpochDay(this / MILLIS_PER_DAY).toEpochDay().toInt()

/** Creates a task, or edits one when [initial] carries an [TaskFormState.editingId]. */
@Composable
fun TaskFormSheet(
    initial: TaskFormState,
    today: LogicalDay,
    onSave: (TaskFormState) -> Unit,
    onDismiss: () -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("task-form-sheet")) {
            Text(
                stringResource(if (state.isEditing) R.string.task_form_edit_title else R.string.task_form_title),
                style = MaterialTheme.typography.titleMedium,
                color = Tinta,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.title,
                onValueChange = { state = state.copy(title = it) },
                label = { Text(stringResource(R.string.task_form_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.firstStep,
                onValueChange = { state = state.copy(firstStep = it) },
                label = { Text(stringResource(R.string.task_form_step_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
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
            SegmentedPills(
                options = listOf(stringResource(R.string.task_form_due_none), stringResource(R.string.task_form_due_week), dateLabel),
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    when (index) {
                        0 -> state = state.copy(dueKind = DueKind.NONE, dueDay = null)
                        1 -> state = state.copy(dueKind = DueKind.WEEK, dueDay = null)
                        else -> showDatePicker = true
                    }
                },
                fillWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.save),
                onClick = { onSave(state) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().testTag("task-form-save"),
            )
        }
    }

    if (showDatePicker) {
        val todayMillis = today.toUtcMillis()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = todayMillis,
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
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** The "+" button's first question: a habit, or a task. */
@Composable
fun CreateChoiceSheet(
    onHabit: () -> Unit,
    onTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("create-choice-sheet")) {
            Text(stringResource(R.string.create_choice_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.create_choice_habit), onClick = onHabit, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.create_choice_task), onClick = onTask, modifier = Modifier.fillMaxWidth())
        }
    }
}
