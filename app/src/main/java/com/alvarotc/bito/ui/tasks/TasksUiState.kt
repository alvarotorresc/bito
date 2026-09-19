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
 * Snapshot the full tasks list screen renders (spec §8.4): five sections that never overlap —
 * a task touching Hoy shows up there and nowhere else.
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
 * Hoy is [Tasks.todayTasks] minus every task [isOpenThisWeek] — exactly the set that goes to
 * [weekTasks] instead, postponed today or not: a WEEK task due exactly today (its Sunday) moves
 * out of Hoy and shows only in Esta semana; an overdue one ([Task.dueDay] `< today`) still shows
 * only in Hoy, as `OVERDUE`. `inToday` is computed from that trimmed Hoy list and reused by Con
 * plazo and Sin plazo, which keep excluding it — Hoy wins there, so no task appears twice.
 */
fun buildTasksUiState(
    state: DomainState,
    today: LogicalDay,
): TasksUiState {
    val todayTasks =
        Tasks.todayTasks(state, today)
            .map { it.task }
            .filterNot { isOpenThisWeek(it, today) }
    val inToday = todayTasks.map { it.id }.toSet()
    val open = state.tasks.filter { it.status == TaskStatus.OPEN }

    val weekTasks =
        open.filter { isOpenThisWeek(it, today) }
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

/** Open, WEEK-kind, not past its Sunday yet — the set Esta semana groups and Hoy gives up to it. */
private fun isOpenThisWeek(
    task: Task,
    today: LogicalDay,
): Boolean = task.dueKind == DueKind.WEEK && (task.dueDay ?: Int.MIN_VALUE) >= today

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
