package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.propuestas.Ambito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioridadesTest {
    private val respuestas = setOf(Situacion.CONFIRMA_VALE, Situacion.GORDO_ELEGIDO, Situacion.TRAS_HOY_NO, Situacion.NOCHE)
    private val listas =
        mapOf(
            Ambito.HOY to Prioridades.HOY,
            Ambito.FICHA to Prioridades.FICHA,
            Ambito.CAMINO to Prioridades.CAMINO,
            Ambito.CIERRE to Prioridades.CIERRE,
        )

    @Test
    fun `Hoy follows the thirteen rows of section 5_1`() =
        assertEquals(
            listOf(
                Situacion.PERMISO_AVISOS, Situacion.VUELTA, Situacion.DIA_DURO, Situacion.CERRAR_DIA, Situacion.TODO_HECHO,
                Situacion.ELEGIR_GORDO, Situacion.GORDO_SIN_EMPEZAR, Situacion.AYER_SE_ESCAPO, Situacion.RACHA_CERCA,
                Situacion.DIA_FLOJO, Situacion.HORA_HABITUAL, Situacion.TAREA_VENCE, Situacion.SALUDO,
            ),
            Prioridades.HOY,
        )

    @Test
    fun `the habit page follows section 5_7`() =
        assertEquals(
            listOf(
                Situacion.FI_PAUSA,
                Situacion.FI_HOY_FALTA,
                Situacion.FI_OBJETIVO,
                Situacion.FI_NUEVO,
                Situacion.FI_RACHA_VS_MEJOR,
                Situacion.FI_LIMPIO,
                Situacion.FI_MEJOR_DIA,
                Situacion.FI_CONSTANCIA,
            ),
            Prioridades.FICHA,
        )

    @Test
    fun `Camino puts the absence first`() =
        assertEquals(
            listOf(Situacion.CA_AUSENCIA, Situacion.CA_RADIANTE, Situacion.CA_MUSTIO, Situacion.CA_NORMAL),
            Prioridades.CAMINO,
        )

    @Test
    fun `the closing follows section 5_9_3`() =
        assertEquals(
            listOf(
                Situacion.CIE_GORDO_HECHO,
                Situacion.CIE_LOGRO,
                Situacion.CIE_RACHA_AVANZA,
                Situacion.CIE_NADA_PENDIENTE,
                Situacion.CIE_SENCILLO,
            ),
            Prioridades.CIERRE,
        )

    @Test
    fun `no situation is in two lists`() {
        val todas = listas.values.flatten()
        assertEquals(todas.size, todas.toSet().size)
    }

    @Test
    fun `every situation is in the list of its scope, except the responses`() =
        Situacion.entries.filterNot { it in respuestas }.forEach { s ->
            assertTrue(s.name, s in listas.getValue(s.ambito))
        }

    @Test
    fun `the responses are in no list`() = respuestas.forEach { s -> assertTrue(s.name, listas.values.none { s in it }) }
}
