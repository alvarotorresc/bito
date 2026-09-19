package com.alvarotc.bito.ui.tasks

import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Task
import com.alvarotc.bito.domain.model.TaskStatus

/** One task's row in the full list screen, ready for the UI without further domain lookups. */
data class TaskListRowUi(
    val id: String,
    val title: String,
    val firstStep: String?,
    val dueKind: DueKind,
    val dueDay: LogicalDay?,
    val done: Boolean,
    val inToday: Boolean,
)

/**
 * Snapshot the full tasks list screen renders (spec §8.4): sections grouped by due-kind. Hoy,
 * Con plazo, Sin plazo and Hechas never overlap. Esta semana is the one exception (capataz
 * ruling, 2026-09-19): it groups every open WEEK task of the current week regardless of whether
 * it also touches Hoy, so a WEEK task due today can show in both.
 */
data class TasksUiState(
    val today: LogicalDay = 0,
    val todayTasks: List<TaskListRowUi> = emptyList(),
    val weekTasks: List<TaskListRowUi> = emptyList(),
    val datedTasks: List<TaskListRowUi> = emptyList(),
    val looseTasks: List<TaskListRowUi> = emptyList(),
    val doneTasks: List<TaskListRowUi> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean
        get() = todayTasks.isEmpty() && weekTasks.isEmpty() && datedTasks.isEmpty() && looseTasks.isEmpty() && doneTasks.isEmpty()
}

/**
 * Derives the full tasks list from [state] as seen on [today]. Pure — no side effects, no
 * storage, no clock reads — same discipline as [com.alvarotc.bito.ui.today.buildTodayUiState].
 *
 * Hoy is [Tasks.todayTasks] minus its `THIS_WEEK` rows: those already have a home in [weekTasks],
 * which groups every open WEEK task whose deadline has not passed ([Task.dueDay] `>= today`) —
 * postponed today or not, whether or not it also happens to sit in Hoy (an overdue WEEK task
 * stays OVERDUE, in Hoy only; one due exactly today stays DUE_TODAY, in both). `inToday` is
 * computed from that trimmed Hoy list and reused only by Con plazo and Sin plazo, which keep
 * excluding it — Esta semana does not (capataz ruling, 2026-09-19).
 */
fun buildTasksUiState(
    state: DomainState,
    today: LogicalDay,
): TasksUiState {
    val todayTasks =
        Tasks.todayTasks(state, today)
            .filter { it.slot != Tasks.TodaySlot.THIS_WEEK }
            .map { it.task }
    val inToday = todayTasks.map { it.id }.toSet()
    val open = state.tasks.filter { it.status == TaskStatus.OPEN }

    val weekTasks =
        open.filter { it.dueKind == DueKind.WEEK && (it.dueDay ?: Int.MIN_VALUE) >= today }
            .sortedWith(compareBy({ it.dueDay ?: Int.MAX_VALUE }, { it.createdAtMillis }, { it.id }))

    val datedTasks =
        open.filter { it.dueKind == DueKind.DATE && it.id !in inToday }
            .sortedWith(compareBy({ it.dueDay ?: Int.MAX_VALUE }, { it.createdAtMillis }, { it.id }))

    val looseTasks =
        open.filter { it.dueKind == DueKind.NONE && it.id !in inToday }
            .sortedWith(compareBy({ sinkKeyOf(state, it.id, today) }, { it.createdOnDay }, { it.createdAtMillis }, { it.id }))

    val doneTasks =
        state.tasks
            .filter { it.status == TaskStatus.DONE }
            .sortedWith(
                compareByDescending<Task> { it.doneOnDay ?: Int.MIN_VALUE }
                    .thenByDescending { it.createdAtMillis }
                    .thenByDescending { it.id },
            )

    return TasksUiState(
        today = today,
        todayTasks = todayTasks.map { it.toRowUi(inToday) },
        weekTasks = weekTasks.map { it.toRowUi(inToday) },
        datedTasks = datedTasks.map { it.toRowUi(inToday) },
        looseTasks = looseTasks.map { it.toRowUi(inToday) },
        doneTasks = doneTasks.map { it.toRowUi(inToday) },
        loading = false,
    )
}

/**
 * Mirrors [Tasks.looseOfTheDay]'s private sink key through the public [Tasks.lastPostponedDay]:
 * a task postponed before today sinks to that day; postponed today (or never) stays at the
 * front. Today's champion is already excluded (it lives in [TasksUiState.todayTasks]), so this
 * is the same priority order Habi uses — the head of [TasksUiState.looseTasks] previews who
 * Habi brings next.
 */
private fun sinkKeyOf(
    state: DomainState,
    taskId: String,
    today: LogicalDay,
): LogicalDay {
    val lastPostponed = Tasks.lastPostponedDay(state, taskId) ?: return Int.MIN_VALUE
    return if (lastPostponed < today) lastPostponed else Int.MIN_VALUE
}

private fun Task.toRowUi(inToday: Set<String>): TaskListRowUi =
    TaskListRowUi(
        id = id,
        title = title,
        firstStep = firstStep,
        dueKind = dueKind,
        dueDay = dueDay,
        done = status == TaskStatus.DONE,
        inToday = id in inToday,
    )
