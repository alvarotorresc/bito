package com.alvarotc.bito.sesion.fuera

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
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
class SesionReceiverTest {
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

    private fun dispara(
        ms: Long,
        tramoEsperado: Int,
    ) = runBlocking {
        b.ahora = ahoraA(ms)
        SesionReceiver.procesar(b.entorno, b.audio, tramoEsperado)
    }

    private fun aviso(): Notification = shadowOf(b.manager).getNotification(Notifier.FOCUS_ID)

    private fun guardar(r: com.alvarotc.bito.domain.sesion.RegistroSesion) = runBlocking { b.store.guardar(r) }

    @Test
    fun `the receiver does nothing with the app in front`() {
        guardar(registroDeDosBloques())
        b.visible = true
        dispara(25 * MIN + 1_000, 0)
        assertEquals(0, shadowOf(b.manager).size())
        assertTrue(b.fondo.llamadas.isEmpty())
        assertTrue(b.sonido.sonados.isEmpty())
        assertTrue(b.tacto.hapticas.isEmpty())
        assertTrue(alarmas.isEmpty())
    }

    @Test
    fun `the receiver advances from the store with the app dead and schedules the next`() {
        guardar(registroDeDosBloques())
        dispara(25 * MIN + 1_000, 0)

        assertEquals(context.getString(R.string.ses_notif_descanso), aviso().extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf(Snd.CAMPANILLA), b.sonido.snds())
        assertEquals(listOf(Haptica.CLICK), b.tacto.hapticas)
        assertEquals(1, alarmas.size)
        assertEquals(ANCLA_ELAPSED + 30 * MIN + 1_000 - 1_000, alarmas.single().triggerAtTime)
    }

    @Test
    fun `with the app out the break silences the background and the next block resumes it`() {
        guardar(registroDeDosBloques())
        dispara(25 * MIN + 1_000, 0)
        assertEquals(listOf("callar"), b.fondo.llamadas)

        dispara(30 * MIN + 1_000, 1)
        assertEquals(listOf("callar", "sonar:${SessionBackground.RAIN.name}"), b.fondo.llamadas)
        assertEquals(listOf(Snd.CAMPANILLA, Snd.BROTE), b.sonido.snds())
        assertEquals(listOf(Haptica.CLICK, Haptica.RISE), b.tacto.hapticas)
        assertEquals(context.getString(R.string.ses_notif_en_marcha), aviso().extras.getString(Notification.EXTRA_TEXT))
        assertEquals(ANCLA_ELAPSED + 55 * MIN, alarmas.single().triggerAtTime)
    }

    @Test
    fun `leaving the breathing stops the tone and starts the background`() {
        guardar(registroDeDosBloques(entrada = Entrada.RESPIRAR))
        dispara(60_000 + 500, 0)
        assertEquals(listOf("parar"), b.aire.llamadas)
        assertEquals(listOf("sonar:${SessionBackground.RAIN.name}"), b.fondo.llamadas)
        assertEquals(listOf(Snd.ALEJAR), b.sonido.snds())
        assertEquals(listOf(Haptica.TICK), b.tacto.hapticas)
    }

    @Test
    fun `at the end it posts time is up on the habi channel and restores dnd`() {
        shadowOf(b.manager).setNotificationPolicyAccessGranted(true)
        b.manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        guardar(registroDeDosBloques(filtroPrevio = NotificationManager.INTERRUPTION_FILTER_ALL))

        dispara(55 * MIN + 1_000, 2)

        val n = aviso()
        assertEquals(NotificationChannels.HABI, n.channelId)
        assertEquals(context.getString(R.string.ses_notif_fin), n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, b.manager.currentInterruptionFilter)
        assertEquals(listOf("callar"), b.fondo.llamadas)
        assertEquals(listOf(Haptica.RISE), b.tacto.hapticas)
        assertTrue(alarmas.isEmpty())
    }

    @Test
    fun `after giving dnd back the saved session no longer carries the previous filter`() {
        shadowOf(b.manager).setNotificationPolicyAccessGranted(true)
        b.manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        guardar(registroDeDosBloques(filtroPrevio = NotificationManager.INTERRUPTION_FILTER_ALL))

        dispara(55 * MIN + 1_000, 2)

        assertNull(runBlocking { b.store.leer() }?.filtroPrevio)
    }

    @Test
    fun `an alarm that arrives twice does not ring twice`() {
        guardar(registroDeDosBloques())
        dispara(55 * MIN + 1_000, 2)
        b.manager.cancelAll()
        dispara(55 * MIN + 2_000, 2)
        assertNull(shadowOf(b.manager).getNotification(Notifier.FOCUS_ID))
        assertEquals(listOf(Haptica.RISE), b.tacto.hapticas)
    }

    @Test
    fun `a stale alarm that fires before the boundary only reschedules`() {
        guardar(registroDeDosBloques())
        dispara(20 * MIN, 0)
        assertTrue(b.sonido.sonados.isEmpty())
        assertEquals(ANCLA_ELAPSED + 25 * MIN, alarmas.single().triggerAtTime)
    }
}
