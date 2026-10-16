package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Motor
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.propuestas.Ambito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasCaminoTest {
    private fun comenta(
        animo: Mood,
        perfectos: Int = 3,
        delta: Int? = 12,
    ) = Motor.elegir(
        Ambito.CAMINO,
        hechos().copy(animoSemana = animo, diasPerfectosMes = perfectos, deltaSemanaPct = delta),
        Memoria(),
        ctx(),
    )!!

    @Test
    fun `each weekly mood has its own comment`() {
        assertEquals(Situacion.CA_RADIANTE, comenta(Mood.RADIANT).situacion)
        assertEquals(Situacion.CA_NORMAL, comenta(Mood.NORMAL).situacion)
        assertEquals(Situacion.CA_MUSTIO, comenta(Mood.WILTED).situacion)
        assertEquals(Situacion.CA_AUSENCIA, comenta(Mood.DRAMATIC).situacion)
    }

    @Test
    fun `it brings the perfect days and the weekly delta, and has no actions`() {
        val c = comenta(Mood.NORMAL, perfectos = 5, delta = -8)
        assertEquals(ValorHueco.Numero(5), c.huecos[Hueco.N])
        assertEquals(ValorHueco.Numero(-8), c.huecos[Hueco.PCT])
        assertTrue(c.acciones.isEmpty())
        assertEquals(null, c.ref)
    }

    @Test
    fun `without a delta the percentage hole is left out`() {
        val c = comenta(Mood.RADIANT, delta = null)
        assertFalse(Hueco.PCT in c.huecos)
        assertEquals(ValorHueco.Numero(3), c.huecos[Hueco.N])
    }

    @Test
    fun `without perfect days this month the count hole is left out`() {
        val c = comenta(Mood.WILTED, perfectos = 0)
        assertFalse(Hueco.N in c.huecos)
        assertEquals(ValorHueco.Numero(12), c.huecos[Hueco.PCT])
    }
}
