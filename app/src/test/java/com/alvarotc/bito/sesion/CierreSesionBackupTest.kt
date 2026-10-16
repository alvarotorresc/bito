package com.alvarotc.bito.sesion

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.domain.sesion.registroSesion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Restaurar una copia con la sesión en marcha la cierra antes y devuelve No molestar (spec §14.3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CierreSesionBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        // Una BitoApp de otro test del mismo proceso puede tener vivo su contenedor sobre los mismos archivos.
        AppStartup.quiesceForTests()
        container = AppContainer(context)
    }

    @After
    fun tearDown() {
        container.cerrarSesion()
        container.database.close()
        AppStartup.quiesceForTests()
    }

    @Test
    fun `restoring a backup closes the live session first`() =
        runTest {
            val manager = context.getSystemService(NotificationManager::class.java)
            shadowOf(manager).setNotificationPolicyAccessGranted(true)
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            container.sesionStore.guardar(registroSesion().copy(filtroPrevio = NotificationManager.INTERRUPTION_FILTER_ALL))
            val copia = container.backup.exportJson(1_000L)

            container.backup.import(copia)

            assertNull(container.sesionStore.leer())
            assertNull(container.motorSesion.registro.value)
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        }
}
