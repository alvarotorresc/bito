package com.alvarotc.bito.sesion.fuera

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AvisosRetenidosTest {
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
        runBlocking { b.store.guardar(registroDeDosBloques()) }
    }

    @After
    fun tearDown() = b.cerrar()

    private fun en(ms: Long) =
        runBlocking {
            b.ahora = ahoraA(ms)
            fuera.alCambiar(registroDeDosBloques(), sonando = false)
        }

    @Test
    fun `a reminder during a session is held and posted when the timed part ends, not after the answer`() {
        b.visible = true
        b.ahora = ahoraA(10 * MIN)
        assertTrue(runBlocking { b.retenidos.retenerSiHaySesion("TASKS", "") })
        assertTrue(b.publicados.isEmpty())

        en(30 * MIN) // el descanso: todavía hay sesión
        assertTrue(b.publicados.isEmpty())

        en(56 * MIN) // Terminada: acaba la parte con tiempo, aún sin respuesta
        assertEquals(listOf("TASKS" to ""), b.publicados)

        // Responder y cerrar no lo vuelve a sacar.
        runBlocking { fuera.alCambiar(null, sonando = false) }
        assertEquals(1, b.publicados.size)
    }

    @Test
    fun `the held notices come out once when the session is closed without passing the end`() {
        b.ahora = ahoraA(10 * MIN)
        runBlocking {
            b.retenidos.retenerSiHaySesion("BREATHING", "")
            fuera.alCambiar(null, sonando = false)
            fuera.alCambiar(null, sonando = false)
        }
        assertEquals(listOf("BREATHING" to ""), b.publicados)
    }

    @Test
    fun `without a live session nothing is held`() {
        b.ahora = ahoraA(56 * MIN)
        assertFalse(runBlocking { b.retenidos.retenerSiHaySesion("TASKS", "") })
        runBlocking { b.store.borrar() }
        b.ahora = ahoraA(1_000)
        assertFalse(runBlocking { b.retenidos.retenerSiHaySesion("TASKS", "") })
    }

    @Test
    fun `a held notice from another day is dropped`() {
        b.ahora = ahoraA(10 * MIN)
        runBlocking { b.retenidos.retenerSiHaySesion("TASKS", "") }
        b.dia += 1
        en(56 * MIN)
        assertTrue(b.publicados.isEmpty())
    }

    @Test
    fun `time is up is never held`() {
        b.ahora = ahoraA(10 * MIN)
        runBlocking { b.retenidos.retenerSiHaySesion("TASKS", "") }
        en(56 * MIN)
        val n = shadowOf(b.manager).getNotification(Notifier.FOCUS_ID)
        assertEquals(NotificationChannels.HABI, n.channelId)
        assertEquals(context.getString(R.string.ses_notif_fin), n.extras.getString(Notification.EXTRA_TEXT))
    }
}
