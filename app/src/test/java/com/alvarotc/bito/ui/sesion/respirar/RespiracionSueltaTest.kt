package com.alvarotc.bito.ui.sesion.respirar

import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class RespiracionSueltaTest {
    private val elapsed = AtomicLong(1_000L)
    private val reglas = RespiracionSuelta({ elapsed.get() }, { 50_000L + elapsed.get() })

    private fun respirar(
        modo: BreathingMode,
        ms: Long,
    ): Desenlace {
        reglas.modo = modo
        reglas.empezar()
        elapsed.addAndGet(ms)
        return reglas.cerrar()
    }

    @Test
    fun `durations are 120 114 and 128 s`() {
        assertEquals(120_000L, BreathingRhythm.totalMillis(BreathingMode.CALM))
        assertEquals(114_000L, BreathingRhythm.totalMillis(BreathingMode.SLEEP))
        assertEquals(128_000L, BreathingRhythm.totalMillis(BreathingMode.FOCUS))
        for ((modo, seg) in listOf(BreathingMode.CALM to 120, BreathingMode.SLEEP to 114, BreathingMode.FOCUS to 128)) {
            val d = respirar(modo, seg * 1_000L) as Desenlace.Guardar
            assertEquals(seg, d.cierre.segundos)
            assertTrue(d.cierre.completa)
            assertEquals(EtapaSuelta.COMO_ESTAS, reglas.etapa)
        }
    }

    @Test
    fun `Sleep at 7_5 s holds full`() {
        reglas.modo = BreathingMode.SLEEP
        reglas.empezar()
        val p = reglas.punto(reglas.ancla + 4_000L + 3_500L)
        assertEquals(BreathPhase.HOLD, p.phase)
        assertEquals(1f, p.fill, 0f)
    }

    @Test
    fun `under 10 s nothing is saved and it goes back to choose, from 10 s it is saved`() {
        assertEquals(Desenlace.Descartada, respirar(BreathingMode.CALM, 9_999L))
        assertEquals(EtapaSuelta.ELEGIR, reglas.etapa)
        val d = respirar(BreathingMode.CALM, 10_000L) as Desenlace.Guardar
        assertEquals(10, d.cierre.segundos)
        assertEquals(false, d.cierre.completa)
        assertEquals(50_000L + reglas.ancla, d.cierre.inicioPared)
    }

    @Test
    fun `the end is reached once the cycles are done and never saves more than the total`() {
        val d = respirar(BreathingMode.SLEEP, 500_000L) as Desenlace.Guardar
        assertEquals(114, d.cierre.segundos)
        assertTrue(d.cierre.completa)
    }

    @Test
    fun `the end time is the anchor plus the whole exercise`() {
        reglas.modo = BreathingMode.FOCUS
        reglas.empezar()
        assertEquals(1_000L + 128_000L, reglas.finElapsed)
        assertEquals(8, reglas.ciclos)
    }
}
