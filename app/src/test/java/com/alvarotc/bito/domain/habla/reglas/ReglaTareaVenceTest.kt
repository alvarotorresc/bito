package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.tarea
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaTareaVenceTest {
    private fun sale(
        vararg ts: com.alvarotc.bito.domain.habla.TareaHoy,
        gordo: List<RefGordo> = emptyList(),
    ) = ReglaTareaVence.candidatas(hechos(tareas = ts.toList(), gordo = gordo), Memoria(), ctx())

    @Test
    fun `a task due today comes out with Now and Not today`() {
        val c = sale(tarea("t1", venceHoy = true)).single()
        assertEquals(Situacion.TAREA_VENCE, c.situacion)
        assertEquals("t1", c.ref)
        assertEquals(ValorHueco.Texto("El informe"), c.huecos[Hueco.TAREA])
        assertEquals(listOf(Respuesta.AHORA, Respuesta.HOY_NO), c.acciones)
        assertEquals(Destino.Tarea("t1"), c.destino)
        assertEquals(0, c.peso)
    }

    @Test
    fun `an overdue task weighs its days late`() {
        val cs = sale(tarea("a", vencidaDias = 2), tarea("b", vencidaDias = 5))
        assertEquals(5, cs.single { it.ref == "b" }.peso)
        assertEquals(2, cs.single { it.ref == "a" }.peso)
    }

    @Test
    fun `a task that neither expires nor is late, or is done, does not come out`() {
        assertTrue(sale(tarea("a")).isEmpty())
        assertTrue(sale(tarea("b", venceHoy = true, hecha = true)).isEmpty())
    }

    @Test
    fun `the big thing is not proposed again`() {
        val gordo = listOf(RefGordo(TipoRefGordo.TAREA, "t1"))
        val cs = sale(tarea("t1", venceHoy = true), tarea("t2", vencidaDias = 1), gordo = gordo)
        assertEquals(listOf("t2"), cs.map { it.ref })
    }

    @Test
    fun `a habit in the big thing with the same id as a task does not hide it`() {
        val gordo = listOf(RefGordo(TipoRefGordo.HABITO, "t1"))
        assertEquals(1, sale(tarea("t1", venceHoy = true), gordo = gordo).size)
    }
}
