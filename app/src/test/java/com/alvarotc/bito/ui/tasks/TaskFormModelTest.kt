package com.alvarotc.bito.ui.tasks

import com.alvarotc.bito.data.db.TaskEntity
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val TODAY = 20_000

class TaskFormModelTest {
    @Test
    fun `an empty or blank title cannot be saved`() {
        assertFalse(TaskFormState(title = "").canSave)
        assertFalse(TaskFormState(title = "   ").canSave)
    }

    @Test
    fun `no deadline resolves to null`() {
        val form = TaskFormState(title = "Llamar al banco", dueKind = DueKind.NONE, dueDay = 12_345)
        assertNull(form.resolvedDueDay(TODAY))
    }

    @Test
    fun `this week resolves to the sunday of the current week, whatever was stored`() {
        val staleSunday = TODAY - 30 // a Sunday from some old week, long gone
        val form = TaskFormState(title = "Ordenar el trastero", dueKind = DueKind.WEEK, dueDay = staleSunday)

        assertEquals(Tasks.weekDueOf(TODAY), form.resolvedDueDay(TODAY))
    }

    @Test
    fun `a date resolves to the chosen day`() {
        val chosen = TODAY + 5
        val form = TaskFormState(title = "Renovar el DNI", dueKind = DueKind.DATE, dueDay = chosen)

        assertEquals(chosen, form.resolvedDueDay(TODAY))
    }

    @Test
    fun `an entity loads back into the form, blanks included`() {
        val entity =
            TaskEntity(
                id = "t1",
                title = "Pedir cita medico",
                firstStep = null,
                dueKind = DueKind.WEEK,
                dueDay = TODAY + 3,
                status = TaskStatus.OPEN,
                createdAtMillis = 1_000L,
                createdOnDay = TODAY,
                doneAtMillis = null,
                doneOnDay = null,
            )

        val form = entity.toFormState()

        assertEquals("t1", form.editingId)
        assertEquals("Pedir cita medico", form.title)
        assertEquals("", form.firstStep)
        assertEquals(DueKind.WEEK, form.dueKind)
        assertEquals(TODAY + 3, form.dueDay)
    }

    // --- datePickerSeed --------------------------------------------------------------------

    @Test
    fun `the date picker seed is the already chosen day, not today`() {
        val chosen = TODAY + 12
        val form = TaskFormState(title = "Renovar el DNI", dueKind = DueKind.DATE, dueDay = chosen)

        assertEquals(chosen, form.datePickerSeed(TODAY))
    }

    @Test
    fun `the date picker seed falls back to today when nothing is chosen yet`() {
        val form = TaskFormState(title = "Renovar el DNI")

        assertEquals(TODAY, form.datePickerSeed(TODAY))
    }
}
