package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas puras de las tareas (spec §3 y §4). Sin reloj y sin Android: todo entra
 * por parametro, igual que en SealingTest.
 */
class TasksTest {
    @Test
    fun `weekDueOf returns the sunday of the natural week for every day of it`() {
        for (day in THIS_MONDAY..THIS_SUNDAY) {
            assertEquals("day $day", THIS_SUNDAY, Tasks.weekDueOf(day))
        }
    }

    @Test
    fun `weekDueOf jumps to the next sunday as soon as the week is crossed`() {
        assertEquals(THIS_SUNDAY + 7, Tasks.weekDueOf(THIS_SUNDAY + 1))
    }

    @Test
    fun `postponedOn only sees a POSTPONED event of that task on that day`() {
        val state =
            domainState(
                tasks = listOf(task(id = "t1")),
                taskEvents =
                    listOf(
                        taskEvent(taskId = "t1", kind = TaskEventKind.POSTPONED, day = TODAY),
                        taskEvent(taskId = "t2", kind = TaskEventKind.POSTPONED, day = TODAY),
                        taskEvent(taskId = "t1", kind = TaskEventKind.BROUGHT, day = TODAY),
                    ),
            )

        assertTrue(Tasks.postponedOn(state, "t1", TODAY))
        assertFalse(Tasks.postponedOn(state, "t1", TODAY - 1))
        assertFalse(Tasks.postponedOn(state, "t3", TODAY))
    }

    @Test
    fun `broughtOn only sees a BROUGHT event of that task on that day`() {
        val state =
            domainState(
                tasks = listOf(task(id = "t1")),
                taskEvents =
                    listOf(
                        taskEvent(taskId = "t1", kind = TaskEventKind.BROUGHT, day = TODAY),
                        taskEvent(taskId = "t1", kind = TaskEventKind.POSTPONED, day = TODAY),
                    ),
            )

        assertTrue(Tasks.broughtOn(state, "t1", TODAY))
        assertFalse(Tasks.broughtOn(state, "t1", TODAY - 1))
    }

    @Test
    fun `lastPostponedDay takes the most recent one and ignores other kinds`() {
        val state =
            domainState(
                tasks = listOf(task(id = "t1"), task(id = "t2")),
                taskEvents =
                    listOf(
                        taskEvent(taskId = "t1", kind = TaskEventKind.POSTPONED, day = TODAY - 5),
                        taskEvent(taskId = "t1", kind = TaskEventKind.POSTPONED, day = TODAY - 2),
                        taskEvent(taskId = "t1", kind = TaskEventKind.ATTEMPT, day = TODAY),
                    ),
            )

        assertEquals(TODAY - 2, Tasks.lastPostponedDay(state, "t1"))
        assertNull(Tasks.lastPostponedDay(state, "t2"))
    }

    @Test
    fun `an overdue, a due today and a due soon task all reach today`() {
        val state =
            domainState(
                tasks =
                    listOf(
                        datedTask("overdue", TODAY - 4),
                        datedTask("today", TODAY),
                        datedTask("soon", TODAY + Tasks.DUE_SOON_DAYS),
                    ),
            )

        val slots = Tasks.todayTasks(state, TODAY).associate { it.task.id to it.slot }

        assertEquals(
            mapOf(
                "overdue" to Tasks.TodaySlot.OVERDUE,
                "today" to Tasks.TodaySlot.DUE_TODAY,
                "soon" to Tasks.TodaySlot.DUE_SOON,
            ),
            slots,
        )
    }

    @Test
    fun `a task further out than the margin stays away until it is brought by hand`() {
        val far = datedTask("far", TODAY + Tasks.DUE_SOON_DAYS + 1)
        val without = domainState(tasks = listOf(far))
        assertTrue(Tasks.todayTasks(without, TODAY).isEmpty())

        val brought =
            domainState(
                tasks = listOf(far),
                taskEvents = listOf(taskEvent(taskId = "far", kind = TaskEventKind.BROUGHT, day = TODAY)),
            )
        assertEquals(Tasks.TodaySlot.BROUGHT, Tasks.todayTasks(brought, TODAY).single().slot)
    }

    @Test
    fun `postponing today takes it out of today and tomorrow brings it back`() {
        val state =
            domainState(
                tasks = listOf(datedTask("overdue", TODAY - 4)),
                taskEvents = listOf(taskEvent(taskId = "overdue", kind = TaskEventKind.POSTPONED, day = TODAY)),
            )

        assertTrue(Tasks.todayTasks(state, TODAY).isEmpty())
        assertEquals("overdue", Tasks.todayTasks(state, TODAY + 1).single().task.id)
    }

    @Test
    fun `a done task never reaches today`() {
        val state =
            domainState(
                tasks =
                    listOf(
                        task(
                            id = "done",
                            dueKind = DueKind.DATE,
                            dueDay = TODAY,
                            status = TaskStatus.DONE,
                            doneOnDay = TODAY,
                        ),
                    ),
            )

        assertTrue(Tasks.todayTasks(state, TODAY).isEmpty())
    }

    @Test
    fun `a week task shows every day of its week, closes on sunday and is overdue on monday`() {
        val state = domainState(tasks = listOf(weekTask("week", THIS_MONDAY)))

        for (day in THIS_MONDAY..THIS_SUNDAY - 1) {
            assertEquals("day $day", Tasks.TodaySlot.THIS_WEEK, Tasks.todayTasks(state, day).single().slot)
        }
        assertEquals(Tasks.TodaySlot.DUE_TODAY, Tasks.todayTasks(state, THIS_SUNDAY).single().slot)
        assertEquals(Tasks.TodaySlot.OVERDUE, Tasks.todayTasks(state, THIS_SUNDAY + 1).single().slot)
    }

    @Test
    fun `a task that qualifies twice appears once, under the first slot in order`() {
        val state =
            domainState(
                tasks = listOf(datedTask("both", TODAY - 1)),
                taskEvents = listOf(taskEvent(taskId = "both", kind = TaskEventKind.BROUGHT, day = TODAY)),
            )

        val todays = Tasks.todayTasks(state, TODAY)

        assertEquals(1, todays.size)
        assertEquals(Tasks.TodaySlot.OVERDUE, todays.single().slot)
    }

    @Test
    fun `the order is overdue, today, this week, soon, brought — and deterministic inside each group`() {
        val state =
            domainState(
                tasks =
                    listOf(
                        datedTask("soon", TODAY + 2),
                        datedTask("brought", TODAY + 9),
                        weekTask("week", TODAY),
                        datedTask("today", TODAY),
                        datedTask("overdue-old", TODAY - 5),
                        datedTask("overdue-new", TODAY - 1),
                    ),
                taskEvents = listOf(taskEvent(taskId = "brought", kind = TaskEventKind.BROUGHT, day = TODAY)),
            )

        assertEquals(
            listOf("overdue-old", "overdue-new", "today", "week", "soon", "brought"),
            Tasks.todayTasks(state, TODAY).map { it.task.id },
        )
    }

    @Test
    fun `inside a group the oldest creation wins, and the id breaks a perfect tie`() {
        val state =
            domainState(
                tasks =
                    listOf(
                        datedTask("b", TODAY, createdAtMillis = 500L),
                        datedTask("a", TODAY, createdAtMillis = 500L),
                        datedTask("c", TODAY, createdAtMillis = 100L),
                    ),
            )

        assertEquals(listOf("c", "a", "b"), Tasks.todayTasks(state, TODAY).map { it.task.id })
    }
}
