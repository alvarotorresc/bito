package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.datedTask
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entryOn
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.pauseOn
import com.alvarotc.bito.domain.task
import com.alvarotc.bito.domain.taskEvent
import com.alvarotc.bito.domain.weekTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoGordoTest {
    private fun tareaRef(id: String) = RefGordo(TipoRefGordo.TAREA, id)

    private fun habitoRef(id: String) = RefGordo(TipoRefGordo.HABITO, id)

    @Test
    fun `quitting entirely and limits are never candidates`() {
        val state = domainState(habits = listOf(RealHabits.noSmoking, RealHabits.socialMedia, RealHabits.meditate))
        assertEquals(listOf(habitoRef(RealHabits.meditate.id)), LoGordo.candidatos(state, TODAY).map { it.ref })
    }

    @Test
    fun `habits already done today or paused are not candidates`() {
        val state =
            domainState(
                habits = listOf(RealHabits.meditate, RealHabits.makeBed, RealHabits.floss),
                entries = listOf(entryOn(RealHabits.meditate, TODAY)),
                pauses = listOf(pauseOn(RealHabits.floss, TODAY - 2)),
            )
        assertEquals(listOf(habitoRef(RealHabits.makeBed.id)), LoGordo.candidatos(state, TODAY).map { it.ref })
    }

    @Test
    fun `a weekly habit already met this week is not a candidate`() {
        val state =
            domainState(
                habits = listOf(RealHabits.callFamily),
                entries = listOf(entryOn(RealHabits.callFamily, TODAY - 1)),
            )
        assertTrue(LoGordo.candidatos(state, TODAY).isEmpty())
    }

    @Test
    fun `tasks come first, then habits in the user's order, with the habit name`() {
        val state =
            domainState(
                habits = listOf(RealHabits.stretching, RealHabits.meditate),
                tasks = listOf(datedTask("t-hoy", TODAY)),
            )
        val c = LoGordo.candidatos(state, TODAY)
        assertEquals(listOf(tareaRef("t-hoy"), habitoRef("stretching"), habitoRef("meditate")), c.map { it.ref })
        assertEquals("Estiramientos", c[1].nombre)
        assertEquals(SubtituloGordo.Habito, c[1].subtitulo)
    }

    @Test
    fun `a task overdue two days comes out as overdue by two`() {
        val state = domainState(tasks = listOf(datedTask("t1", TODAY - 2).copy(title = "El informe")))
        val c = LoGordo.candidatos(state, TODAY).single()
        assertEquals("El informe", c.nombre)
        assertEquals(SubtituloGordo.TareaVencida(2), c.subtitulo)
    }

    @Test
    fun `each task slot gives its subtitle`() {
        val state =
            domainState(
                tasks = listOf(datedTask("hoy", TODAY), weekTask("semana", TODAY), datedTask("pronto", TODAY + 2)),
            )
        val porId = LoGordo.candidatos(state, TODAY).associate { it.ref.id to it.subtitulo }
        assertEquals(SubtituloGordo.TareaHoy, porId["hoy"])
        assertEquals(SubtituloGordo.TareaSemana, porId["semana"])
        assertEquals(SubtituloGordo.Tarea, porId["pronto"])
    }

    @Test
    fun `the suggestion prefers yesterday's unfinished big thing`() {
        val state =
            domainState(
                tasks = listOf(datedTask("vencida", TODAY - 5), datedTask("hoy", TODAY), task(id = "ayer", dueDay = null)),
                taskEvents = listOf(taskEvent("ayer", TaskEventKind.BROUGHT, TODAY)),
            )
        val c = LoGordo.candidatos(state, TODAY)
        assertEquals(tareaRef("ayer"), LoGordo.sugerencia(c, listOf(tareaRef("ayer")), state, TODAY))
    }

    @Test
    fun `yesterday's big thing that is no longer a candidate is skipped`() {
        val state = domainState(tasks = listOf(datedTask("hoy", TODAY), task(id = "hecha", status = TaskStatus.DONE)))
        val c = LoGordo.candidatos(state, TODAY)
        assertEquals(tareaRef("hoy"), LoGordo.sugerencia(c, listOf(tareaRef("hecha")), state, TODAY))
    }

    @Test
    fun `then the oldest overdue task`() {
        val state =
            domainState(
                tasks = listOf(datedTask("hoy", TODAY), datedTask("vencida-1", TODAY - 1), datedTask("vencida-4", TODAY - 4)),
            )
        val c = LoGordo.candidatos(state, TODAY)
        assertEquals(tareaRef("vencida-4"), LoGordo.sugerencia(c, emptyList(), state, TODAY))
    }

    @Test
    fun `then the task due today`() {
        val state = domainState(habits = listOf(RealHabits.meditate), tasks = listOf(weekTask("semana", TODAY), datedTask("hoy", TODAY)))
        val c = LoGordo.candidatos(state, TODAY)
        assertEquals(tareaRef("hoy"), LoGordo.sugerencia(c, emptyList(), state, TODAY))
    }

    @Test
    fun `without tasks there is no suggestion`() {
        val state = domainState(habits = listOf(RealHabits.meditate))
        val c = LoGordo.candidatos(state, TODAY)
        assertNull(LoGordo.sugerencia(c, emptyList(), state, TODAY))
    }

    @Test
    fun `a done task is done`() {
        val state = domainState(tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY), task(id = "t2")))
        assertTrue(LoGordo.hecho(tareaRef("t1"), state, TODAY))
        assertFalse(LoGordo.hecho(tareaRef("t2"), state, TODAY))
        assertFalse(LoGordo.hecho(tareaRef("no-existe"), state, TODAY))
    }

    @Test
    fun `a habit met today is done`() {
        val state =
            domainState(
                habits = listOf(RealHabits.meditate, RealHabits.makeBed),
                entries = listOf(entryOn(RealHabits.meditate, TODAY)),
            )
        assertTrue(LoGordo.hecho(habitoRef("meditate"), state, TODAY))
        assertFalse(LoGordo.hecho(habitoRef("make-bed"), state, TODAY))
    }

    @Test
    fun `a habit met yesterday is not done today`() {
        val state = domainState(habits = listOf(RealHabits.meditate), entries = listOf(entryOn(RealHabits.meditate, TODAY - 1)))
        assertFalse(LoGordo.hecho(habitoRef("meditate"), state, TODAY))
    }

    @Test
    fun `the big thing holds three at most`() = assertEquals(3, LoGordo.MAXIMO)
}
