package com.alvarotc.bito.ui.tasks

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.datedTask
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.task
import com.alvarotc.bito.domain.taskEvent
import com.alvarotc.bito.domain.weekTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TasksUiStateTest {
    @Test
    fun `sections do not overlap and today wins`() {
        val overdue = datedTask("overdue", TODAY - 2)
        val dueToday = datedTask("due-today", TODAY)
        val loose = task(id = "loose", dueKind = DueKind.NONE, createdOnDay = TODAY - 30)
        val state = domainState(tasks = listOf(overdue, dueToday, loose))

        val ui = buildTasksUiState(state, TODAY)

        assertEquals(setOf("overdue", "due-today", "loose"), ui.todayTasks.map { it.id }.toSet())
        assertTrue(ui.weekTasks.isEmpty())
        assertTrue(ui.datedTasks.isEmpty())
        assertTrue(ui.looseTasks.isEmpty())
        assertTrue(ui.todayTasks.all { it.inToday })
    }

    @Test
    fun `an open week task sits in esta semana and not in hoy, postponed or not — an overdue one stays in hoy`() {
        // weekTask(id, TODAY) dues on this week's Sunday (TODAY+2, since TODAY is a Friday): open, not overdue.
        val open = weekTask("open-week", TODAY)
        val postponedToday = weekTask("postponed-week", TODAY)
        // Its Sunday is today: dueDay == today is not yet overdue, so it belongs to Esta semana only —
        // not Hoy, even though slotOf alone would have called it DUE_TODAY.
        val dueToday = task(id = "due-today-week", dueKind = DueKind.WEEK, dueDay = TODAY, createdOnDay = TODAY - 2)
        // A WEEK task whose Sunday already passed: still WEEK-kind, but its dueDay < today makes it OVERDUE.
        val overdue = task(id = "overdue-week", dueKind = DueKind.WEEK, dueDay = TODAY - 5, createdOnDay = TODAY - 12)
        // Overdue AND postponed today: Tasks.todayTasks drops it entirely (postponedOn), so isOpenThisWeek's
        // dueDay >= today alone would leave it in no section. "not in Hoy" still gives it a home.
        val overduePostponed =
            task(id = "overdue-postponed-week", dueKind = DueKind.WEEK, dueDay = TODAY - 5, createdOnDay = TODAY - 12)
        val state =
            domainState(
                tasks = listOf(open, postponedToday, dueToday, overdue, overduePostponed),
                taskEvents =
                    listOf(
                        taskEvent("postponed-week", TaskEventKind.POSTPONED, TODAY),
                        taskEvent("overdue-postponed-week", TaskEventKind.POSTPONED, TODAY),
                    ),
            )

        val ui = buildTasksUiState(state, TODAY)

        val weekIds = setOf("open-week", "postponed-week", "due-today-week", "overdue-postponed-week")
        assertEquals(weekIds, ui.weekTasks.map { it.id }.toSet())
        assertTrue(ui.todayTasks.none { it.id in weekIds })

        assertEquals(listOf("overdue-week"), ui.todayTasks.map { it.id })
        assertTrue(ui.weekTasks.none { it.id == "overdue-week" })

        // The overdue-and-postponed-today task shows up exactly once, in Esta semana.
        val allSections = ui.todayTasks + ui.weekTasks + ui.datedTasks + ui.looseTasks + ui.doneTasks
        assertEquals(1, allSections.count { it.id == "overdue-postponed-week" })
    }

    @Test
    fun `dated tasks are ordered by deadline, ascending`() {
        // due - today > DUE_SOON_DAYS (3) so none of these land in Hoy.
        val late = datedTask("late", TODAY + 20)
        val soon = datedTask("soon", TODAY + 4)
        val mid = datedTask("mid", TODAY + 10)
        val state = domainState(tasks = listOf(late, soon, mid))

        val ui = buildTasksUiState(state, TODAY)

        assertEquals(listOf("soon", "mid", "late"), ui.datedTasks.map { it.id })
        assertTrue(ui.datedTasks.none { it.inToday })
    }

    @Test
    fun `loose tasks put the oldest first — the one Habi will bring`() {
        val brought = task(id = "brought", createdOnDay = TODAY - 20, createdAtMillis = 50L)
        val oldest = task(id = "oldest", createdOnDay = TODAY - 10, createdAtMillis = 100L)
        val middle = task(id = "middle", createdOnDay = TODAY - 5, createdAtMillis = 200L)
        val newest = task(id = "newest", createdOnDay = TODAY - 1, createdAtMillis = 300L)
        val state = domainState(tasks = listOf(newest, oldest, middle, brought))

        val ui = buildTasksUiState(state, TODAY)

        // Habi already brought the globally oldest into Hoy; the queue left behind stays
        // oldest-first, previewing who is next.
        assertEquals("brought", Tasks.looseOfTheDay(state, TODAY)?.id)
        assertEquals(listOf("brought"), ui.todayTasks.map { it.id })
        assertEquals(listOf("oldest", "middle", "newest"), ui.looseTasks.map { it.id })
    }

    @Test
    fun `done tasks come last, most recent first`() {
        val oldDone = task(id = "old-done", status = TaskStatus.DONE, doneOnDay = TODAY - 10)
        val recentDone = task(id = "recent-done", status = TaskStatus.DONE, doneOnDay = TODAY - 1)
        val midDone = task(id = "mid-done", status = TaskStatus.DONE, doneOnDay = TODAY - 5)
        val state = domainState(tasks = listOf(oldDone, recentDone, midDone))

        val ui = buildTasksUiState(state, TODAY)

        assertEquals(listOf("recent-done", "mid-done", "old-done"), ui.doneTasks.map { it.id })
        assertTrue(ui.doneTasks.all { it.done })
        assertTrue(ui.todayTasks.isEmpty())
    }

    @Test
    fun `an empty state reports itself empty`() {
        val ui = buildTasksUiState(domainState(), TODAY)

        assertTrue(ui.isEmpty)
        assertFalse(ui.loading)

        val nonEmpty = buildTasksUiState(domainState(tasks = listOf(task(id = "t1"))), TODAY)
        assertFalse(nonEmpty.isEmpty)
    }
}
