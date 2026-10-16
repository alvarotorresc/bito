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
        assertTrue(noMolestar.permitido())
        val previo = noMolestar.poner()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, previo)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
        assertTrue(noMolestar.activo(previo))
    }

    @Test
    fun `a stricter filter is never loosened`() {
        concederPermiso(true)
        listOf(NotificationManager.INTERRUPTION_FILTER_ALARMS, NotificationManager.INTERRUPTION_FILTER_NONE).forEach { estricto ->
            manager.setInterruptionFilter(estricto)
            assertNull(noMolestar.poner())
            assertEquals(estricto, manager.currentInterruptionFilter)
        }
    }

    @Test
    fun `a second instance with the record still sees it active`() {
        concederPermiso(true)
        val previo = NoMolestar(context).poner()
        val otra = NoMolestar(context)
        assertTrue(otra.activo(previo))
        assertFalse(otra.activo())
        assertFalse(otra.activo(null))
    }

    @Test
    fun `an unknown current filter is left alone and never stored as previous`() {
        val desconocido =
            object : FiltroAndroid {
                var puestos = 0

                override fun permitido() = true

                override fun actual() = NotificationManager.INTERRUPTION_FILTER_UNKNOWN

                override fun poner(filtro: Int) {
                    puestos++
                }
            }
        assertNull(NoMolestar(desconocido).poner())
        assertEquals(0, desconocido.puestos)
    }

    @Test
    fun `any runtime failure from the system returns safely`() {
        val roto =
            object : FiltroAndroid {
                override fun permitido() = true

                override fun actual() = NotificationManager.INTERRUPTION_FILTER_PRIORITY

                override fun poner(filtro: Int) = throw IllegalArgumentException("Invalid filter")
            }
        val seguro = NoMolestar(roto)
        seguro.restaurar(NotificationManager.INTERRUPTION_FILTER_ALL)
        seguro.restaurar(NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
        val sinServicio =
            object : FiltroAndroid {
                override fun permitido() = throw IllegalStateException("sistema muerto")

                override fun actual() = throw IllegalStateException("sistema muerto")

                override fun poner(filtro: Int) = throw IllegalStateException("sistema muerto")
            }
        val s2 = NoMolestar(sinServicio)
        assertNull(s2.poner())
        assertFalse(s2.activo(NotificationManager.INTERRUPTION_FILTER_ALL))
        s2.restaurar(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun `restores only if still priority`() {
        concederPermiso(true)
        val previo = noMolestar.poner()
        noMolestar.restaurar(previo)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertFalse(noMolestar.activo(previo))
    }

    @Test
    fun `a filter changed by hand is not restored`() {
        concederPermiso(true)
        val previo = noMolestar.poner()
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        noMolestar.restaurar(previo)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, manager.currentInterruptionFilter)
        assertFalse(noMolestar.activo(previo))
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
