package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.INICIO_PARED
import com.alvarotc.bito.sesion.fuera.BancoFuera
import com.alvarotc.bito.sesion.fuera.MIN
import com.alvarotc.bito.sesion.fuera.PuertaFuera
import com.alvarotc.bito.sesion.fuera.registroDeDosBloques
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Tras reiniciar, el arranque rearma la sesión viva con el reloj de pared. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BootReceiverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var b: BancoFuera

    @Before
    fun setUp() {
        b = BancoFuera(context, tmp.root)
        shadowOf(b.manager).setNotificationsEnabled(true)
        PuertaFuera.entorno = { b.entorno }
    }

    @After
    fun tearDown() {
        PuertaFuera.entorno = null
        b.cerrar()
    }

    @Test
    fun `boot re-arms a live session through the session sync`() {
        runBlocking {
            b.store.guardar(registroDeDosBloques())
            b.ahora = Ahora(INICIO_PARED + 10 * MIN, 120_000L, arranques = 2)
            BootReceiver.recuperarSesion(context)
        }
        val alarmas = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarmas.size)
        assertEquals(1, shadowOf(b.manager).size())
    }
}
