package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.tarea
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaElegirGordoTest {
    private val informe = tarea("t1", titulo = "El informe", venceHoy = true)
    private val sugerencia = RefGordo(TipoRefGordo.TAREA, "t1")

    /** Dos candidatos: la tarea y el hábito; [candidatosGordo] cuenta lo mismo que las listas. */
    private fun dos(
        animoAyer: Int? = null,
        gordo: List<RefGordo> = emptyList(),
        sugerenciaGordo: RefGordo? = sugerencia,
    ) = hechos(
        habitos = listOf(habito("h1")),
        tareas = listOf(informe),
        candidatosGordo = 2,
        sugerenciaGordo = sugerenciaGordo,
        animoAyer = animoAyer,
        gordo = gordo,
    )

    @Test
    fun `one candidate is not worth choosing`() =
        assertTrue(ReglaElegirGordo.candidatas(hechos(tareas = listOf(informe), candidatosGordo = 1), Memoria(), ctx()).isEmpty())

    @Test
    fun `two candidates in the morning propose choosing, with the suggestion`() {
        val c = ReglaElegirGordo.candidatas(dos(), Memoria(), ctx(ahoraMin = 12 * 60 + 59)).single()
        assertEquals(Situacion.ELEGIR_GORDO, c.situacion)
        assertEquals(listOf(Respuesta.ELEGIR_GORDO, Respuesta.HOY_NO), c.acciones)
        assertEquals(ValorHueco.Texto("El informe"), c.huecos[Hueco.TAREA])
        assertEquals(Destino.LoGordo, c.destino)
    }

    @Test
    fun `without a suggestion there is no task`() {
        val c = ReglaElegirGordo.candidatas(dos(sugerenciaGordo = null), Memoria(), ctx()).single()
        assertTrue(c.huecos.isEmpty())
    }

    @Test
    fun `from one in the afternoon it is too late`() =
        assertTrue(ReglaElegirGordo.candidatas(dos(), Memoria(), ctx(ahoraMin = 13 * 60)).isEmpty())

    @Test
    fun `not on a hard day`() = assertTrue(ReglaElegirGordo.candidatas(dos(animoAyer = 2), Memoria(), ctx()).isEmpty())

    @Test
    fun `not when it is already chosen`() =
        assertTrue(ReglaElegirGordo.candidatas(dos(gordo = listOf(sugerencia)), Memoria(), ctx()).isEmpty())
}
