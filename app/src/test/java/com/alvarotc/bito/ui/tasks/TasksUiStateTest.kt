package com.alvarotc.bito.ui.tasks

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.datedTask
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.task
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
        val week = weekTask("week", TODAY)
        val loose = task(id = "loose", dueKind = DueKind.NONE, createdOnDay = TODAY - 30)
        val state = domainState(tasks = listOf(overdue, dueToday, week, loose))

        val ui = buildTasksUiState(state, TODAY)

        assertEquals(setOf("overdue", "due-today", "week", "loose"), ui.todayTasks.map { it.id }.toSet())
        assertTrue(ui.weekTasks.isEmpty())
        assertTrue(ui.datedTasks.isEmpty())
        assertTrue(ui.looseTasks.isEmpty())
        assertTrue(ui.todayTasks.all { it.inToday })
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
