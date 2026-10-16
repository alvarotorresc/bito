package com.alvarotc.bito.sesion.sonido

import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class SintesisAireTest {
    private val muestreo = SintesisAire.MUESTREO

    private fun sintesis(volumen: Float = 1f) = SintesisAire().also { it.volumen = volumen }

    private fun SintesisAire.correr(ms: Int): FloatArray = FloatArray(ms * muestreo / 1000).also { generar(it) }

    private fun pico(x: FloatArray) = x.maxOf { abs(it) }

    /** Avanza muestra a muestra hasta una cresta (el peor momento para un cambio) y la devuelve. */
    private fun SintesisAire.hastaCresta(): Float {
        val referencia = pico(correr(100))
        val una = FloatArray(1)
        do generar(una) while (abs(una[0]) < 0.98f * referencia)
        return una[0]
    }

    /** El mayor salto entre muestras, contando desde [antes]. */
    private fun mayorSalto(
        antes: Float,
        x: FloatArray,
    ): Float {
        var salto = abs(x[0] - antes)
        for (i in 1 until x.size) salto = maxOf(salto, abs(x[i] - x[i - 1]))
        return salto
    }

    private fun rms(x: FloatArray) = sqrt(x.sumOf { (it * it).toDouble() } / x.size)

    /** Cruces por cero hacia arriba en un segundo de señal = hercios. */
    private fun hercios(x: FloatArray): Int = (1 until x.size).count { x[it - 1] < 0f && x[it] >= 0f } * muestreo / x.size

    @Test
    fun `the tone rises a fifth with the lungs`() {
        listOf(0f to 196, 0.5f to 245, 1f to 294).forEach { (llenado, esperado) ->
            val s = sintesis()
            s.fijar(llenado, BreathPhase.INHALE)
            s.correr(1000)
            val f = hercios(s.correr(1000))
            assertTrue("llenado $llenado: $f Hz", abs(f - esperado) <= 1)
        }
    }

    @Test
    fun `tone gain is 0_045 breathing in and out and 0_03 holding`() {
        val picos =
            BreathPhase.entries.associateWith { fase ->
                val s = sintesis()
                s.fijar(0.5f, fase)
                s.correr(1000)
                pico(s.correr(1000))
            }
        val dentro = picos.getValue(BreathPhase.INHALE)
        // El paso bajo de 900 Hz realza un poco (≈ +4 %) a 245 Hz.
        assertTrue("dentro $dentro", dentro in 0.045f..0.05f)
        assertEquals(dentro, picos.getValue(BreathPhase.EXHALE), 1e-4f)
        assertEquals(0.03f / 0.045f, picos.getValue(BreathPhase.HOLD) / dentro, 0.01f)
    }

    /** Recorre un ciclo entero con el reloj del ritmo, como el hilo de audio, y mide el mayor salto entre muestras. */
    private fun mayorSalto(
        modo: BreathingMode,
        musica: Boolean,
    ): Pair<Float, SintesisAire> {
        val s = sintesis()
        s.respirar(modo, ciclos = 1, musica = musica)
        val bloque = FloatArray(SintesisAire.BLOQUE)
        var anterior = 0f
        var salto = 0f
        var ms = 0L
        while (!s.callado && ms < 60_000L) {
            s.llenar(bloque, ms)
            bloque.forEach {
                salto = maxOf(salto, abs(it - anterior))
                anterior = it
            }
            ms += 10
        }
        return salto to s
    }

    @Test
    fun `the tone never jumps`() {
        // Pendiente máxima del seno a 294 Hz y 0,047: 2π·294/44100·0,047 ≈ 0,002 por muestra. El paso
        // de 0,045 a 0,03 al mantener, sin suavizar, saltaría hasta 0,015.
        listOf(BreathingMode.CALM, BreathingMode.FOCUS).forEach { modo ->
            val (salto, s) = mayorSalto(modo, musica = false)
            assertTrue("$modo: salto $salto", salto < 0.003f)
            assertTrue("$modo acaba solo y callado", s.callado)
        }
    }

    @Test
    fun `the tone never jumps when the phase changes on a crest`() {
        val s = sintesis()
        s.fijar(1f, BreathPhase.INHALE)
        s.correr(1000)
        val antes = s.hastaCresta()
        // De 0,045 a 0,03 con τ 80 ms (el paso bajo, detrás de la ganancia como en el mock, también lo suaviza).
        s.fijar(1f, BreathPhase.HOLD)
        val salto = mayorSalto(antes, s.correr(80))
        assertEquals(0.03f + 0.015f * exp(-1f), s.gananciaGuia, 0.0005f)
        assertTrue("salto $salto", salto < 0.003f)
    }

    @Test
    fun `the pitch glides with a 50 ms time constant`() {
        val s = sintesis()
        s.fijar(0f, BreathPhase.INHALE)
        s.correr(500)
        s.fijar(1f, BreathPhase.INHALE)
        s.correr(50)
        assertEquals(294f - 98f * exp(-1f), s.frecuenciaGuia, 1f)
    }

    @Test
    fun `the tone never jumps with music either`() {
        listOf(BreathingMode.CALM, BreathingMode.FOCUS).forEach { modo ->
            val (salto, _) = mayorSalto(modo, musica = true)
            assertTrue("$modo: salto $salto", salto < 0.008f)
        }
    }

    @Test
    fun `stopping reaches zero without a click`() {
        val s = sintesis()
        s.fijar(1f, BreathPhase.INHALE)
        s.correr(1000)
        val antes = s.hastaCresta()
        s.parar()
        val despues = s.correr(2000)
        val salto = mayorSalto(antes, despues)
        assertTrue("salto $salto", salto < 0.003f)
        assertTrue(pico(despues.copyOfRange(despues.size - 441, despues.size)) < 1e-4f)
        assertTrue(s.callado)
    }

    @Test
    fun `stopping fades the tone in 150 ms and the music in 250 ms`() {
        val s = sintesis()
        s.respirar(BreathingMode.CALM, ciclos = 1, musica = true)
        s.fijar(1f, BreathPhase.INHALE)
        s.correr(1000)
        val tono = s.gananciaGuia
        val musica = s.gananciaMusica
        s.parar()
        s.correr(150)
        assertEquals(tono * exp(-1f), s.gananciaGuia, tono * 0.03f)
        s.correr(100)
        assertEquals(musica * exp(-1f), s.gananciaMusica, musica * 0.03f)
    }

    @Test
    fun `the music follows the lungs in volume and brightness`() {
        val s = sintesis()
        s.respirar(BreathingMode.CALM, ciclos = 1, musica = true)
        s.fijar(0f, BreathPhase.INHALE)
        s.correr(1000)
        assertEquals(0.02f, s.gananciaMusica, 1e-4f)
        assertEquals(380f, s.corteMusica, 0.5f)
        s.fijar(1f, BreathPhase.INHALE)
        s.correr(1000)
        assertEquals(0.08f, s.gananciaMusica, 1e-4f)
        assertEquals(1880f, s.corteMusica, 0.5f)
        val conMusica = rms(s.correr(1000))

        val sinMusica = sintesis()
        sinMusica.fijar(1f, BreathPhase.INHALE)
        sinMusica.correr(1000)
        assertTrue(conMusica > 1.5 * rms(sinMusica.correr(1000)))

        s.musica(false)
        s.correr(1000)
        assertEquals(0f, s.gananciaMusica, 1e-4f)
    }

    @Test
    fun `channel at zero is silence`() {
        val s = sintesis(volumen = 0f)
        s.respirar(BreathingMode.CALM, ciclos = 1, musica = true)
        s.fijar(1f, BreathPhase.INHALE)
        assertTrue(s.correr(1000).all { it == 0f })
    }

    @Test
    fun `the volume never jumps either`() {
        val s = sintesis()
        s.fijar(1f, BreathPhase.INHALE)
        s.correr(1000)
        val antes = s.hastaCresta()
        s.volumen = 0f
        val x = s.correr(1000)
        val salto = mayorSalto(antes, x)
        assertTrue("salto $salto", salto < 0.003f)
        assertTrue(pico(x.copyOfRange(x.size - 441, x.size)) < 1e-3f)
    }
}
