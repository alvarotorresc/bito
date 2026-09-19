package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.TaskEventKind
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
}
