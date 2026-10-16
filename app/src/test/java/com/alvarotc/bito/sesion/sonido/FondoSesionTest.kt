package com.alvarotc.bito.sesion.sonido

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.ui.sistema.sonido.AjustesSonido
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoAndroid
import com.alvarotc.bito.ui.sistema.sonido.Reproductor
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

internal class FuenteGrabada(
    private val reloj: () -> Long,
) : FuenteFondo {
    /** (ms de reloj, volumen) en cada paso. */
    val volumenes = mutableListOf<Pair<Long, Float>>()
    var cerrada = false

    val ultimo get() = volumenes.last().second

    override fun volumen(v: Float) {
        volumenes += reloj() to v
    }

    override fun cerrar() {
        cerrada = true
    }
}

internal class SalidaGrabada(
    private val reloj: () -> Long,
) : SalidaFondo {
    val cortos = mutableListOf<Pair<Snd, FuenteGrabada>>()
    val pistas = mutableListOf<Pair<Int, FuenteGrabada>>()

    override fun bucleCorto(snd: Snd): FuenteFondo = FuenteGrabada(reloj).also { cortos += snd to it }

    override fun pista(recurso: Int): FuenteFondo = FuenteGrabada(reloj).also { pistas += recurso to it }
}

internal class JugadorGrabado : PistaEncadenada.Jugador {
    var siguiente: PistaEncadenada.Jugador? = null
    var alAcabar: (() -> Unit)? = null
    var arrancado = false
    var liberado = false
    var volumen = -1f

    override fun volumen(v: Float) {
        volumen = v
    }

    override fun encadenar(siguiente: PistaEncadenada.Jugador) {
        this.siguiente = siguiente
    }

    override fun alAcabar(f: () -> Unit) {
        alAcabar = f
    }

    override fun arrancar() {
        arrancado = true
    }

    override fun liberar() {
        liberado = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FondoSesionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private var ganancia = 1f

    private fun cambiar(cambio: Int) = shadowOf(audio).lastAudioFocusRequest.listener.onAudioFocusChange(cambio)

    private class Montaje(
        val fondo: FondoSesion,
        val salida: SalidaGrabada,
    )

    private fun TestScope.montar(): Montaje {
        val salida = SalidaGrabada { currentTime }
        val fondo =
            FondoSesion(
                salida = salida,
                foco = FocoAudio(PlataformaFocoAndroid(audio)),
                ganancia = { ganancia },
                scope = backgroundScope,
            )
        return Montaje(fondo, salida)
    }

    @Test
    fun `sonar fades in in 400 ms and callar out in 300`() =
        runTest {
            val (fondo, salida) = montar().let { it.fondo to it.salida }
            fondo.sonar(SessionBackground.BROWN)
            advanceTimeBy(400)
            val fuente = salida.cortos.single().second
            // Constante de 400 ms: a los 400 ms lleva el 63 % del camino.
            assertEquals(0.632f, fuente.ultimo, 0.02f)
            assertEquals(SessionBackground.BROWN, fondo.sonando.value)
            advanceTimeBy(4_000)
            assertTrue(fuente.ultimo > 0.99f)

            fondo.callar()
            assertNull(fondo.sonando.value)
            val t0 = currentTime
            advanceTimeBy(300)
            // Constante de 300 ms: a los 300 ms queda el 37 %.
            assertEquals(0.368f, fuente.ultimo, 0.02f)
            assertTrue(currentTime - t0 >= 300)
            advanceTimeBy(5_000)
            assertTrue(fuente.cerrada)
            assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
        }

    @Test
    fun `dry cut is 30 ms`() =
        runTest {
            val m = montar()
            m.fondo.sonar(SessionBackground.BROWN)
            advanceTimeBy(3_000)
            val fuente = m.salida.cortos.single().second
            m.fondo.callar(seco = true)
            advanceTimeBy(48)
            assertTrue("a los 48 ms ya casi nada: ${fuente.ultimo}", fuente.ultimo < 0.25f)
            advanceTimeBy(400)
            assertTrue(fuente.cerrada)
        }

    @Test
    fun `none means silence`() =
        runTest {
            val m = montar()
            m.fondo.sonar(SessionBackground.NONE)
            advanceTimeBy(1_000)
            assertTrue(m.salida.cortos.isEmpty() && m.salida.pistas.isEmpty())
            assertNull(m.fondo.sonando.value)
            assertNull(shadowOf(audio).lastAudioFocusRequest)

            m.fondo.sonar(SessionBackground.CAFE)
            advanceTimeBy(1_000)
            assertEquals(SessionBackground.CAFE, m.fondo.sonando.value)
            m.fondo.sonar(SessionBackground.NONE)
            assertNull(m.fondo.sonando.value)
            advanceTimeBy(5_000)
            assertTrue(m.salida.pistas.single().second.cerrada)
        }

    @Test
    fun `going to background does not stop it`() =
        runTest {
            val m = montar()
            m.fondo.sonar(SessionBackground.BROWN)
            advanceTimeBy(2_000)
            val fuente = m.salida.cortos.single().second

            // El motor de sonido de la app sí funde sus bucles al salir de la app.
            val pool = ReproductorGrabado()
            val motor =
                MotorSonidoAndroid(
                    pool,
                    recursoDe = { 1 },
                    ajustes = { AjustesSonido(true, true, true, 6, 6, 10) },
                    timbreNormal = { true },
                    scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            motor.bucle(Snd.FONDO_LLUVIA).empezar()
            motor.segundoPlano()

            advanceTimeBy(2_000)
            assertEquals(SessionBackground.BROWN, m.fondo.sonando.value)
            assertFalse(fuente.cerrada)
            assertTrue(fuente.ultimo > 0.99f)
        }

    @Test
    fun `temporary loss fades and returns, permanent stops and clears sonando`() =
        runTest {
            val m = montar()
            m.fondo.sonar(SessionBackground.BROWN)
            advanceTimeBy(4_000)
            val fuente = m.salida.cortos.single().second

            cambiar(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            advanceTimeBy(150)
            assertTrue("sin salto: ${fuente.ultimo}", fuente.ultimo in 0.2f..0.5f)
            advanceTimeBy(1_500)
            assertTrue(fuente.ultimo < 0.01f)
            assertEquals(SessionBackground.BROWN, m.fondo.sonando.value)

            cambiar(AudioManager.AUDIOFOCUS_GAIN)
            advanceTimeBy(1_500)
            assertTrue(fuente.ultimo > 0.99f)

            cambiar(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            advanceTimeBy(1_500)
            assertEquals(0.3f, fuente.ultimo, 0.01f)

            cambiar(AudioManager.AUDIOFOCUS_LOSS)
            assertNull(m.fondo.sonando.value)
            advanceTimeBy(3_000)
            assertTrue(fuente.cerrada)
            assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
        }

    @Test
    fun `the sample lasts 2200 ms`() =
        runTest {
            val m = montar()
            m.fondo.muestra(SessionBackground.BROWN)
            advanceTimeBy(2_199)
            val fuente = m.salida.cortos.single().second
            assertEquals(SessionBackground.BROWN, m.fondo.sonando.value)
            val antes = fuente.ultimo
            assertTrue(antes > 0.9f)
            advanceTimeBy(1)
            advanceTimeBy(150)
            // Desde los 2200 ms se funde (constante de 300 ms), y deja de contar como sonando.
            assertNull(m.fondo.sonando.value)
            assertTrue(fuente.ultimo < antes)
            advanceTimeBy(5_000)
            assertTrue(fuente.cerrada)
            val primeraBaja = fuente.volumenes.first { it.first >= 2_000 && it.second < antes - 0.01f }.first
            assertTrue("empezó a fundirse en $primeraBaja", primeraBaja in 2_200L..2_260L)
        }

    @Test
    fun `the sample does not interrupt a session background`() =
        runTest {
            val m = montar()
            m.fondo.sonar(SessionBackground.RAIN)
            advanceTimeBy(1_000)
            m.fondo.muestra(SessionBackground.CAFE)
            advanceTimeBy(5_000)
            assertEquals(1, m.salida.pistas.size)
            assertEquals(SessionBackground.RAIN, m.fondo.sonando.value)
        }

    @Test
    fun `with a track two players are chained`() {
        val jugadores = mutableListOf<JugadorGrabado>()
        val pista = PistaEncadenada.abrir { JugadorGrabado().also { jugadores += it } }!!
        assertEquals(2, jugadores.size)
        val (a, b) = jugadores
        assertSame(b, a.siguiente)
        assertTrue(a.arrancado)
        assertFalse(b.arrancado)
        pista.volumen(0.5f)
        assertEquals(0.5f, a.volumen, 0f)
        assertEquals(0.5f, b.volumen, 0f)

        // Al acabar el primero, el segundo ya suena: se prepara un tercero y se encadena al segundo.
        a.alAcabar!!.invoke()
        assertEquals(3, jugadores.size)
        val c = jugadores[2]
        assertSame(c, b.siguiente)
        assertEquals(0.5f, c.volumen, 0f)
        assertTrue(a.liberado)
        assertFalse(b.liberado)
        assertNotNull(b.alAcabar)

        pista.cerrar()
        assertTrue(b.liberado && c.liberado)
    }

    @Test
    fun `tracks are real for rain and cafe, brown uses the short loop`() =
        runTest {
            assertEquals(R.raw.fondo_lluvia, PISTAS.getValue(SessionBackground.RAIN))
            assertEquals(R.raw.fondo_cafe, PISTAS.getValue(SessionBackground.CAFE))
            assertNull(PISTAS.getValue(SessionBackground.BROWN))
            val m = montar()
            m.fondo.sonar(SessionBackground.RAIN)
            advanceTimeBy(100)
            assertEquals(R.raw.fondo_lluvia, m.salida.pistas.single().first)
            m.fondo.sonar(SessionBackground.BROWN)
            advanceTimeBy(100)
            assertEquals(Snd.FONDO_MARRON, m.salida.cortos.single().first)
            assertTrue(m.salida.pistas.single().second.cerrada)
        }

    @Test
    fun `silent bito does not take audio focus`() =
        runTest {
            ganancia = 0f
            val m = montar()
            m.fondo.sonar(SessionBackground.RAIN)
            advanceTimeBy(2_000)
            assertNull(shadowOf(audio).lastAudioFocusRequest)
            assertNull(m.fondo.sonando.value)
            assertTrue(m.salida.pistas.isEmpty())

            // Si sube el volumen durante la sesión, entonces sí.
            ganancia = 1f
            advanceTimeBy(1_000)
            assertNotNull(shadowOf(audio).lastAudioFocusRequest)
            assertEquals(SessionBackground.RAIN, m.fondo.sonando.value)
        }
}

internal class ReproductorGrabado : Reproductor {
    val parados = mutableListOf<Int>()
    val volumenes = mutableListOf<Float>()

    override fun cargar(recurso: Int) = 1

    override fun listo(id: Int) = true

    override fun play(
        id: Int,
        volumen: Float,
        bucle: Boolean,
    ) = 7

    override fun volumen(
        stream: Int,
        volumen: Float,
    ) {
        volumenes += volumen
    }

    override fun parar(stream: Int) {
        parados += stream
    }
}
