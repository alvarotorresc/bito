package com.alvarotc.bito.sesion.fuera

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ServicioSesionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var b: BancoFuera
    private lateinit var fuera: FueraAndroid

    @Before
    fun setUp() {
        b = BancoFuera(context, tmp.root)
        shadowOf(b.manager).setNotificationsEnabled(true)
        NotificationChannels.ensure(context)
        fuera = FueraAndroid(b.entorno, CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = b.cerrar()

    private fun cambia(
        r: com.alvarotc.bito.domain.sesion.RegistroSesion?,
        ms: Long,
        sonando: Boolean,
    ) = runBlocking {
        b.ahora = ahoraA(ms)
        fuera.alCambiar(r, sonando)
    }

    @Test
    fun `the service starts with sound and the app in front`() {
        b.visible = true
        cambia(registroDeDosBloques(), 0, sonando = true)
        assertEquals(1, b.servicio.arrancados.size)
        assertEquals(NotificationChannels.SESION, b.servicio.arrancados.single().channelId)
        assertEquals("Leer", b.servicio.arrancados.single().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `the service never starts from the background`() {
        b.visible = false
        cambia(registroDeDosBloques(), 0, sonando = true)
        assertTrue(b.servicio.arrancados.isEmpty())
    }

    @Test
    fun `without background there is no service`() {
        b.visible = true
        val sinFondo = registroDeDosBloques(fondo = SessionBackground.NONE)
        cambia(sinFondo, 0, sonando = false)
        cambia(sinFondo, 26 * MIN, sonando = false)
        assertTrue(b.servicio.arrancados.isEmpty())
    }

    @Test
    fun `the service survives breaks while the session has background sound`() {
        b.visible = true
        val r = registroDeDosBloques()
        cambia(r, 0, sonando = true)
        cambia(r, 26 * MIN, sonando = false) // el fondo se calla en el descanso
        assertEquals(0, b.servicio.paradas)
        cambia(r, 31 * MIN, sonando = true) // vuelve el foco con fondo
        assertEquals(1, b.servicio.arrancados.size)
        assertEquals(0, b.servicio.paradas)

        b.visible = false
        cambia(r, 56 * MIN, sonando = false)
        assertEquals(1, b.servicio.paradas)
    }

    @Test
    fun `the service is released when the sound is gone and there is no background`() {
        b.visible = true
        val r = registroDeDosBloques(entrada = Entrada.RESPIRAR, fondo = SessionBackground.NONE)
        cambia(r, 0, sonando = true)
        assertEquals(1, b.servicio.arrancados.size)
        cambia(r, 61 * MIN / 60 + 1_000, sonando = false)
        assertEquals(1, b.servicio.paradas)
    }

    @Test
    fun `closing the session releases the service the notice and the wake lock`() {
        b.visible = true
        val r = registroDeDosBloques(entrada = Entrada.RESPIRAR)
        cambia(r, 0, sonando = true)
        cambia(null, 5_000, sonando = false)
        assertEquals(1, b.servicio.paradas)
        assertEquals(0, shadowOf(b.manager).size())
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun `no receiver starts the service`() {
        b.store.let { runBlocking { it.guardar(registroDeDosBloques()) } }
        runBlocking {
            b.ahora = ahoraA(25 * MIN + 1_000)
            SesionReceiver.procesar(b.entorno, b.audio, 0)
            b.ahora = ahoraA(30 * MIN + 1_000)
            SesionReceiver.procesar(b.entorno, b.audio, 1)
        }
        assertTrue(b.servicio.arrancados.isEmpty())
        assertNull(shadowOf(context as Application).nextStartedService)
    }

    @Test
    fun `screen off with background the break silences it and the next block resumes it with the service kept`() {
        b.visible = true
        val r = registroDeDosBloques()
        cambia(r, 0, sonando = true)
        b.visible = false
        runBlocking { b.store.guardar(r) }
        runBlocking {
            b.ahora = ahoraA(25 * MIN + 1_000)
            SesionReceiver.procesar(b.entorno, b.audio, 0)
            b.ahora = ahoraA(30 * MIN + 1_000)
            SesionReceiver.procesar(b.entorno, b.audio, 1)
        }
        assertEquals(listOf("callar", "sonar:${SessionBackground.RAIN.name}"), b.fondo.llamadas)
        assertEquals(1, b.servicio.arrancados.size)
        assertEquals(0, b.servicio.paradas)
        assertTrue(b.servicio.vivo())
    }

    @Test
    fun `breathing holds a wake lock only for the exercise`() {
        b.visible = true
        val r = registroDeDosBloques(entrada = Entrada.RESPIRAR)
        cambia(r, 10_000, sonando = true)
        val cerrojo = ShadowPowerManager.getLatestWakeLock()
        assertTrue(cerrojo.isHeld)
        // El foco no lo mantiene.
        cambia(r, 61_000, sonando = true)
        assertFalse(cerrojo.isHeld)
    }

    @Test
    fun `screen-off free breathing posts the Respirar notice and releases it`() {
        val fin = android.os.SystemClock.elapsedRealtime() + 60_000L
        fuera.respirando(true, fin)

        val aviso = b.servicio.arrancados.single()
        assertEquals(context.getString(R.string.breathing_title), aviso.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(context.getString(R.string.ses_notif_en_marcha), aviso.extras.getString(Notification.EXTRA_TEXT))
        assertFalse(aviso.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(aviso.actions == null || aviso.actions.isEmpty())
        assertEquals(NotificationChannels.SESION, aviso.channelId)
        assertEquals(fin, b.servicio.finesElapsed.single())
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)

        Notifier.mostrarSesion(context, "x", "y", null, Personality.NEUTRA)
        fuera.respirando(false, null)
        assertEquals(1, b.servicio.paradas)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
        assertEquals(0, shadowOf(b.manager).size())
    }

    @Test
    fun `the service puts the session notice in the foreground as media playback`() {
        val aviso = Notifier.notificacionSesion(context, "Leer", "En marcha", null, Personality.NEUTRA)
        val intent = Intent(context, ServicioSesion::class.java).putExtra(ServicioSesion.EXTRA_AVISO, aviso)
        val controlador = Robolectric.buildService(ServicioSesion::class.java, intent).create()
        controlador.startCommand(0, 1)

        val sombra = shadowOf(controlador.get())
        assertEquals(Notifier.FOCUS_ID, sombra.lastForegroundNotificationId)
        assertNotNull(sombra.lastForegroundNotification)
        assertTrue(ServicioSesion.vivo.get())

        controlador.destroy()
        assertFalse(ServicioSesion.vivo.get())
    }
}
