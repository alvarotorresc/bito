package com.alvarotc.bito.sesion.fuera

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Evento
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PresenciaSesionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(manager).setNotificationsEnabled(true)
        NotificationChannels.ensure(context)
    }

    private fun aviso(): Notification = shadowOf(manager).getNotification(Notifier.FOCUS_ID)

    private fun texto(n: Notification) = n.extras.getString(Notification.EXTRA_TEXT)

    @Test
    fun `the ongoing notice counts down with the system chronometer and is public`() {
        val r = registroDeDosBloques()
        val ahora = ahoraA(10 * MIN)
        PresenciaSesion.mostrar(context, r, ahora, "Leer", Personality.NEUTRA)

        val n = aviso()
        assertEquals("Leer", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(context.getString(R.string.ses_notif_en_marcha), texto(n))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL == 0)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(RelojSesion.paredDe(r, 25 * MIN, ahora), n.`when`)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, n.visibility)
        assertEquals(NotificationChannels.SESION, n.channelId)
        assertNull(n.sound)
        assertNull(n.vibrate)
        assertNotNull(n.getLargeIcon())
    }

    @Test
    fun `the break says descanso and a free session is called solo foco`() {
        PresenciaSesion.mostrar(context, registroDeDosBloques(), ahoraA(26 * MIN), "Leer", Personality.NEUTRA)
        assertEquals(context.getString(R.string.ses_notif_descanso), texto(aviso()))
        assertTrue(aviso().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))

        PresenciaSesion.mostrar(
            context,
            registroDeDosBloques(objetivo = Objetivo.Libre),
            ahoraA(0),
            context.getString(R.string.ses_solo_foco),
            Personality.NEUTRA,
        )
        assertEquals(context.getString(R.string.ses_solo_foco), aviso().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `paused shows en pausa without chronometer`() {
        val r = registroDeDosBloques(eventos = listOf(Evento.Pausa(10 * MIN)))
        PresenciaSesion.mostrar(context, r, ahoraA(20 * MIN), "Leer", Personality.NEUTRA)

        val n = aviso()
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(texto(n)!!.startsWith(context.getString(R.string.ses_notif_en_pausa)))
        assertTrue(texto(n)!!.contains("15:00"))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `the notice has no actions and opens the session`() {
        PresenciaSesion.mostrar(context, registroDeDosBloques(), ahoraA(0), "Leer", Personality.NEUTRA)

        val n = aviso()
        assertTrue(n.actions == null || n.actions.isEmpty())
        val abre = shadowOf(n.contentIntent).savedIntent
        assertEquals("focus", abre.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
    }

    @Test
    fun `the session channel is low and silent`() {
        val canal = manager.getNotificationChannel(NotificationChannels.SESION)
        assertEquals(NotificationManager.IMPORTANCE_LOW, canal.importance)
        assertNull(canal.sound)
        assertFalse(canal.shouldVibrate())
        assertEquals(context.getString(R.string.ses_canal), canal.name.toString())
    }

    @Test
    fun `time is up goes by the habi channel in the same id and is not ongoing`() {
        PresenciaSesion.mostrar(context, registroDeDosBloques(), ahoraA(0), "Leer", Personality.NEUTRA)
        PresenciaSesion.mostrarFin(context, "Leer", Personality.NEUTRA)

        val n = aviso()
        assertEquals(NotificationChannels.HABI, n.channelId)
        assertEquals(context.getString(R.string.ses_notif_fin), texto(n))
        assertEquals("Leer", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT == 0)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(1, shadowOf(manager).size())
    }
}
