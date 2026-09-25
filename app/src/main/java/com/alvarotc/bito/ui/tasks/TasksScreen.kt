@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.DismissableBitoSnackbar
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * The full tasks list (spec §8.4): five sections that never overlap, straight off [TasksUiState] —
 * Hoy, Esta semana, Con plazo, Sin plazo and, last and folded, Hechas. [onStartFocus] defaults to
 * a no-op, same pattern as [com.alvarotc.bito.ui.today.TodayScreen]'s own: T19 wires it to the
 * focus route, this task only threads the parameter through to every row's "Empezar".
 */
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onBack: () -> Unit,
    onStartFocus: (String) -> Unit = {},
    // D12 revoked: editing now navigates to TaskFormScreen's own route instead of opening a sheet
    // inline here — same default-no-op pattern onStartFocus already carries.
    onEditTask: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val taskDone by viewModel.lastTaskDone.collectAsStateWithLifecycle()
    var doneExpanded by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<TaskListRowUi?>(null) }
    var deleting by remember { mutableStateOf<TaskListRowUi?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val fallbackName = stringResource(R.string.habi_name_fallback)
    val taskDoneLabel = stringResource(HabiVoice.taskDoneRes(state.personality), state.userName.ifBlank { fallbackName })

    // Same phrase and pattern Hoy already shows after marking a task done — this list had none.
    LaunchedEffect(taskDone) {
        if (taskDone != null) {
            try {
                snackbar.showSnackbar(taskDoneLabel, duration = SnackbarDuration.Short)
            } finally {
                viewModel.consumeTaskDone()
            }
        }
    }

    Scaffold(
        containerColor = Papel,
        snackbarHost = { SnackbarHost(snackbar) { DismissableBitoSnackbar(it) } },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxWidth().testTag("tasks-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TasksHeader(onBack) }
            if (state.isEmpty) {
                item { Text(stringResource(R.string.tasks_empty), style = MaterialTheme.typography.bodyLarge, color = TintaSuave) }
            } else {
                taskSection(
                    tasks = state.todayTasks,
                    titleRes = R.string.tasks_section_today,
                    today = state.today,
                    // Hoy is where "bring to today" lands you, not a place you leave it from.
                    showBringToToday = false,
                    onBringToToday = {},
                    onDone = viewModel::markDone,
                    onStart = onStartFocus,
                    onMenu = { menuFor = it },
                )
                taskSection(
                    tasks = state.weekTasks,
                    titleRes = R.string.tasks_section_week,
                    today = state.today,
                    showBringToToday = true,
                    onBringToToday = viewModel::bringToToday,
                    onDone = viewModel::markDone,
                    onStart = onStartFocus,
                    onMenu = { menuFor = it },
                )
                taskSection(
                    tasks = state.datedTasks,
                    titleRes = R.string.tasks_section_dated,
                    today = state.today,
                    showBringToToday = true,
                    onBringToToday = viewModel::bringToToday,
                    onDone = viewModel::markDone,
                    onStart = onStartFocus,
                    onMenu = { menuFor = it },
                )
                taskSection(
                    tasks = state.looseTasks,
                    titleRes = R.string.tasks_section_loose,
                    today = state.today,
                    showBringToToday = true,
                    onBringToToday = viewModel::bringToToday,
                    onDone = viewModel::markDone,
                    onStart = onStartFocus,
                    onMenu = { menuFor = it },
                )

                if (state.doneTasks.isNotEmpty()) {
                    item {
                        GhostPillButton(
                            text = stringResource(R.string.tasks_section_done),
                            onClick = { doneExpanded = !doneExpanded },
                            icon = if (doneExpanded) BitoIcons.ChevronDown else BitoIcons.ChevronRight,
                            modifier = Modifier.testTag("tasks-done-toggle"),
                        )
                    }
                    if (doneExpanded) {
                        items(state.doneTasks, key = { "done-${it.id}" }) { row ->
                            // Done rows carry no circle click, no bring, no start: reopening one is
                            // the deshacer of Hoy's own snackbar, never a tap from this list.
                            TaskRow(
                                row = row,
                                today = state.today,
                                showBringToToday = false,
                                onBringToToday = {},
                                onDone = {},
                                onStart = {},
                                onMenu = { menuFor = row },
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { row ->
        TaskRowMenuSheet(
            onEdit = {
                menuFor = null
                onEditTask(row.id)
            },
            onDelete = {
                menuFor = null
                deleting = row
            },
            onDismiss = { menuFor = null },
        )
    }

    deleting?.let { row ->
        DeleteTaskConfirmSheet(
            onConfirm = {
                viewModel.delete(row.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TasksHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            stringResource(R.string.tasks_screen_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}

/** One non-empty section: its header, then every row — a no-op for an empty list, no bare header. */
private fun LazyListScope.taskSection(
    tasks: List<TaskListRowUi>,
    titleRes: Int,
    today: LogicalDay,
    showBringToToday: Boolean,
    onBringToToday: (String) -> Unit,
    onDone: (String) -> Unit,
    onStart: (String) -> Unit,
    onMenu: (TaskListRowUi) -> Unit,
) {
    if (tasks.isEmpty()) return
    item { Text(stringResource(titleRes), style = MaterialTheme.typography.labelMedium, color = TintaSuave) }
    items(tasks, key = { it.id }) { row ->
        TaskRow(
            row = row,
            today = today,
            showBringToToday = showBringToToday,
            onBringToToday = { onBringToToday(row.id) },
            onDone = { onDone(row.id) },
            onStart = { onStart(row.id) },
            onMenu = { onMenu(row) },
        )
    }
}

/**
 * One task's row: a circle to mark it done (left), title/first step/due legend, "bring to today"
 * (only outside Hoy) and "start", and an ellipsis opening the edit/delete menu. Mirrors
 * [com.alvarotc.bito.ui.today.TodayScreen]'s own task row, plus the parts Today never needed.
 */
@Composable
private fun TaskRow(
    row: TaskListRowUi,
    today: LogicalDay,
    showBringToToday: Boolean,
    onBringToToday: () -> Unit,
    onDone: () -> Unit,
    onStart: () -> Unit,
    onMenu: () -> Unit,
) {
    val doneLabel = stringResource(R.string.task_done)
    BitoCard(modifier = Modifier.fillMaxWidth().testTag("task-${row.id}")) {
        Row(verticalAlignment = Alignment.Top) {
            val circleShape = Modifier.size(56.dp).clip(CircleShape).border(1.dp, Hoja, CircleShape)
            if (row.done) {
                // No onClick, no contentDescription: a done row is not reopened from this list —
                // the absence of the affordance IS the "no se desmarca" rule.
                Box(circleShape, contentAlignment = Alignment.Center) {
                    Icon(BitoIcons.Check, contentDescription = null, tint = Hoja)
                }
            } else {
                Box(
                    circleShape
                        .testTag("task-done-${row.id}")
                        .clickable(onClick = onDone)
                        .semantics { contentDescription = doneLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BitoIcons.Check, contentDescription = null, tint = Hoja)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Tinta)
                if (row.firstStep != null) {
                    Text(row.firstStep, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
                }
                // A suelta has no due day to hand taskDueLabel: task_no_due says so instead of
                // rendering nothing, same as Today's row would if it ever showed a loose one.
                val dueLabel =
                    if (row.dueKind == DueKind.NONE) {
                        stringResource(R.string.task_no_due)
                    } else {
                        taskDueLabel(row.dueKind, row.dueDay, today)
                    }
                if (dueLabel != null) {
                    val overdue = taskDueOverdue(row.dueKind, row.dueDay, today)
                    Text(dueLabel, style = MaterialTheme.typography.labelMedium, color = if (overdue) Peligro else TintaSuave)
                }
            }
            GhostIconButton(
                BitoIcons.Ellipsis,
                contentDescription = stringResource(R.string.task_row_menu_cd),
                onClick = onMenu,
                modifier = Modifier.testTag("task-menu-${row.id}"),
            )
        }
        if (!row.done) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showBringToToday) {
                    GhostPillButton(
                        text = stringResource(R.string.task_bring_today),
                        onClick = onBringToToday,
                        modifier = Modifier.testTag("task-bring-${row.id}"),
                    )
                }
                GhostPillButton(
                    text = stringResource(R.string.task_start),
                    onClick = onStart,
                    modifier = Modifier.testTag("task-start-${row.id}"),
                )
            }
        }
    }
}

/** The ellipsis menu: exactly two rows, editar and borrar — calcado del resto de sheets del repo. */
@Composable
private fun TaskRowMenuSheet(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("task-row-menu")) {
            TaskMenuRow(BitoIcons.Pencil, stringResource(R.string.task_edit), onEdit, Modifier.testTag("task-menu-edit"))
            Spacer(Modifier.height(4.dp))
            // Borrar en rojo, calcado de HabitFormScreen's own delete button (Peligro): the sheet
            // this row opens already confirms in red, so the row that leads there should read the
            // same way instead of looking identical to "editar" until you tap it.
            TaskMenuRow(
                BitoIcons.Trash,
                stringResource(R.string.task_delete),
                onDelete,
                Modifier.testTag("task-menu-delete"),
                tint = Peligro,
            )
        }
    }
}

@Composable
private fun TaskMenuRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tinta,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
