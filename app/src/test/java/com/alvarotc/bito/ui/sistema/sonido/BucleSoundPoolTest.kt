package com.alvarotc.bito.ui.sistema.sonido

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BucleSoundPoolTest {
    private class Falso : Reproductor {
        var siguiente = 1
        val vivos = mutableSetOf<Int>()

        override fun cargar(recurso: Int) = 1

        override fun listo(id: Int) = true

        override fun play(
            id: Int,
            volumen: Float,
            bucle: Boolean,
        ): Int = siguiente++.also { vivos += it }

        override fun volumen(
            stream: Int,
            volumen: Float,
        ) = Unit

        override fun parar(stream: Int) {
            vivos -= stream
        }
    }

    @Test
    fun `restarting during the fade-out leaves a single stream playing`() =
        runTest {
            val falso = Falso()
            val scope = TestScope(testScheduler)
            val bucle = BucleSoundPool(falso, { 1 }, { 1f }, scope)
            bucle.empezar()
            advanceTimeBy(1_000)
            bucle.parar()
            advanceTimeBy(100)
            bucle.empezar()
            advanceUntilIdle()
            assertEquals(1, falso.vivos.size)
            bucle.parar()
            advanceUntilIdle()
            assertEquals(0, falso.vivos.size)
        }
}
