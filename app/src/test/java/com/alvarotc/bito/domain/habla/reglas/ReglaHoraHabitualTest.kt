package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaHoraHabitualTest {
    private fun sale(
        vararg hs: HabitoHoy,
        ahoraMin: Int = 10 * 60,
    ) = ReglaHoraHabitual.candidatas(hechos(habitos = hs.toList()), Memoria(), ctx(ahoraMin = ahoraMin))

    @Test
    fun `sixty minutes ahead comes out and sixty-one does not`() {
        assertEquals(1, sale(habito(horaHabitual = 11 * 60)).size)
        assertTrue(sale(habito(horaHabitual = 11 * 60 + 1)).isEmpty())
    }

    @Test
    fun `right now comes out and a minute ago does not`() {
        assertEquals(1, sale(habito(horaHabitual = 10 * 60)).size)
        assertTrue(sale(habito(horaHabitual = 10 * 60 - 1)).isEmpty())
    }

    @Test
    fun `it brings the habit and its usual hour with Now and Later`() {
        val c = sale(habito(horaHabitual = 10 * 60 + 30)).single()
        assertEquals(Situacion.HORA_HABITUAL, c.situacion)
        assertEquals("h1", c.ref)
        assertEquals(ValorHueco.Hora(10 * 60 + 30), c.huecos[Hueco.HORA])
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertEquals(listOf(Respuesta.AHORA, Respuesta.LUEGO), c.acciones)
        assertEquals(Destino.Habito("h1"), c.destino)
    }

    @Test
    fun `a done habit or one without usual hour does not come out`() {
        assertTrue(sale(habito(horaHabitual = 10 * 60 + 30, hechoHoy = true)).isEmpty())
        assertTrue(sale(habito(horaHabitual = null)).isEmpty())
    }

    @Test
    fun `the nearest hour weighs more`() {
        val cs = sale(habito("a", horaHabitual = 11 * 60), habito("b", horaHabitual = 10 * 60 + 10))
        assertTrue(cs.single { it.ref == "b" }.peso > cs.single { it.ref == "a" }.peso)
    }
}
