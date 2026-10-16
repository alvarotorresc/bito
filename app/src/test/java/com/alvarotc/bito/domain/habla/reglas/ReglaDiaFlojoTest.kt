package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.respuesta
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaDiaFlojoTest {
    private fun flojo(
        muestras: Int = 5,
        dia: Int? = 40,
        media: Int? = 80,
        semanas: Int = 6,
        diario: Boolean = true,
    ) = habito(pct30 = media, pctHoySemana = dia, muestrasHoySemana = muestras, semanasHistoria = semanas, diario = diario)

    private fun sale(
        h: HabitoHoy,
        m: Memoria = Memoria(),
    ) = ReglaDiaFlojo.candidatas(hechos(habitos = listOf(h)), m, ctx())

    @Test
    fun `five samples come out and four do not`() {
        assertEquals(1, sale(flojo(muestras = 5)).size)
        assertTrue(sale(flojo(muestras = 4)).isEmpty())
    }

    @Test
    fun `twenty-five points below the average come out and twenty-four do not`() {
        assertEquals(1, sale(flojo(dia = 50, media = 75)).size)
        assertTrue(sale(flojo(dia = 50, media = 74)).isEmpty())
    }

    @Test
    fun `fifty percent comes out and fifty-one does not`() {
        assertEquals(1, sale(flojo(dia = 50, media = 90)).size)
        assertTrue(sale(flojo(dia = 51, media = 90)).isEmpty())
    }

    @Test
    fun `six weeks of history are needed and only for daily habits`() {
        assertTrue(sale(flojo(semanas = 5)).isEmpty())
        assertTrue(sale(flojo(diario = false)).isEmpty())
    }

    @Test
    fun `without percentages it does not come out`() {
        assertTrue(sale(flojo(dia = null)).isEmpty())
        assertTrue(sale(flojo(media = null)).isEmpty())
    }

    @Test
    fun `it names the habit, the weekday and the hour`() {
        val c = sale(flojo()).single()
        assertEquals(Situacion.DIA_FLOJO, c.situacion)
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertEquals(ValorHueco.DiaSemana(ctx().diaSemana), c.huecos[Hueco.DIA_SEMANA])
        assertEquals(ValorHueco.Hora(14 * 60 + 30), c.huecos[Hueco.HORA])
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), c.acciones)
        assertEquals(Destino.Habito("h1"), c.destino)
    }

    @Test
    fun `an answer to this habit within fourteen days silences it and fourteen days lets it through`() {
        fun con(hace: Int) =
            Memoria(eventos = listOf(respuesta(Situacion.DIA_FLOJO, "h1", Respuesta.HOY_NO, millisOn(TODAY - hace, 10, 0), TODAY - hace)))
        assertTrue(sale(flojo(), con(13)).isEmpty())
        assertEquals(1, sale(flojo(), con(14)).size)
    }

    @Test
    fun `an answer for another habit or another situation does not silence it`() {
        val otro = Memoria(eventos = listOf(respuesta(Situacion.DIA_FLOJO, "h2", Respuesta.HOY_NO, millisOn(TODAY, 9, 0))))
        val otra = Memoria(eventos = listOf(respuesta(Situacion.RACHA_CERCA, "h1", Respuesta.HOY_NO, millisOn(TODAY, 9, 0))))
        assertEquals(1, sale(flojo(), otro).size)
        assertEquals(1, sale(flojo(), otra).size)
    }
}
