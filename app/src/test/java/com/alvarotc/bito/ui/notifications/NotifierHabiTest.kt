package com.alvarotc.bito.ui.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.MainActivity
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Los avisos de Habi tal como llegan a la bandeja: textos, acciones, rutas y canal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotifierHabiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val hora = 14 * 60 + 30

    private fun vigente(situacion: Situacion = Situacion.AYER_SE_ESCAPO) =
        Propuesta(
            id = idPropuesta(situacion, "h1", TODAY),
            texto = TextoHabi(R.string.habla_gordo_aviso, listOf("Leer")),
            acciones =
                listOf(
                    Accion(Respuesta.VALE, TextoHabi(R.string.habla_vale_a_las, listOf(ValorHueco.Hora(hora))), principal = true),
                    Accion(Respuesta.AHORA, TextoHabi(R.string.habla_ahora)),
                    Accion(Respuesta.HOY_NO, TextoHabi(R.string.task_not_today)),
                    Accion(Respuesta.OTRA_HORA, TextoHabi(R.string.habla_otra_hora)),
                ),
            destino = Destino.Habito("h1"),
            hora = hora,
        )

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    private fun Notification.titulo() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.texto() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun `the morning notice has the current proposal actions`() {
        Notifier.showManana(context, vigente(), TextoHabi(R.string.habla_mas_tarde), Personality.NEUTRA, Mood.NORMAL)

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
        assertEquals(NotificationChannels.HABI, n.channelId)
        assertEquals("Later", n.titulo())
        assertEquals("Today's big thing: Leer.", n.texto())
        assertEquals(listOf("OK, at 14:30", "Now", "Not today"), n.actions.map { it.title.toString() })

        val vale = shadowOf(n.actions[0].actionIntent)
        assertTrue(n.actions[0].actionIntent.isBroadcast)
        assertEquals(AvisoHabiReceiver.ACTION_VALE, vale.savedIntent.action)
        assertEquals(vigente().id, vale.savedIntent.getStringExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID))
        assertEquals(hora, vale.savedIntent.getIntExtra(AvisoHabiReceiver.EXTRA_HORA, -1))

        val ahora = shadowOf(n.actions[1].actionIntent)
        assertTrue(n.actions[1].actionIntent.isActivity)
        assertEquals(MainActivity::class.java.name, ahora.savedIntent.component?.className)
        assertEquals("today", ahora.savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))

        val hoyNo = shadowOf(n.actions[2].actionIntent)
        assertEquals(AvisoHabiReceiver.ACTION_HOY_NO, hoyNo.savedIntent.action)
        assertEquals("HOY_NO", hoyNo.savedIntent.getStringExtra(AvisoHabiReceiver.EXTRA_RESPUESTA))

        assertEquals("today", shadowOf(n.contentIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
    }

    @Test
    fun `the morning notice about the big thing opens its sheet`() {
        Notifier.showManana(context, vigente(Situacion.ELEGIR_GORDO), TextoHabi(R.string.habla_mas_tarde), Personality.NEUTRA, Mood.NORMAL)

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
        assertEquals("gordo", shadowOf(n.contentIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
    }

    @Test
    fun `an accepted reminder does not change where the morning notice opens`() {
        Notifier.showManana(context, vigente(Situacion.ELEGIR_GORDO), TextoHabi(R.string.habla_mas_tarde), Personality.NEUTRA, Mood.NORMAL)

        Notifier.showRecordatorioAceptado(context, "a1", 0, "Leer", TextoHabi(R.string.habla_hecho), null, false, Personality.NEUTRA)

        val manana = assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
        assertEquals("gordo", shadowOf(manana.contentIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
        val recordatorio = assertNotNull(shadowOf(manager).getNotification(Notifier.aceptadoId(0)))
        assertEquals("today", shadowOf(recordatorio.contentIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
    }

    @Test
    fun `two morning actions that open the app keep their own route`() {
        val propuesta =
            vigente().copy(
                acciones =
                    listOf(
                        Accion(Respuesta.AHORA, TextoHabi(R.string.habla_ahora)),
                        Accion(Respuesta.ELEGIR_GORDO, TextoHabi(R.string.habla_mas_tarde)),
                    ),
                destino = Destino.Respirar,
            )

        Notifier.showManana(context, propuesta, TextoHabi(R.string.habla_mas_tarde), Personality.NEUTRA, Mood.NORMAL)

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
        val rutas = n.actions.map { shadowOf(it.actionIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE) }
        assertEquals(listOf("breathing", "gordo"), rutas)
    }

    @Test
    fun `the accepted notice takes the place of the morning one quietly`() {
        Notifier.showManana(context, vigente(), TextoHabi(R.string.habla_mas_tarde), Personality.NEUTRA, Mood.NORMAL)

        Notifier.showAceptado(context, TextoHabi(R.string.habla_ahora), TextoHabi(R.string.habla_hecho), Personality.NEUTRA)

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
        assertEquals("Now", n.titulo())
        assertEquals("Done", n.texto())
        assertTrue(n.actions.isNullOrEmpty())
        assertTrue((n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        assertEquals(NotificationChannels.HABI, n.channelId)
    }

    @Test
    fun `the accepted reminder has Done and Later`() {
        val target = QuickTarget("h1", "Leer", amount = 1, isCheck = true)

        Notifier.showRecordatorioAceptado(
            context,
            avisoId = "a1",
            indice = 1,
            nombre = "Leer",
            cuerpo = TextoHabi(R.string.habla_hecho),
            target = target,
            masTarde = true,
            personality = Personality.NEUTRA,
        )

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.aceptadoId(1)))
        assertEquals(9, Notifier.aceptadoId(1))
        assertEquals("Leer", n.titulo())
        assertEquals("Done", n.texto())
        assertEquals(listOf("Done: Leer", "Later"), n.actions.map { it.title.toString() })
        val hecho = shadowOf(n.actions[0].actionIntent).savedIntent
        assertEquals(QuickActionReceiver::class.java.name, hecho.component?.className)
        assertEquals(Notifier.aceptadoId(1), hecho.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, -1))
        val luego = shadowOf(n.actions[1].actionIntent).savedIntent
        assertEquals(AvisoHabiReceiver.ACTION_MAS_TARDE, luego.action)
        assertEquals("a1", luego.getStringExtra(AvisoHabiReceiver.EXTRA_AVISO_ID))
    }

    @Test
    fun `without later the accepted reminder only offers Done`() {
        Notifier.showRecordatorioAceptado(
            context,
            avisoId = "a1",
            indice = 0,
            nombre = "Agua",
            cuerpo = TextoHabi(R.string.habla_hecho),
            target = QuickTarget("h2", "vaso", amount = 1, isCheck = false, hecho = 7, objetivo = 8),
            masTarde = false,
            personality = Personality.NEUTRA,
        )

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.aceptadoId(0)))
        assertEquals(listOf("+1 vaso · 7/8"), n.actions.map { it.title.toString() })
    }

    @Test
    fun `two accepted reminders keep their own later action`() {
        listOf("a1", "a2").forEachIndexed { i, id ->
            Notifier.showRecordatorioAceptado(context, id, i, "Leer", TextoHabi(R.string.habla_hecho), null, true, Personality.NEUTRA)
        }

        val ids =
            (0..1).map { i ->
                val n = assertNotNull(shadowOf(manager).getNotification(Notifier.aceptadoId(i)))
                shadowOf(n.actions.single().actionIntent).savedIntent.getStringExtra(AvisoHabiReceiver.EXTRA_AVISO_ID)
            }
        assertEquals(listOf("a1", "a2"), ids)
    }

    @Test
    fun `the night notice opens the day close`() {
        Notifier.showNoche(context, TextoHabi(R.string.habla_cerrar_dia), TextoHabi(R.string.habla_habi_duerme), Personality.NEUTRA)

        val n = assertNotNull(shadowOf(manager).getNotification(Notifier.NOCHE_ID))
        assertEquals(NotificationChannels.HABI, n.channelId)
        assertEquals("Close the day", n.titulo())
        assertEquals("Habi is asleep. More tomorrow.", n.texto())
        assertEquals(listOf("Close the day", "Tomorrow"), n.actions.map { it.title.toString() })
        val cerrar = shadowOf(n.actions[0].actionIntent)
        assertTrue(n.actions[0].actionIntent.isActivity)
        assertEquals("review", cerrar.savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
        assertEquals(AvisoHabiReceiver.ACTION_MANANA, shadowOf(n.actions[1].actionIntent).savedIntent.action)
        assertEquals("review", shadowOf(n.contentIntent).savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))

        Notifier.cancelNoche(context)
        assertNull(shadowOf(manager).getNotification(Notifier.NOCHE_ID))
    }

    @Test
    fun `the review channel is gone and habi exists`() {
        manager.createNotificationChannel(NotificationChannel("review", "Daily review", NotificationManager.IMPORTANCE_DEFAULT))

        NotificationChannels.ensure(context)

        assertNull(manager.getNotificationChannel("review"))
        val habi = assertNotNull(manager.getNotificationChannel(NotificationChannels.HABI))
        assertEquals("Habi's notices", habi.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, habi.importance)
    }
}
