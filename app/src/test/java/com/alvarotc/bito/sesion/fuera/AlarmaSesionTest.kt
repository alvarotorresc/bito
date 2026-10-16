package com.alvarotc.bito.sesion.fuera

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Evento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@Suppress("DEPRECATION") // ShadowAlarmManager.ScheduledAlarm#operation no tiene otro acceso.
class AlarmaSesionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val alarmas get() = shadowOf(alarmManager).scheduledAlarms

    @Test
    fun `only the next boundary is scheduled`() {
        val r = registroDeDosBloques()
        AlarmaSesion.programar(context, r, ahoraA(0))
        assertEquals(1, alarmas.size)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarmas.single().type)
        assertEquals(ANCLA_ELAPSED + 25 * MIN, alarmas.single().triggerAtTime)

        // Cruzada la primera frontera, la alarma pasa a la siguiente y no se acumulan.
        AlarmaSesion.programar(context, r, ahoraA(25 * MIN + 2_000))
        assertEquals(1, alarmas.size)
        assertEquals(ANCLA_ELAPSED + 30 * MIN, alarmas.single().triggerAtTime)
    }

    @Test
    fun `the trigger follows the elapsed clock even if the wall clock jumped`() {
        val r = registroDeDosBloques()
        val ahora = ahoraA(10 * MIN).copy(paredMillis = ahoraA(10 * MIN).paredMillis + 3_600_000L)
        AlarmaSesion.programar(context, r, ahora)
        assertEquals(ANCLA_ELAPSED + 25 * MIN, alarmas.single().triggerAtTime)
    }

    @Test
    fun `with and without exact alarm permission`() {
        val r = registroDeDosBloques()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        AlarmaSesion.programar(context, r, ahoraA(0))
        assertEquals(1, alarmas.size)
        assertTrue(alarmas.single().isAllowWhileIdle)

        AlarmaSesion.cancelar(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        AlarmaSesion.programar(context, r, ahoraA(0))
        assertEquals(1, alarmas.size)
        assertTrue(alarmas.single().isAllowWhileIdle)
        assertEquals(ANCLA_ELAPSED + 25 * MIN, alarmas.single().triggerAtTime)
    }

    @Test
    fun `a paused or finished session has no alarm and cancel leaves none`() {
        val pausada = registroDeDosBloques(eventos = listOf(Evento.Pausa(5 * MIN)))
        AlarmaSesion.programar(context, registroDeDosBloques(), ahoraA(0))
        AlarmaSesion.programar(context, pausada, ahoraA(10 * MIN))
        assertTrue(alarmas.isEmpty())

        AlarmaSesion.programar(context, registroDeDosBloques(), ahoraA(0))
        AlarmaSesion.cancelar(context)
        assertTrue(alarmas.isEmpty())

        AlarmaSesion.programar(context, registroDeDosBloques(), ahoraA(0))
        AlarmaSesion.programar(context, registroDeDosBloques(), ahoraA(56 * MIN))
        assertTrue(alarmas.isEmpty())
    }
}
