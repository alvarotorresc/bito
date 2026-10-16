package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.respuesta
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaPermisoAvisosTest {
    private fun aceptado(dia: Int = TODAY) = AvisoAceptado("a1", dia, "h1", 14 * 60 + 30, 0, EstadoAviso.PENDIENTE, "p1", millisOn(dia, 9))

    private fun respondida(haceDias: Int) =
        Memoria(
            eventos =
                listOf(
                    respuesta(Situacion.PERMISO_AVISOS, null, Respuesta.HOY_NO, millisOn(TODAY - haceDias, 10), dia = TODAY - haceDias),
                ),
        )

    @Test
    fun `without notification permission it asks`() {
        val c = ReglaPermisoAvisos.candidatas(hechos(), Memoria(), ctx(permisoAvisos = false)).single()
        assertEquals(Situacion.PERMISO_AVISOS, c.situacion)
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), c.acciones)
        assertEquals(Bolsa.NU_PERMISO_AVISOS, c.nucleo)
        assertNull(c.ref)
        assertNull(c.destino)
    }

    @Test
    fun `with every permission it does not ask`() =
        assertTrue(ReglaPermisoAvisos.candidatas(hechos(), Memoria(avisos = listOf(aceptado())), ctx()).isEmpty())

    @Test
    fun `without exact alarms it asks only with an accepted reminder today`() {
        val sinAceptado = ReglaPermisoAvisos.candidatas(hechos(), Memoria(), ctx(alarmasExactas = false))
        val deAyer = ReglaPermisoAvisos.candidatas(hechos(), Memoria(avisos = listOf(aceptado(TODAY - 1))), ctx(alarmasExactas = false))
        val conAceptado = ReglaPermisoAvisos.candidatas(hechos(), Memoria(avisos = listOf(aceptado())), ctx(alarmasExactas = false))
        assertTrue(sinAceptado.isEmpty())
        assertTrue(deAyer.isEmpty())
        assertEquals(Bolsa.NU_PERMISO_ALARMAS, conAceptado.single().nucleo)
    }

    @Test
    fun `a cancelled accepted reminder does not ask for exact alarms`() {
        val cancelado = aceptado().copy(estado = EstadoAviso.CANCELADO)
        assertTrue(ReglaPermisoAvisos.candidatas(hechos(), Memoria(avisos = listOf(cancelado)), ctx(alarmasExactas = false)).isEmpty())
    }

    @Test
    fun `missing both, notifications come first`() =
        assertEquals(
            Bolsa.NU_PERMISO_AVISOS,
            ReglaPermisoAvisos
                .candidatas(hechos(), Memoria(avisos = listOf(aceptado())), ctx(permisoAvisos = false, alarmasExactas = false))
                .single()
                .nucleo,
        )

    @Test
    fun `an answer six days ago keeps it quiet`() =
        assertTrue(ReglaPermisoAvisos.candidatas(hechos(), respondida(6), ctx(permisoAvisos = false)).isEmpty())

    @Test
    fun `an answer seven days ago lets it ask again`() =
        assertEquals(1, ReglaPermisoAvisos.candidatas(hechos(), respondida(7), ctx(permisoAvisos = false)).size)

    @Test
    fun `answers to other situations do not count`() {
        val m =
            Memoria(eventos = listOf(respuesta(Situacion.VUELTA, "h1", Respuesta.HOY_NO, millisOn(TODAY, 9))))
        assertEquals(1, ReglaPermisoAvisos.candidatas(hechos(), m, ctx(permisoAvisos = false)).size)
    }
}
