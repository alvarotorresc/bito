package com.alvarotc.bito.ui.sistema.sonido

import com.alvarotc.bito.data.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MotorSonidoTest {
    private class Falso : Reproductor {
        val cargados = mutableSetOf<Int>()
        val tocados = mutableListOf<Pair<Int, Float>>()
        var siguiente = 1

        override fun cargar(recurso: Int): Int = siguiente++

        override fun listo(id: Int): Boolean = id in cargados

        override fun play(
            id: Int,
            volumen: Float,
            bucle: Boolean,
        ): Int {
            tocados += id to volumen
            return tocados.size
        }

        override fun volumen(
            stream: Int,
            volumen: Float,
        ) = Unit

        override fun parar(stream: Int) = Unit
    }

    private fun motor(
        falso: Falso,
        ajustes: AjustesSonido,
        timbre: Boolean = true,
    ) = MotorSonidoAndroid(falso, recursoDe = { it.ordinal + 100 }, ajustes = { ajustes }, timbreNormal = { timbre }, scope = TestScope())

    @Test
    fun `a sound requested before its sample loads is dropped without waiting`() {
        val falso = Falso()
        motor(falso, AjustesSonido.de(Settings())).play(Snd.BROTE)
        assertTrue(falso.tocados.isEmpty())
    }

    @Test
    fun `a loaded sound plays at its channel gain times the intensity`() {
        val falso = Falso()
        val m = motor(falso, AjustesSonido.de(Settings()))
        falso.cargados += (1..Snd.entries.size)
        m.play(Snd.MONEDA, 0.6f)
        assertEquals(Ganancia.deNivel(7) * 0.6f, falso.tocados.single().second, 0.0001f)
    }

    @Test
    fun `probar previews a channel at the dragged level, ignoring the stored one`() {
        val falso = Falso()
        val m = motor(falso, AjustesSonido.de(Settings(volEffects = 0)))
        falso.cargados += (1..Snd.entries.size)
        m.probar(Canal.EFECTOS, 10)
        assertEquals(1f, falso.tocados.single().second, 0f)
    }
}
