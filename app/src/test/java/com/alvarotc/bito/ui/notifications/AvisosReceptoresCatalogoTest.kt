package com.alvarotc.bito.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.PlanAvisos
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Los receptores de los avisos de Habi con el cableado de la app: lo que redactan sale del catálogo real del
 * contenedor. Con el catálogo vacío el redactor se queda en el índice 0 de cada bolsa, y el índice 0 de estas pide un
 * hueco que aquí no hay (`{nombre}` sin nombre, `{gordo}` sin lo gordo): el texto saldría vacío.
 *
 * El reloj es el del sistema. El silencio de todo el día adelanta la noche al minuto 0 del día lógico, así que su
 * alarma ya toca a cualquier hora; el «Vale» pide las 23:59, que nunca ha pasado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = BitoApp::class)
class AvisosReceptoresCatalogoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val container get() = (context.applicationContext as BitoApp).container
    private val hoy get() = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())

    @Before
    fun setUp() {
        AppStartup.quiesceForTests()
        NotificationChannels.ensure(context)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        AppStartup.quiesceForTests()
    }

    private fun preparar(voz: Personality) =
        runBlocking {
            container.settings.update {
                it.copy(
                    onboardingDone = true,
                    userName = "",
                    personality = voz,
                    dayCutoffMinutes = 0,
                    dndEnabled = true,
                    dndFromMinutes = 0,
                    dndToMinutes = 23 * 60 + 59,
                )
            }
            container.habits.create(habitEntity(id = "h1", name = "Leer", createdOnDay = hoy - 10))
        }

    @Test
    fun `the night alarm writes its title with the real catalog, also without a name`() {
        preparar(Personality.CHEERLEADER)

        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderScheduler.EXTRA_KIND, SlotKind.HABI_NOCHE.name)
                .putExtra(ReminderScheduler.EXTRA_KEY, PlanAvisos.CLAVE_NOCHE),
        )
        shadowOf(Looper.getMainLooper()).idle()

        eventually { shadowOf(manager).getNotification(Notifier.NOCHE_ID) != null }
        val titulo = shadowOf(manager).getNotification(Notifier.NOCHE_ID).extras.getCharSequence(Notification.EXTRA_TITLE)
        assertTrue("título «$titulo»", !titulo.isNullOrBlank())
    }

    @Test
    fun `vale from the morning notice writes the confirmation body with the real catalog, also without lo gordo`() {
        preparar(Personality.SARGENTO)
        val hora = 23 * 60 + 59
        val vigente =
            PropuestaVigente(
                ambito = Ambito.HOY,
                habitId = "",
                dia = hoy,
                situacion = Situacion.AYER_SE_ESCAPO,
                ref = "h1",
                trozos = listOf(ClaveTrozo(Bolsa.NU_AYER_ESCAPO, Personality.SARGENTO, 0)),
                huecos = mapOf(Hueco.HABITO to ValorHueco.Texto("Leer"), Hueco.HORA to ValorHueco.Hora(hora)),
                acciones = listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO),
                destino = Destino.Habito("h1"),
                hora = hora,
                creadoMillis = System.currentTimeMillis(),
            )
        runBlocking { container.propuestaVigente.guardar(vigente) }

        context.sendBroadcast(
            Intent(context, AvisoHabiReceiver::class.java)
                .setAction(AvisoHabiReceiver.ACTION_VALE)
                .putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, idPropuesta(Situacion.AYER_SE_ESCAPO, "h1", hoy))
                .putExtra(AvisoHabiReceiver.EXTRA_HORA, hora),
        )
        shadowOf(Looper.getMainLooper()).idle()

        eventually { shadowOf(manager).getNotification(Notifier.MANANA_ID) != null }
        val aviso = shadowOf(manager).getNotification(Notifier.MANANA_ID)
        val titulo = aviso.extras.getCharSequence(Notification.EXTRA_TITLE)
        val cuerpo = aviso.extras.getCharSequence(Notification.EXTRA_TEXT)
        assertTrue("título «$titulo»", !titulo.isNullOrBlank())
        assertTrue("cuerpo «$cuerpo»", !cuerpo.isNullOrBlank())
    }

    private fun eventually(
        timeoutMs: Long = 5_000,
        check: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > deadline) error("condition not met within ${timeoutMs}ms")
            Thread.sleep(5)
        }
    }
}
