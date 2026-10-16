package com.alvarotc.bito.sesion.fuera

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.INICIO_PARED
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@Suppress("DEPRECATION")
class SesionSyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var b: BancoFuera
    private val alarmas get() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    @Before
    fun setUp() {
        b = BancoFuera(context, tmp.root)
        shadowOf(b.manager).setNotificationsEnabled(true)
        NotificationChannels.ensure(context)
    }

    @After
    fun tearDown() = b.cerrar()

    private fun aviso(): Notification? = shadowOf(b.manager).getNotification(Notifier.FOCUS_ID)

    private fun recuperar(ahora: Ahora) =
        runBlocking {
            b.ahora = ahora
            SesionSync.recuperar(b.entorno)
        }

    private fun guardar(r: com.alvarotc.bito.domain.sesion.RegistroSesion) = runBlocking { b.store.guardar(r) }

    @Test
    fun `sync after process death schedules only the next boundary`() {
        val r = registroDeDosBloques()
        guardar(r)
        // La alarma vieja (la de las 25 min) sigue ahí; la app murió y el descanso acabó mientras tanto.
        AlarmaSesion.programar(context, r, ahoraA(0))

        recuperar(ahoraA(32 * MIN))

        assertEquals(1, alarmas.size)
        assertEquals(ANCLA_ELAPSED + 55 * MIN, alarmas.single().triggerAtTime)
        assertEquals(context.getString(R.string.ses_notif_en_marcha), aviso()!!.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(1, shadowOf(b.manager).size())
        // Recuperar no suena: nada se repite.
        assertTrue(b.sonido.sonados.isEmpty())
        assertTrue(b.tacto.hapticas.isEmpty())
        assertTrue(b.fondo.llamadas.isEmpty())
    }

    @Test
    fun `after a reboot a live session is re-armed and an ended one notifies`() {
        val r = registroDeDosBloques()
        guardar(r)
        // Reinicio: otro contador de arranques, elapsed casi a cero; manda el reloj de pared.
        val tras10 = Ahora(INICIO_PARED + 10 * MIN, 120_000L, arranques = 2)
        recuperar(tras10)
        assertEquals(120_000L + 15 * MIN, alarmas.single().triggerAtTime)
        assertEquals(INICIO_PARED + 25 * MIN, aviso()!!.`when`)

        b.manager.cancelAll()
        shadowOf(b.manager).setNotificationPolicyAccessGranted(true)
        b.manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        guardar(r.copy(filtroPrevio = NotificationManager.INTERRUPTION_FILTER_ALL))
        recuperar(Ahora(INICIO_PARED + 90 * MIN, 120_000L, arranques = 2))

        assertEquals(NotificationChannels.HABI, aviso()!!.channelId)
        assertEquals(context.getString(R.string.ses_notif_fin), aviso()!!.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, b.manager.currentInterruptionFilter)
        assertTrue(alarmas.isEmpty())

        // Otra recuperación con la misma sesión terminada no vuelve a avisar.
        b.manager.cancelAll()
        recuperar(Ahora(INICIO_PARED + 91 * MIN, 180_000L, arranques = 2))
        assertNull(aviso())
    }

    @Test
    fun `with the app in front an ended session posts nothing`() {
        guardar(registroDeDosBloques())
        b.visible = true
        recuperar(ahoraA(56 * MIN))
        assertNull(aviso())
    }

    @Test
    fun `without a session recovering clears a stray notice and alarm`() {
        AlarmaSesion.programar(context, registroDeDosBloques(), ahoraA(0))
        PresenciaSesion.mostrar(context, registroDeDosBloques(), ahoraA(0), "Leer", com.alvarotc.bito.domain.model.Personality.NEUTRA)
        recuperar(ahoraA(1_000))
        assertNull(aviso())
        assertTrue(alarmas.isEmpty())
    }

    @Test
    fun `fuera android publishes on change and cancels on close`() {
        val fuera = FueraAndroid(b.entorno)
        val r = registroDeDosBloques()
        runBlocking {
            b.ahora = ahoraA(0)
            fuera.alCambiar(r, sonando = false)
        }
        assertEquals("Leer", aviso()!!.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(1, alarmas.size)

        runBlocking { fuera.alCambiar(null, sonando = false) }
        assertNull(aviso())
        assertTrue(alarmas.isEmpty())
    }

    @Test
    fun `fuera android with the app in front and the session over only clears the notice`() {
        val fuera = FueraAndroid(b.entorno)
        val r = registroDeDosBloques()
        runBlocking {
            b.ahora = ahoraA(0)
            fuera.alCambiar(r, sonando = false)
            b.visible = true
            b.ahora = ahoraA(56 * MIN)
            fuera.alCambiar(r, sonando = false)
        }
        assertNull(aviso())
        assertTrue(alarmas.isEmpty())
    }
}
