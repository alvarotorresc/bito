package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.habla.reglas.Respuestas
import com.alvarotc.bito.domain.propuestas.Ambito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lo que las firmas de la Ola 0 prometen a los carriles, sea cual sea su cuerpo. */
class ContratosTest {
    @Test
    fun `the engine fits the candidate chooser the source receives by default`() {
        val elegir = ElegirCandidata(Motor::elegir)
        assertNull(elegir.elegir(Ambito.DESCANSO, hechos(), Memoria(), ctx(), null))
    }

    @Test
    fun `the line after Not today and the night line take the facts and keep their situation`() {
        assertEquals(Situacion.TRAS_HOY_NO, Respuestas.trasHoyNo(hechos()).situacion)
        assertEquals(Situacion.NOCHE, Respuestas.noche(hechos()).situacion)
    }
}
