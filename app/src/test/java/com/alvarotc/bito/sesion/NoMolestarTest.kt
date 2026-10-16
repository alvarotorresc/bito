package com.alvarotc.bito.sesion

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoMolestarTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val noMolestar = NoMolestar(context)

    @Before
    fun setUp() {
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    private fun concederPermiso(concedido: Boolean) = shadowOf(manager).setNotificationPolicyAccessGranted(concedido)

    @Test
    fun `without permission nothing is touched`() {
        concederPermiso(false)
        assertFalse(noMolestar.permitido())
        assertNull(noMolestar.poner())
        assertFalse(noMolestar.activo())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
    }

    @Test
    fun `puts priority and returns the previous filter`() {
        concederPermiso(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertTrue(noMolestar.permitido())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, noMolestar.poner())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
        assertTrue(noMolestar.activo())
    }

    @Test
    fun `restores only if still priority`() {
        concederPermiso(true)
        val previo = noMolestar.poner()
        noMolestar.restaurar(previo)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertFalse(noMolestar.activo())
    }

    @Test
    fun `a filter changed by hand is not restored`() {
        concederPermiso(true)
        val previo = noMolestar.poner()
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        noMolestar.restaurar(previo)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, manager.currentInterruptionFilter)
        assertFalse(noMolestar.activo())
    }

    @Test
    fun `a null previous filter restores nothing`() {
        concederPermiso(true)
        noMolestar.poner()
        noMolestar.restaurar(null)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
    }

    @Test
    fun `a security exception returns null and does not crash`() {
        val sinPermiso =
            object : FiltroAndroid {
                override fun permitido() = true

                override fun actual() = NotificationManager.INTERRUPTION_FILTER_ALL

                override fun poner(filtro: Int) = throw SecurityException("permiso retirado")
            }
        val seguro = NoMolestar(sinPermiso)
        assertNull(seguro.poner())
        assertFalse(seguro.activo())
        seguro.restaurar(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun `revoked permission ends without crashing`() {
        concederPermiso(true)
        val previo = noMolestar.poner()
        val retirado =
            object : FiltroAndroid {
                override fun permitido() = false

                override fun actual() = NotificationManager.INTERRUPTION_FILTER_PRIORITY

                override fun poner(filtro: Int) = throw SecurityException("permiso retirado")
            }
        NoMolestar(retirado).restaurar(previo)
    }

    @Test
    fun `settings intent asks for the notification policy access`() {
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, noMolestar.intentAjustes().action)
    }
}
