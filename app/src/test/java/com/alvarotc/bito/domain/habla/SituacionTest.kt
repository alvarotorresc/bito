package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.propuestas.Ambito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SituacionTest {
    private val exentasDelTope =
        setOf(Situacion.CERRAR_DIA, Situacion.TODO_HECHO, Situacion.CONFIRMA_VALE, Situacion.GORDO_ELEGIDO, Situacion.PERMISO_AVISOS)

    @Test
    fun `there are thirty four situations`() = assertEquals(34, Situacion.entries.size)

    @Test
    fun `every situation with buttons has a core bag`() =
        Situacion.entries.filter { it.conBotones }.forEach { assertNotNull(it.name, it.nucleo) }

    @Test
    fun `every core bag is a core piece`() =
        Situacion.entries.forEach { s -> s.nucleo?.let { assertEquals(s.name, TipoTrozo.NUCLEO, it.tipo) } }

    @Test
    fun `core bags follow the names of section 8_1`() {
        assertEquals(Bolsa.NU_PERMISO_AVISOS, Situacion.PERMISO_AVISOS.nucleo)
        assertEquals(Bolsa.NU_AYER_ESCAPO, Situacion.AYER_SE_ESCAPO.nucleo)
        assertEquals(Bolsa.NU_CONFIRMA_VALE, Situacion.CONFIRMA_VALE.nucleo)
        assertEquals(Bolsa.NU_TRAS_HOY_NO, Situacion.TRAS_HOY_NO.nucleo)
        assertEquals(Bolsa.FI_PAUSA, Situacion.FI_PAUSA.nucleo)
        assertEquals(Bolsa.CA_AUSENCIA, Situacion.CA_AUSENCIA.nucleo)
        assertEquals(Bolsa.CIE_NADA_PENDIENTE, Situacion.CIE_NADA_PENDIENTE.nucleo)
    }

    @Test
    fun `the situations with buttons are the twelve first of Hoy, the confirmations and two notes of the habit page`() =
        assertEquals(
            setOf(
                Situacion.PERMISO_AVISOS, Situacion.VUELTA, Situacion.DIA_DURO, Situacion.CERRAR_DIA,
                Situacion.TODO_HECHO, Situacion.ELEGIR_GORDO, Situacion.GORDO_SIN_EMPEZAR, Situacion.AYER_SE_ESCAPO,
                Situacion.RACHA_CERCA, Situacion.DIA_FLOJO, Situacion.HORA_HABITUAL, Situacion.TAREA_VENCE,
                Situacion.CONFIRMA_VALE, Situacion.GORDO_ELEGIDO, Situacion.FI_HOY_FALTA, Situacion.FI_OBJETIVO,
            ),
            Situacion.entries.filter { it.conBotones }.toSet(),
        )

    @Test
    fun `the five exempt situations never count for the daily cap`() = exentasDelTope.forEach { assertFalse(it.name, it.cuentaTope) }

    @Test
    fun `counting for the cap implies having buttons`() =
        Situacion.entries.filter { it.cuentaTope }.forEach { assertTrue(it.name, it.conBotones) }

    @Test
    fun `greetings, comments and closing lines do not count for the cap`() {
        assertFalse(Situacion.SALUDO.cuentaTope)
        assertFalse(Situacion.NOCHE.cuentaTope)
        assertFalse(Situacion.TRAS_HOY_NO.cuentaTope)
        assertFalse(Situacion.CA_NORMAL.cuentaTope)
        assertFalse(Situacion.CIE_SENCILLO.cuentaTope)
        assertFalse(Situacion.FI_CONSTANCIA.cuentaTope)
    }

    @Test
    fun `a proposal with buttons that is not exempt counts for the cap`() {
        assertTrue(Situacion.AYER_SE_ESCAPO.cuentaTope)
        assertTrue(Situacion.VUELTA.cuentaTope)
        assertTrue(Situacion.TAREA_VENCE.cuentaTope)
    }

    @Test
    fun `the response situations belong to Hoy`() =
        listOf(Situacion.CONFIRMA_VALE, Situacion.GORDO_ELEGIDO, Situacion.TRAS_HOY_NO, Situacion.NOCHE)
            .forEach { assertEquals(it.name, Ambito.HOY, it.ambito) }

    @Test
    fun `the proposal id joins situation, ref and day`() {
        assertEquals("AYER_SE_ESCAPO:h1:$TODAY", idPropuesta(Situacion.AYER_SE_ESCAPO, "h1", TODAY))
        assertEquals("SALUDO::$TODAY", idPropuesta(Situacion.SALUDO, null, TODAY))
    }
}
