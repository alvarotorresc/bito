package com.alvarotc.bito.ui.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.sistema.BitoClaro
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "es")
class NotifierVisualTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    private fun payload(count: Int) =
        ReminderPayload(
            pendingNames = List(count) { "h$it" },
            targets = List(count) { QuickTarget("h$it", "h$it", 1, isCheck = true) },
            doneCount = 0,
            totalCount = count,
        )

    @Test
    fun `action labels have no tick and carry the progress`() {
        assertEquals("Hecho: Leer", etiquetaAccion(context, QuickTarget("h", "Leer", 1, isCheck = true)))
        assertEquals(
            "+1 vaso · 7/8",
            etiquetaAccion(context, QuickTarget("a", "vaso", 1, isCheck = false, hecho = 7, objetivo = 8)),
        )
    }

    @Test
    fun `reminders use the deep water accent and a large Habi icon`() {
        Notifier.showReminder(context, payload(1), Personality.NEUTRA, "Álvaro", 12 * 60)

        val notification = shadowOf(manager).getNotification(Notifier.REMINDER_ID)!!
        assertEquals(BitoClaro.aguaHonda.toArgb(), notification.color)
        assertNotNull(notification.getLargeIcon())
    }

    @Test
    fun `the night and the perfect day also carry a large Habi icon`() {
        Notifier.showNoche(context, TextoHabi(R.string.habla_cerrar_dia), TextoHabi(R.string.habla_habi_duerme), Personality.CHEERLEADER)
        Notifier.showPerfectDay(context, "Día perfecto", Personality.SARGENTO)

        assertNotNull(shadowOf(manager).getNotification(Notifier.NOCHE_ID)!!.getLargeIcon())
        assertNotNull(shadowOf(manager).getNotification(Notifier.CELEBRATION_ID)!!.getLargeIcon())
    }

    @Test
    fun `the morning notice and the accepted reminder carry a large Habi icon`() {
        val vigente = Propuesta(id = "SALUDO::1", texto = TextoHabi(R.string.habla_ahora), acciones = emptyList())
        Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), Personality.NEUTRA, Mood.NORMAL)
        Notifier.showRecordatorioAceptado(context, "a1", 0, "Leer", TextoHabi(R.string.habla_hecho), null, false, Personality.NEUTRA)

        assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID)!!.getLargeIcon())
        assertNotNull(shadowOf(manager).getNotification(Notifier.aceptadoId(0))!!.getLargeIcon())
    }

    @Test
    fun `at most three actions`() {
        Notifier.showReminder(context, payload(4), Personality.NEUTRA, "Álvaro", 12 * 60)

        assertEquals(3, shadowOf(manager).getNotification(Notifier.REMINDER_ID)!!.actions.size)
    }
}
