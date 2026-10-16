package com.alvarotc.bito.ui.premios

import com.alvarotc.bito.domain.model.GardenSpecial
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ColaCelebracionesTest {
    private fun monedas(
        origen: String = "fila:h1",
        n: Int = 1,
    ) = Momento.Monedas(origen, n, n, lote = 1)

    @Test
    fun `G14 order even when they arrive shuffled, one at a time with 360 ms between`() =
        runTest {
            val cola = ColaCelebraciones(backgroundScope)
            cola.encolar(
                listOf(
                    Momento.Etapa(0, false, 1),
                    Momento.Logro("streak-7", 5L, 1),
                    Momento.Veta(TipoVeta.RACHA, 5, racha = 7, lote = 1),
                    Momento.Veta(TipoVeta.DIA, 3, dia = 1, lote = 1),
                    monedas(),
                    Momento.Especial(GardenSpecial.LANTERN, 1),
                ),
            )
            val vistos = mutableListOf<Momento>()
            repeat(6) {
                vistos += cola.actual.value!!
                cola.terminado()
                runCurrent()
                if (it < 5) {
                    assertNull("nunca dos a la vez: hay respiro", cola.actual.value)
                    advanceTimeBy(359)
                    runCurrent()
                    assertNull(cola.actual.value)
                    advanceTimeBy(1)
                    runCurrent()
                }
            }
            assertEquals(listOf(1, 2, 5, 6, 7, 8), vistos.map { it.orden })
        }

    @Test
    fun `leaving Today drops the current one and keeps the rest for later`() =
        runTest {
            val cola = ColaCelebraciones(backgroundScope)
            cola.encolar(listOf(Momento.Veta(TipoVeta.DIA, 3, dia = 1, lote = 1), Momento.Logro("first-habit", 1L, 1)))
            assertEquals(2, cola.actual.value!!.orden)
            cola.pausar() // el ViewModel ya dio por terminada la veta (regla 11)
            assertNull(cola.actual.value)
            assertEquals(1, cola.pendientes.size)
            cola.encolar(listOf(Momento.Veta(TipoVeta.SEMANA, 10, lote = 2)))
            assertNull("en pausa no sale nada", cola.actual.value)
            cola.reanudar()
            assertEquals(3, cola.actual.value!!.orden)
        }

    @Test
    fun `discard removes queued moments but never the current one`() =
        runTest {
            val cola = ColaCelebraciones(backgroundScope)
            cola.encolar(
                listOf(
                    Momento.Veta(TipoVeta.DIA, 3, dia = 5, lote = 1),
                    Momento.Veta(TipoVeta.DIA, 3, dia = 6, lote = 2),
                    Momento.Logro("first-habit", 1L, 3),
                ),
            )
            val quitados = cola.descartar { it is Momento.Veta }
            assertEquals(listOf(6), quitados.map { (it as Momento.Veta).dia })
            assertEquals(2, cola.actual.value!!.orden) // la que suena no se toca
            assertEquals(1, cola.pendientes.size)
        }

    @Test
    fun `a burst of coin moments collapses into one capped flight`() =
        runTest {
            val absorbidas = mutableListOf<Momento.Monedas>()
            val cola = ColaCelebraciones(backgroundScope, alAbsorber = { absorbidas += it })
            cola.encolar(listOf(Momento.Veta(TipoVeta.DIA, 3, dia = 1, lote = 1)))
            cola.encolar((1..8).map { monedas("fila:h$it") }) // llegan mientras suena la veta
            val juntas = cola.pendientes.filterIsInstance<Momento.Monedas>()
            assertEquals(1, juntas.size)
            assertEquals(5, juntas.single().monedas)
            assertEquals(8, juntas.single().bitos)
            assertEquals("fila:h1", juntas.single().origen)
            assertEquals(7, absorbidas.size) // el ViewModel las da por terminadas para su lote
        }

    @Test
    fun `held, it keeps what arrives but lets the coins through`() =
        runTest {
            val cola = ColaCelebraciones(backgroundScope)
            cola.retener = true
            cola.encolar(listOf(Momento.Veta(TipoVeta.DIA, 3, dia = 1, lote = 1), Momento.Logro("first-habit", 1L, 1)))
            assertNull("retenida, la veta no sale", cola.actual.value)
            assertEquals(2, cola.pendientes.size)
            cola.encolar(listOf(monedas()))
            assertEquals(1, cola.actual.value!!.orden) // las monedas de cada marca sí
            cola.terminado()
            advanceTimeBy(400)
            runCurrent()
            assertNull("tras las monedas sigue retenida", cola.actual.value)
            cola.retener = false
            assertEquals(2, cola.actual.value!!.orden)
        }
}
