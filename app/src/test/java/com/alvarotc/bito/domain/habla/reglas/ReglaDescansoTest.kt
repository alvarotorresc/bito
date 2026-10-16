package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.ContextoDescanso
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaDescansoTest {
    private val corto = ContextoDescanso("s1", 2, largo = false, minutos = 5, excluir = null)
    private val largo = ContextoDescanso("s1", 4, largo = true, minutos = 15, excluir = null)

    private fun elige(
        vararg hs: HabitoHoy,
        d: ContextoDescanso = corto,
        m: Memoria = Memoria(),
        ahoraMin: Int = 10 * 60,
    ): Candidata = ReglaDescanso.candidata(hechos(habitos = hs.toList()), m, ctx(ahoraMin = ahoraMin), d)

    private fun Candidata.habito(): String? = (destino as? Destino.Habito)?.id

    @Test
    fun `a pending habit comes with its name and the minutes and no buttons`() {
        val c = elige(habito("a", nombre = "Estirar"))
        assertEquals(Situacion.DESCANSO_HABITO, c.situacion)
        assertEquals("s1:2:a", c.ref)
        assertEquals("a", ReglaDescanso.habitoDe(c.ref))
        assertEquals("a", c.habito())
        assertEquals(ValorHueco.Texto("Estirar"), c.huecos[Hueco.HABITO])
        assertEquals(ValorHueco.Numero(5), c.huecos[Hueco.MINUTOS])
        assertTrue(c.acciones.isEmpty())
    }

    @Test
    fun `the long break uses the long situation`() {
        val c = elige(habito("a"), d = largo)
        assertEquals(Situacion.DESCANSO_HABITO_LARGO, c.situacion)
        assertEquals(ValorHueco.Numero(15), c.huecos[Hueco.MINUTOS])
    }

    @Test
    fun `limit, quit, paused, done and not due habits are never proposed`() {
        val c =
            elige(
                habito("limite", tipo = TipoHabito.LIMITE),
                habito("dejar", tipo = TipoHabito.DEJAR),
                habito("pausa", enPausa = true),
                habito("hecho", hechoHoy = true),
                habito("noToca", exigibleHoy = false),
            )
        assertEquals(Situacion.DESCANSO_SIN_PENDIENTES, c.situacion)
        assertEquals(null, c.habito())
        assertEquals("s1:2", c.ref)
        assertTrue(Hueco.HABITO !in c.huecos)
    }

    @Test
    fun `the session own habit is excluded`() {
        val c = elige(habito("a"), habito("b", objetivo = 9), d = corto.copy(excluir = "a"))
        assertEquals("b", c.habito())
    }

    @Test
    fun `a habit with Not today answered today is not proposed`() {
        val hoyNo =
            EventoMemoria(
                "RESPUESTA:x",
                TipoMemoria.RESPUESTA,
                TODAY,
                "HORA_HABITUAL:a:$TODAY",
                Respuesta.HOY_NO.ordinal,
                1L,
            )
        val c = elige(habito("a"), habito("b", objetivo = 9), m = Memoria(eventos = listOf(hoyNo)))
        assertEquals("b", c.habito())
    }

    @Test
    fun `the habitual hour nearest to now goes first`() {
        val c =
            elige(
                habito("lejos", horaHabitual = 18 * 60),
                habito("cerca", horaHabitual = 10 * 60 + 20),
                habito("sin", horaHabitual = null, hitoCerca = 7, objetivo = 1),
            )
        assertEquals("cerca", c.habito())
    }

    @Test
    fun `without hours a streak near a milestone goes before the smallest target`() {
        val c =
            elige(
                habito("pequeno", objetivo = 1),
                habito("hito", objetivo = 8, hitoCerca = 7, faltanHito = 1),
            )
        assertEquals("hito", c.habito())
    }

    @Test
    fun `then the smallest target, and the id breaks the tie`() {
        assertEquals("b", elige(habito("a", objetivo = 5), habito("b", objetivo = 2)).habito())
        assertEquals("a", elige(habito("b", objetivo = 2), habito("a", objetivo = 2)).habito())
    }

    @Test
    fun `the short break prefers a check`() {
        val c = elige(habito("dur", tipo = TipoHabito.DURACION, objetivo = 2), habito("chk", objetivo = 30))
        assertEquals("chk", c.habito())
    }

    @Test
    fun `the long break prefers a duration that fits and falls back to the rest`() {
        val cabe = habito("cabe", tipo = TipoHabito.DURACION, objetivo = 10)
        val noCabe = habito("noCabe", tipo = TipoHabito.DURACION, objetivo = 40)
        val chk = habito("chk", objetivo = 1)
        assertEquals("cabe", elige(chk, noCabe, cabe, d = largo).habito())
        assertEquals("chk", elige(chk, noCabe, d = largo).habito())
    }

    @Test
    fun `a counter is proposed only when one step finishes it`() {
        val sinPaso = habito("lejos", tipo = TipoHabito.CONTADOR, objetivo = 8, paso = 1)
        val unPaso = habito("uno", tipo = TipoHabito.CONTADOR, objetivo = 1, paso = 1)
        assertEquals(Situacion.DESCANSO_SIN_PENDIENTES, elige(sinPaso).situacion)
        assertEquals("uno", elige(sinPaso, unPaso).habito())
    }

    @Test
    fun `nothing pending says to get up`() {
        val c = elige()
        assertEquals(Situacion.DESCANSO_SIN_PENDIENTES, c.situacion)
        assertTrue(c.acciones.isEmpty())
    }
}
