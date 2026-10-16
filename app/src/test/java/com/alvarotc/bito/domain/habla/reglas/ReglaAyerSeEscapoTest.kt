package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaAyerSeEscapoTest {
    private fun sale(
        vararg hs: HabitoHoy,
        ahoraMin: Int = 10 * 60,
    ) = ReglaAyerSeEscapo.candidatas(hechos(habitos = hs.toList()), Memoria(), ctx(ahoraMin = ahoraMin))

    private fun escapado(
        id: String = "h1",
        racha: Int = 0,
        tipo: TipoHabito = TipoHabito.CHECK,
        objetivo: Int = 1,
    ) = habito(id, tipo = tipo, objetivo = objetivo, racha = racha, falladoAyer = true, cumplidosTresAnteriores = 1)

    @Test
    fun `from thirty minutes before the night it no longer comes out`() {
        // La noche del fixture es a las 21:30: la fuente la da por caducada desde las 21:00.
        assertEquals(1, sale(escapado(), ahoraMin = 20 * 60 + 59).size)
        assertTrue(sale(escapado(), ahoraMin = 21 * 60).isEmpty())
    }

    @Test
    fun `a daily habit that failed yesterday comes out with the hour and three actions`() {
        val c = sale(escapado()).single()
        assertEquals(Situacion.AYER_SE_ESCAPO, c.situacion)
        assertEquals("h1", c.ref)
        assertEquals(14 * 60 + 30, c.hora)
        assertEquals(listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO), c.acciones)
        assertEquals(ValorHueco.Hora(14 * 60 + 30), c.huecos[Hueco.HORA])
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertNull(c.huecos[Hueco.MINUTOS])
        assertEquals(Destino.Habito("h1"), c.destino)
    }

    @Test
    fun `without a possible hour there is no hour and no Vale`() {
        val c = sale(escapado(), ahoraMin = 20 * 60 + 50).single()
        assertNull(c.hora)
        assertNull(c.huecos[Hueco.HORA])
        assertEquals(listOf(Respuesta.AHORA, Respuesta.HOY_NO), c.acciones)
    }

    @Test
    fun `a duration habit brings its minutes and the others do not`() {
        assertEquals(ValorHueco.Numero(20), sale(escapado(tipo = TipoHabito.DURACION, objetivo = 20)).single().huecos[Hueco.MINUTOS])
        assertNull(sale(escapado(tipo = TipoHabito.CONTADOR, objetivo = 8)).single().huecos[Hueco.MINUTOS])
    }

    @Test
    fun `the better streak weighs more`() {
        val cs = sale(escapado("a", racha = 2), escapado("b", racha = 9))
        assertEquals("b", cs.maxByOrNull { it.peso }!!.ref)
        assertEquals(9, cs.single { it.ref == "b" }.peso)
    }

    @Test
    fun `a weekly habit does not qualify`() = assertTrue(sale(habito(diario = false, falladoAyer = true)).isEmpty())

    @Test
    fun `limit and quit habits do not qualify`() {
        assertTrue(sale(escapado(tipo = TipoHabito.LIMITE)).isEmpty())
        assertTrue(sale(escapado(tipo = TipoHabito.DEJAR)).isEmpty())
    }

    @Test
    fun `failing the three previous days too does not qualify and one good day does`() {
        assertTrue(sale(habito(falladoAyer = true, cumplidosTresAnteriores = 0)).isEmpty())
        assertEquals(1, sale(habito(falladoAyer = true, cumplidosTresAnteriores = 1)).size)
    }

    @Test
    fun `not failed yesterday, done today or not due today do not qualify`() {
        assertTrue(sale(habito(falladoAyer = false)).isEmpty())
        assertTrue(sale(habito(falladoAyer = true, hechoHoy = true)).isEmpty())
        assertTrue(sale(habito(falladoAyer = true, exigibleHoy = false)).isEmpty())
    }
}
