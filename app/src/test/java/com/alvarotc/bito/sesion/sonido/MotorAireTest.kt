package com.alvarotc.bito.sesion.sonido

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.AjustesSonido
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Un sumidero que no suena: guarda lo escrito y adelanta el reloj 10 ms por bloque, como el `AudioTrack` bloqueante. */
internal class SalidaFalsa(
    private val reloj: AtomicLong,
    private val alEscribir: (bloque: Int) -> Unit = {},
) : SalidaAudio {
    var abierta = false
    var cerrada = false
    val bloques = mutableListOf<FloatArray>()

    override fun abrir() {
        abierta = true
    }

    override fun escribir(muestras: FloatArray) {
        check(bloques.size < 200_000) { "no para nunca" }
        bloques += muestras.copyOf()
        reloj.addAndGet(10)
        alEscribir(bloques.size)
    }

    override fun cerrar() {
        cerrada = true
    }

    fun pico(
        desde: Int,
        hasta: Int,
    ) = bloques.subList(desde, hasta).maxOf { b -> b.maxOf { abs(it) } }
}

internal class TactoFalso : Tacto {
    val hapticas = mutableListOf<Haptica>()

    override fun haptica(h: Haptica) {
        hapticas += h
    }

    override fun probar(nivel: Int) = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotorAireTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val reloj = AtomicLong(1_000_000L)
    private val tacto = TactoFalso()

    @After
    fun tearDown() {
        audio.mode = AudioManager.MODE_NORMAL
    }

    /** El hilo de audio corre en el mismo hilo del test, así que `respirar` vuelve con la respiración entera hecha. */
    private fun motor(salida: SalidaFalsa = SalidaFalsa(reloj)) =
        MotorAire(
            audio = audio,
            foco = FocoAudio(PlataformaFocoAndroid(audio)),
            tacto = tacto,
            ganancia = { 1f },
            salida = { salida },
            reloj = { reloj.get() },
            lanzar = { it.run() },
        )

    @Test
    fun `phases vibrate rise tick low but not the first`() {
        motor().respirar(BreathingMode.SLEEP, anclaElapsed = reloj.get(), ciclos = 2, musica = false)
        // Dormir: dentro, mantén, fuera. La primera entrada no vibra.
        assertEquals(listOf(Haptica.TICK, Haptica.LOW, Haptica.RISE, Haptica.TICK, Haptica.LOW), tacto.hapticas)
    }

    @Test
    fun `no vibration during a call`() {
        audio.mode = AudioManager.MODE_IN_CALL
        motor().respirar(BreathingMode.SLEEP, anclaElapsed = reloj.get(), ciclos = 1, musica = false)
        assertTrue(tacto.hapticas.isEmpty())
    }

    @Test
    fun `ends by itself after its cycles`() {
        val salida = SalidaFalsa(reloj)
        val m = motor(salida)
        m.respirar(BreathingMode.CALM, anclaElapsed = reloj.get(), ciclos = 1, musica = true)
        assertFalse(m.sonando.value)
        assertTrue(salida.abierta && salida.cerrada)
        // Un ciclo de Calmarme son 10 s (1000 bloques) y luego la cola de parar, sin cortar.
        assertTrue(salida.bloques.size in 1000..1300)
        assertTrue(salida.pico(500, 600) > 0.03f)
        assertTrue(salida.pico(salida.bloques.size - 1, salida.bloques.size) < 1e-3f)
    }

    @Test
    fun `it sounds while breathing`() {
        var durante = false
        lateinit var m: MotorAire
        val salida = SalidaFalsa(reloj) { if (it == 100) durante = m.sonando.value }
        m = motor(salida)
        m.respirar(BreathingMode.CALM, anclaElapsed = reloj.get(), ciclos = 1, musica = false)
        assertTrue(durante)
    }

    @Test
    fun `stop fades out and ends`() {
        lateinit var m: MotorAire
        val salida = SalidaFalsa(reloj) { if (it == 300) m.parar() }
        m = motor(salida)
        m.respirar(BreathingMode.CALM, anclaElapsed = reloj.get(), ciclos = 6, musica = true)
        assertFalse(m.sonando.value)
        assertTrue(salida.cerrada)
        // Parar con τ de 150 y 250 ms: la cola dura un par de segundos hasta el silencio y no sigue los 60 s.
        assertTrue(salida.bloques.size in 330..600)
        assertTrue(salida.pico(301, 303) > 0.01f)
    }

    @Test
    fun `the phone on silent does not mute it but the master switch and the channel do`() {
        val base = AjustesSonido(general = true, habi = true, registro = true, volHabi = 6, volEfectos = 6, volAmbiente = 10)
        assertEquals(1f, MotorAire.ganancia(base), 1e-6f)
        assertEquals(0f, MotorAire.ganancia(base.copy(general = false)), 0f)
        assertEquals(0f, MotorAire.ganancia(base.copy(volAmbiente = 0)), 0f)
    }

    @Test
    fun `silent bito does not take audio focus`() {
        val salida = SalidaFalsa(reloj)
        val m =
            MotorAire(
                audio = audio,
                foco = FocoAudio(PlataformaFocoAndroid(audio)),
                tacto = tacto,
                ganancia = { 0f },
                salida = { salida },
                reloj = { reloj.get() },
                lanzar = { it.run() },
            )
        m.respirar(BreathingMode.SLEEP, anclaElapsed = reloj.get(), ciclos = 1, musica = true)
        assertNull(shadowOf(audio).lastAudioFocusRequest)
        // No suena, pero la vibración por fase sigue.
        assertEquals(listOf(Haptica.TICK, Haptica.LOW), tacto.hapticas)
        assertTrue(salida.pico(0, salida.bloques.size) < 1e-6f)
        assertFalse(m.sonando.value)
    }
}
