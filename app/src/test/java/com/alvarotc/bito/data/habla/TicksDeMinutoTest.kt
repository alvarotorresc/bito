package com.alvarotc.bito.data.habla

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** El reloj de la tarjeta de Habi, con el tiempo virtual de la prueba. */
class TicksDeMinutoTest {
    // 10:00:45: el primer cambio de minuto llega a los 15 s.
    private val inicio = 10 * 3_600_000L + 45_000L

    @Test
    fun `ticks on listening and then on every change of minute`() =
        runTest {
            val momentos = mutableListOf<Long>()
            backgroundScope.launch { ticksDeMinuto { inicio + currentTime }.collect { momentos += currentTime } }
            advanceTimeBy(140_000)
            runCurrent()
            assertEquals(listOf(0L, 15_000L, 75_000L, 135_000L), momentos)
        }

    @Test
    fun `nothing ticks once nobody listens`() =
        runTest {
            val vistos = ticksDeMinuto { inicio + currentTime }.take(2).toList()
            assertEquals(2, vistos.size)
            assertEquals(15_000L, currentTime)
            advanceTimeBy(600_000)
            runCurrent()
            assertEquals(615_000L, currentTime)
        }
}
