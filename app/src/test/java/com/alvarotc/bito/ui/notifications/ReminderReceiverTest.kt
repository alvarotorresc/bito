package com.alvarotc.bito.ui.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.sesion.fuera.BancoFuera
import com.alvarotc.bito.sesion.fuera.MIN
import com.alvarotc.bito.sesion.fuera.PuertaFuera
import com.alvarotc.bito.sesion.fuera.ahoraA
import com.alvarotc.bito.sesion.fuera.registroDeDosBloques
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Con una sesión viva los avisos de Bito esperan (decisión 8); sin ella salen como siempre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderReceiverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var b: BancoFuera
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        AppStartup.quiesceForTests()
        b = BancoFuera(context, tmp.root)
        shadowOf(b.manager).setNotificationsEnabled(true)
        PuertaFuera.entorno = { b.entorno }
        container = AppContainer(context)
        runBlocking { container.settings.update { it.copy(breathingReminderEnabled = true) } }
    }

    @After
    fun tearDown() {
        PuertaFuera.entorno = null
        b.cerrar()
        AppStartup.quiesceForTests()
    }

    private fun suena() =
        runBlocking {
            ReminderReceiver.procesar(context, container, "BREATHING", "", retener = true)
        }

    @Test
    fun `a reminder during a live session is held and the chain keeps going`() {
        runBlocking { b.store.guardar(registroDeDosBloques()) }
        b.ahora = ahoraA(10 * MIN)
        suena()
        assertNull(shadowOf(b.manager).getNotification(Notifier.BREATHING_ID))
        assertEquals(1, shadowOf(context.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms.size)

        b.ahora = ahoraA(56 * MIN)
        runBlocking { b.retenidos.soltar() }
        assertEquals(listOf("BREATHING" to ""), b.publicados)
    }

    @Test
    fun `without a session the reminder posts as always`() {
        suena()
        assertNotNull(shadowOf(b.manager).getNotification(Notifier.BREATHING_ID))
        assertEquals(emptyList<Pair<String, String>>(), b.publicados)
    }
}
