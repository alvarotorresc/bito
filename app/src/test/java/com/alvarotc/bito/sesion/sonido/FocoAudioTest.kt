package com.alvarotc.bito.sesion.sonido

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.BreathingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocoAudioTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val reloj = AtomicLong(1_000_000L)
    private val foco = FocoAudio(PlataformaFocoAndroid(audio))

    private fun cambiar(cambio: Int) = shadowOf(audio).lastAudioFocusRequest.listener.onAudioFocusChange(cambio)

    private fun usuario(alPerder: () -> Unit = {}) = FocoAudio.Usuario { alPerder() }

    private fun motor(salida: SalidaFalsa) =
        MotorAire(
            audio = audio,
            foco = foco,
            tacto = TactoFalso(),
            ganancia = { 1f },
            salida = { salida },
            reloj = { reloj.get() },
            lanzar = { it.run() },
        )

    @Test
    fun `asks for gain as music and wants to hear about ducking`() {
        assertTrue(foco.tomar(usuario()))
        val pedido = shadowOf(audio).lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, pedido.focusGain)
        assertEquals(AudioAttributes.USAGE_MEDIA, pedido.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, pedido.audioAttributes.contentType)
        assertTrue(pedido.willPauseWhenDucked())
        assertEquals(1f, foco.factor, 0f)
    }

    @Test
    fun `temporary loss mutes and comes back, duck is 30 percent`() {
        foco.tomar(usuario())
        cambiar(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(0f, foco.factor, 0f)
        cambiar(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1f, foco.factor, 0f)
        cambiar(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertEquals(0.3f, foco.factor, 0f)
    }

    @Test
    fun `permanent loss tells every user and lets go`() {
        var perdidos = 0
        foco.tomar(usuario { perdidos++ })
        foco.tomar(usuario { perdidos++ })
        cambiar(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(2, perdidos)
        assertEquals(0f, foco.factor, 0f)
        assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `one request for the whole process, let go with the last user`() {
        val aire = usuario()
        val fondo = usuario()
        foco.tomar(aire)
        val primero = shadowOf(audio).lastAudioFocusRequest
        foco.tomar(fondo)
        assertTrue(primero === shadowOf(audio).lastAudioFocusRequest)
        foco.soltar(aire)
        assertEquals(null, shadowOf(audio).lastAbandonedAudioFocusRequest)
        foco.soltar(fondo)
        assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `refused focus does not sound`() {
        shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val salida = SalidaFalsa(reloj)
        val m = motor(salida)
        m.respirar(BreathingMode.CALM, anclaElapsed = reloj.get(), ciclos = 1, musica = false)
        assertTrue(salida.bloques.isEmpty())
        assertFalse(m.sonando.value)
    }

    @Test
    fun `temporary loss fades the breathing to zero and it comes back`() {
        val salida =
            SalidaFalsa(reloj) {
                if (it == 300) cambiar(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                if (it == 600) cambiar(AudioManager.AUDIOFOCUS_GAIN)
            }
        motor(salida).respirar(BreathingMode.SLEEP, anclaElapsed = reloj.get(), ciclos = 1, musica = false)
        // Sin salto al perderlo, callado al rato, y vuelve: el reloj no se ha parado.
        assertTrue(salida.pico(301, 303) > 0.01f)
        assertTrue(salida.pico(450, 600) < 1e-3f)
        assertTrue(salida.pico(700, 800) > 0.02f)
        assertTrue(salida.bloques.size in 1900..2200)
    }

    @Test
    fun `permanent loss stops the breathing`() {
        lateinit var m: MotorAire
        var sonabaAlPerder = true
        val salida =
            SalidaFalsa(reloj) {
                if (it == 300) {
                    cambiar(AudioManager.AUDIOFOCUS_LOSS)
                    sonabaAlPerder = m.sonando.value
                }
            }
        m = motor(salida)
        m.respirar(BreathingMode.CALM, anclaElapsed = reloj.get(), ciclos = 6, musica = false)
        assertFalse(sonabaAlPerder)
        assertTrue(salida.cerrada)
        assertTrue(salida.bloques.size < 500)
    }

    @Test
    fun `duck plays the breathing at 30 percent`() {
        val salida = SalidaFalsa(reloj) { if (it == 200) cambiar(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) }
        motor(salida).respirar(BreathingMode.SLEEP, anclaElapsed = reloj.get(), ciclos = 1, musica = false)
        // En la retención (4–11 s) el tono está quieto: comparar antes y después del agachado.
        val agachado = salida.pico(600, 700)
        val entero = SalidaFalsa(reloj)
        motor(entero).respirar(BreathingMode.SLEEP, anclaElapsed = reloj.get(), ciclos = 1, musica = false)
        assertEquals(0.3f, agachado / entero.pico(600, 700), 0.02f)
    }
}
