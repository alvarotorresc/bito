package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Motor
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.respuesta
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class ReglasFichaTest {
    private fun nota(
        h: HabitoHoy,
        memoria: Memoria = Memoria(),
    ): Candidata? = Motor.elegir(Ambito.FICHA, hechos(habitos = listOf(h)), memoria, ctx(), h.id)

    @Test
    fun `a paused habit gets the pause note before anything else`() {
        val c =
            nota(
                habito(enPausa = true, pct30 = 10, racha = 5, creadoHaceDias = 2, falladoAyer = true)
                    .copy(pausadoDesde = TODAY - 3),
            )!!
        assertEquals(Situacion.FI_PAUSA, c.situacion)
        assertEquals(ValorHueco.Fecha((TODAY - 3).toLong()), c.huecos[Hueco.FECHA])
    }

    @Test
    fun `an archived habit or no habit id has no note`() {
        val h = hechos(habitos = listOf(habito("h1")))
        assertNull(Motor.elegir(Ambito.FICHA, h, Memoria(), ctx(), "archivado"))
        assertNull(Motor.elegir(Ambito.FICHA, h, Memoria(), ctx(), null))
    }

    @Test
    fun `pending today with a possible hour offers the hour`() {
        val c = nota(habito(racha = 5))!!
        assertEquals(Situacion.FI_HOY_FALTA, c.situacion)
        assertEquals(14 * 60 + 30, c.hora)
        assertEquals(ValorHueco.Hora(14 * 60 + 30), c.huecos[Hueco.HORA])
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), c.acciones)
        assertEquals(Destino.Habito("h1"), c.destino)
    }

    @Test
    fun `with two accepted notices today the pending note does not come out`() {
        fun aviso(
            id: String,
            dia: Int = TODAY,
        ) = AvisoAceptado(id, dia, "hx", 18 * 60, 0, EstadoAviso.PENDIENTE, "p-$id", 1L)
        val h = habito(racha = 5, mejorRacha = 9)
        assertEquals(Situacion.FI_HOY_FALTA, nota(h, Memoria(avisos = listOf(aviso("a"))))!!.situacion)
        assertEquals(Situacion.FI_RACHA_VS_MEJOR, nota(h, Memoria(avisos = listOf(aviso("a"), aviso("b"))))!!.situacion)
        val deAyer = Memoria(avisos = listOf(aviso("a", TODAY - 1), aviso("b", TODAY - 1)))
        assertEquals(Situacion.FI_HOY_FALTA, nota(h, deAyer)!!.situacion)
    }

    @Test
    fun `done today or without a possible hour the pending note does not come out`() {
        assertEquals(Situacion.FI_CONSTANCIA, nota(habito(hechoHoy = true))!!.situacion)
        val tarde = Motor.elegir(Ambito.FICHA, hechos(habitos = listOf(habito())), Memoria(), ctx(ahoraMin = 21 * 60), "h1")!!
        assertEquals(Situacion.FI_CONSTANCIA, tarde.situacion)
    }

    @Test
    fun `objective note at 39 percent and 14 judged comes out, 40 percent or 13 judged do not`() {
        val hecho = { pct: Int, juz: Int -> nota(habito(hechoHoy = true, pct30 = pct, juzgados30 = juz, objetivo = 8, paso = 1)) }
        val c = hecho(39, 14)!!
        assertEquals(Situacion.FI_OBJETIVO, c.situacion)
        assertEquals(ValorHueco.Numero(39), c.huecos[Hueco.PCT])
        assertEquals(ValorHueco.Numero(4), c.huecos[Hueco.OBJETIVO])
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), c.acciones)
        assertEquals(Situacion.FI_CONSTANCIA, hecho(40, 14)!!.situacion)
        assertEquals(Situacion.FI_CONSTANCIA, hecho(39, 13)!!.situacion)
    }

    @Test
    fun `the suggested objective is half rounded down with the step as minimum`() {
        fun sugerido(
            objetivo: Int,
            paso: Int,
        ) = nota(habito(hechoHoy = true, pct30 = 10, objetivo = objetivo, paso = paso))!!.huecos[Hueco.OBJETIVO]
        assertEquals(ValorHueco.Numero(4), sugerido(8, 1))
        assertEquals(ValorHueco.Numero(1), sugerido(2, 1))
        assertEquals(ValorHueco.Numero(5), sugerido(9, 5))
        assertEquals(ValorHueco.Numero(4), sugerido(9, 1))
    }

    @Test
    fun `the objective note does not come out when it would not lower the objective`() {
        fun situacion(
            objetivo: Int,
            paso: Int,
        ) = nota(habito(hechoHoy = true, pct30 = 10, objetivo = objetivo, paso = paso))!!.situacion
        assertEquals(Situacion.FI_CONSTANCIA, situacion(1, 1))
        assertEquals(Situacion.FI_CONSTANCIA, situacion(5, 5))
        assertEquals(Situacion.FI_CONSTANCIA, situacion(4, 5))
        assertEquals(Situacion.FI_OBJETIVO, situacion(2, 1))
        assertEquals(Situacion.FI_OBJETIVO, situacion(6, 5))
    }

    @Test
    fun `limit and quit habits never get the objective note`() {
        for (tipo in listOf(TipoHabito.LIMITE, TipoHabito.DEJAR)) {
            val c = nota(habito(tipo = tipo, hechoHoy = true, pct30 = 10, objetivo = 8))!!
            assertEquals(Situacion.FI_CONSTANCIA, c.situacion)
        }
    }

    @Test
    fun `a new habit, a streak, a clean streak and the best day follow the priority`() {
        val base = habito(hechoHoy = true, pct30 = 80)
        assertEquals(Situacion.FI_NUEVO, nota(base.copy(creadoHaceDias = 6, racha = 5))!!.situacion)
        assertEquals(Situacion.FI_CONSTANCIA, nota(base.copy(creadoHaceDias = 7))!!.situacion)

        val racha = nota(base.copy(racha = 3, mejorRacha = 9))!!
        assertEquals(Situacion.FI_RACHA_VS_MEJOR, racha.situacion)
        assertEquals(ValorHueco.Numero(3), racha.huecos[Hueco.RACHA])
        assertEquals(ValorHueco.Numero(9), racha.huecos[Hueco.MEJOR])
        assertEquals(Situacion.FI_CONSTANCIA, nota(base.copy(racha = 2))!!.situacion)

        val limpio = nota(base.copy(tipo = TipoHabito.DEJAR, diasLimpios = 4, racha = 4))!!
        assertEquals(Situacion.FI_RACHA_VS_MEJOR, limpio.situacion)
        val soloLimpio = nota(base.copy(tipo = TipoHabito.DEJAR, diasLimpios = 4))!!
        assertEquals(Situacion.FI_LIMPIO, soloLimpio.situacion)
        assertEquals(ValorHueco.Numero(4), soloLimpio.huecos[Hueco.N])
        assertEquals(Situacion.FI_CONSTANCIA, nota(base.copy(tipo = TipoHabito.DEJAR, diasLimpios = 0))!!.situacion)
    }

    @Test
    fun `best day needs six weeks and fifteen points above the average`() {
        val base = habito(hechoHoy = true, pct30 = 60).copy(mejorDiaSemana = DayOfWeek.TUESDAY, pctMejorDiaSemana = 75)
        val c = nota(base)!!
        assertEquals(Situacion.FI_MEJOR_DIA, c.situacion)
        assertEquals(ValorHueco.DiaSemana(DayOfWeek.TUESDAY), c.huecos[Hueco.DIA_SEMANA])
        assertEquals(ValorHueco.Numero(75), c.huecos[Hueco.PCT])
        assertEquals(Situacion.FI_CONSTANCIA, nota(base.copy(pctMejorDiaSemana = 74))!!.situacion)
        assertEquals(Situacion.FI_CONSTANCIA, nota(base.copy(semanasHistoria = 5))!!.situacion)
    }

    @Test
    fun `the default is the thirty day percentage`() {
        val c = nota(habito(hechoHoy = true, pct30 = 83))!!
        assertEquals(Situacion.FI_CONSTANCIA, c.situacion)
        assertEquals(ValorHueco.Numero(83), c.huecos[Hueco.PCT])
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertEquals(emptyList<Respuesta>(), c.acciones)
    }

    @Test
    fun `no hace falta hides that note of that habit for six days but not for seven`() {
        val h = habito(hechoHoy = true, pct30 = 10, objetivo = 8)

        fun conRespuestaHace(
            dias: Int,
            habit: String = "h1",
        ) = Memoria(
            eventos = listOf(respuesta(Situacion.FI_OBJETIVO, habit, Respuesta.HOY_NO, 1L, dia = TODAY - dias)),
        )
        assertEquals(Situacion.FI_CONSTANCIA, nota(h, conRespuestaHace(6))!!.situacion)
        assertEquals(Situacion.FI_OBJETIVO, nota(h, conRespuestaHace(7))!!.situacion)
        assertEquals(Situacion.FI_OBJETIVO, nota(h, conRespuestaHace(0, habit = "otro"))!!.situacion)
    }

    @Test
    fun `no hace falta on one note does not hide another`() {
        val h = habito(racha = 5)
        val m = Memoria(eventos = listOf(respuesta(Situacion.FI_OBJETIVO, "h1", Respuesta.HOY_NO, 1L)))
        assertEquals(Situacion.FI_HOY_FALTA, nota(h, m)!!.situacion)
    }
}
