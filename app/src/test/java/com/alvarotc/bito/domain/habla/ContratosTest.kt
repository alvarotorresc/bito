package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.habla.reglas.Respuestas
import com.alvarotc.bito.domain.propuestas.Ambito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que las firmas de la Ola 0 prometen a los carriles, sea cual sea su cuerpo. */
class ContratosTest {
    @Test
    fun `the engine fits the candidate chooser the source receives by default`() {
        val elegir = ElegirCandidata(Motor::elegir)
        assertNull(elegir.elegir(Ambito.DESCANSO, hechos(), Memoria(), ctx(), null))
    }

    @Test
    fun `the line after Not today and the night line bring a count and the first pending task`() {
        val h =
            hechos(
                habitos = listOf(habito(id = "h1"), habito(id = "h2", hechoHoy = true)),
                tareas =
                    listOf(
                        tarea(id = "t0", titulo = "Hecha", hecha = true),
                        tarea(id = "t1", titulo = "El informe"),
                        tarea(id = "t2"),
                    ),
                pendientesCierre = 5,
            )
        listOf(Respuestas.trasHoyNo(h), Respuestas.noche(h)).forEach { c ->
            assertTrue(c.situacion.name, Hueco.N in c.huecos)
            assertEquals(c.situacion.name, ValorHueco.Texto("El informe"), c.huecos[Hueco.TAREA])
        }
        assertEquals(Situacion.TRAS_HOY_NO, Respuestas.trasHoyNo(h).situacion)
        assertEquals(Situacion.NOCHE, Respuestas.noche(h).situacion)
    }

    @Test
    fun `without pending tasks there is no task gap`() {
        assertNull(Respuestas.trasHoyNo(hechos()).huecos[Hueco.TAREA])
        assertNull(Respuestas.noche(hechos()).huecos[Hueco.TAREA])
    }
}
