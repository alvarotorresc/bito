package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaTodoHechoTest {
    @Test
    fun `nothing pending and not sealed proposes closing`() {
        val habitos =
            listOf(
                habito("a", hechoHoy = true),
                habito("b", exigibleHoy = false),
                habito("c", tipo = TipoHabito.LIMITE, hechoHoy = true),
            )
        val c = ReglaTodoHecho.candidatas(hechos(habitos = habitos), Memoria(), ctx()).single()
        assertEquals(Situacion.TODO_HECHO, c.situacion)
        assertEquals(listOf(Respuesta.CERRAR_DIA, Respuesta.LUEGO), c.acciones)
        assertTrue(c.huecos.isEmpty())
        assertEquals(Destino.Cierre, c.destino)
    }

    @Test
    fun `a day with nothing required also waits for the close`() =
        assertEquals(1, ReglaTodoHecho.candidatas(hechos(habitos = emptyList()), Memoria(), ctx()).size)

    @Test
    fun `a pending habit keeps it away`() =
        assertTrue(
            ReglaTodoHecho.candidatas(hechos(habitos = listOf(habito("a", hechoHoy = true), habito("b"))), Memoria(), ctx()).isEmpty(),
        )

    @Test
    fun `a relapse or a passed limit do not keep it away`() {
        val recaida = habito("d", tipo = TipoHabito.DEJAR).copy(falladoHoy = true)
        val pasado = habito("l", tipo = TipoHabito.LIMITE).copy(falladoHoy = true)
        val h = hechos(habitos = listOf(habito("a", hechoHoy = true), recaida, pasado))
        assertEquals(Situacion.TODO_HECHO, ReglaTodoHecho.candidatas(h, Memoria(), ctx()).single().situacion)
    }

    @Test
    fun `a sealed day does not come out`() =
        assertTrue(
            ReglaTodoHecho.candidatas(hechos(habitos = listOf(habito(hechoHoy = true)), diaSellado = true), Memoria(), ctx()).isEmpty(),
        )
}
